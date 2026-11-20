package com.example.data

import com.example.data.db.JobPostEntity
import com.example.data.db.JobStatus
import com.example.data.repository.SkillsExchangeRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H6 — Rating UI authorization policy (`SkillsExchangeRepository.isRatingAuthorized`).
 *
 * The rating dialog and its ViewModel/repository wiring already exist (uncommitted H6 work in the
 * tree); this test pins the pure, actor-explicit policy that wiring depends on, mirroring
 * `isJobTransitionAuthorized`:
 *
 *  1. Only the job OWNER (`postedByUid`) may rate — strangers and other clients are denied,
 *     so a user can never rate an unrelated job.
 *  2. Only a job in the terminal `RELEASED_TO_WORKER` escrow state may be rated — in-progress,
 *     quoted, disputed or merely funded jobs are denied (job-status rule).
 *  3. The credited target must be exactly the provider assigned to THIS job — an arbitrary
 *     worker id is denied, so a caller cannot credit or defame an unrelated worker.
 *  4. Blank / synthetic / presentation-text actor uids are denied (F7 identity rule).
 *  5. Legacy jobs with a null `postedByUid` belong to nobody and may be rated by nobody.
 *  6. A missing assignment (`assignedProviderId == null`) denies every target.
 *
 * Pure policy only — no Firebase user, no writes, no Android framework. Runs as a plain
 * JVM test (no Robolectric) so it is immune to emulator/runtime environment issues.
 */
class RatingAuthorizationSecurityTest {

    private fun job(
        escrowStatus: String = "RELEASED_TO_WORKER",
        postedByUid: String? = "uid_owner",
        assignedProviderId: String? = "prov_1",
    ) = JobPostEntity(
        id = "job_1",
        title = "Repair kitchen sink",
        category = "Plumbing",
        description = "Tap is dripping.",
        postedByUid = postedByUid,
        postedByResident = "You (Nakawa)",
        district = "Nakawa",
        budget = 50.0,
        urgency = "Today",
        status = JobStatus.COMPLETED.value,
        assignedProviderId = assignedProviderId,
        assignedProviderName = "Elena Rostova",
        createdTimestamp = 1_000L,
        estimatedHours = 1.0,
        materialsCost = 0.0,
        safetyFee = 0.0,
        escrowStatus = escrowStatus,
    )

    private fun uid(raw: String?) = SkillsExchangeRepository.resolveAuthoritativeUid(raw)

    // --- 1. Ownership -----------------------------------------------------------

    @Test
    fun `owner rating assigned worker on released job is authorized`() {
        assertTrue(
            SkillsExchangeRepository.isRatingAuthorized(uid("uid_owner"), job(), "prov_1")
        )
    }

    @Test
    fun `stranger cannot rate another users released job`() {
        assertFalse(
            "a user must not rate an unrelated job",
            SkillsExchangeRepository.isRatingAuthorized(uid("uid_stranger"), job(), "prov_1")
        )
    }

    @Test
    fun `legacy job with null owner may be rated by nobody`() {
        assertFalse(
            SkillsExchangeRepository.isRatingAuthorized(
                uid("uid_owner"), job(postedByUid = null), "prov_1"
            )
        )
    }

    // --- 2. Job-status rule -----------------------------------------------------

    @Test
    fun `owner cannot rate before escrow is released`() {
        for (status in listOf(
            "QUOTE_PENDING", "DEPOSIT_PROMPTED", "FUNDS_LOCKED_IN_ESCROW",
            "WORK_IN_PROGRESS", "PROOF_SUBMITTED"
        )) {
            assertFalse(
                "escrow state $status must not be rateable",
                SkillsExchangeRepository.isRatingAuthorized(
                    uid("uid_owner"), job(escrowStatus = status), "prov_1"
                )
            )
        }
    }

    // --- 3. Target must be THIS job's provider ----------------------------------

    @Test
    fun `owner cannot credit an unrelated worker`() {
        assertFalse(
            "toUserId must match the job's assigned provider",
            SkillsExchangeRepository.isRatingAuthorized(uid("uid_owner"), job(), "prov_9")
        )
    }

    @Test
    fun `missing assignment denies every target`() {
        assertFalse(
            SkillsExchangeRepository.isRatingAuthorized(
                uid("uid_owner"), job(assignedProviderId = null), "prov_1"
            )
        )
    }

    // --- 4. Identity rule (F7) ---------------------------------------------------

    @Test
    fun `synthetic and presentation-text actors are denied`() {
        val released = job()
        for (never in listOf(null, "", "   ", "system", "unknown", "unknown_worker", "user_default_me", "John Doe", "You (Nakawa)")) {
            assertFalse(
                "'$never' must never authorize a rating",
                SkillsExchangeRepository.isRatingAuthorized(uid(never), released, "prov_1")
            )
        }
    }
}
