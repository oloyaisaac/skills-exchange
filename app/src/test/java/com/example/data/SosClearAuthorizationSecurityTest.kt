package com.example.data

import com.example.data.db.JobPostEntity
import com.example.data.db.JobStatus
import com.example.data.repository.SkillsExchangeRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H7/SAFE-003 — SOS stand-down authorization policy
 * (`SkillsExchangeRepository.isSosClearAuthorized`).
 *
 * Raising the panic flag is an emergency action, but standing it down is a status mutation on
 * somebody's job, so it is owner-gated and fail-closed, mirroring `isRatingAuthorized`:
 *
 *  1. Only the job OWNER (`postedByUid`) may clear the flag — a stranger can never silence another
 *     account's emergency.
 *  2. A legacy job with a null `postedByUid` belongs to nobody and may be stood down by nobody.
 *  3. The flag must actually be raised — there is nothing to clear on a non-SOS job.
 *  4. Blank / synthetic / presentation-text actor uids are denied (F7 identity rule).
 *
 * Pure policy only — no Firebase user, no writes, no Android framework. Runs as a plain JVM test
 * (no Robolectric) so it is immune to emulator/runtime environment issues.
 */
class SosClearAuthorizationSecurityTest {

    private fun job(
        postedByUid: String? = "uid_owner",
        isSosTriggered: Boolean = true,
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
        status = JobStatus.IN_PROGRESS.value,
        assignedProviderId = "prov_1",
        assignedProviderName = "Elena Rostova",
        createdTimestamp = 1_000L,
        estimatedHours = 1.0,
        materialsCost = 0.0,
        safetyFee = 0.0,
        isSosTriggered = isSosTriggered,
    )

    private fun uid(raw: String?) = SkillsExchangeRepository.resolveAuthoritativeUid(raw)

    // --- 1. Ownership -----------------------------------------------------------

    @Test
    fun `owner standing down active SOS is authorized`() {
        assertTrue(
            SkillsExchangeRepository.isSosClearAuthorized(uid("uid_owner"), job())
        )
    }

    @Test
    fun `stranger cannot stand down another users SOS`() {
        assertFalse(
            "a user must not silence an unrelated job's emergency flag",
            SkillsExchangeRepository.isSosClearAuthorized(uid("uid_stranger"), job())
        )
    }

    @Test
    fun `legacy job with null owner may be stood down by nobody`() {
        assertFalse(
            SkillsExchangeRepository.isSosClearAuthorized(
                uid("uid_owner"), job(postedByUid = null)
            )
        )
    }

    // --- 2. Must actually be raised ---------------------------------------------

    @Test
    fun `owner cannot clear an SOS that is not active`() {
        assertFalse(
            "there is nothing to stand down when the flag is not raised",
            SkillsExchangeRepository.isSosClearAuthorized(uid("uid_owner"), job(isSosTriggered = false))
        )
    }

    // --- 3. Identity rule (F7) --------------------------------------------------

    @Test
    fun `synthetic and presentation-text actors are denied`() {
        val active = job()
        for (never in listOf(null, "", "   ", "system", "unknown", "unknown_worker", "user_default_me", "John Doe", "You (Nakawa)")) {
            assertFalse(
                "'$never' must never authorize clearing an SOS",
                SkillsExchangeRepository.isSosClearAuthorized(uid(never), active)
            )
        }
    }
}
