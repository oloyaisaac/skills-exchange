package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.UserProfileEntity
import com.example.data.db.WalletLedgerEntity
import com.example.data.remote.FirebaseService
import com.example.data.repository.SkillsExchangeRepository
import com.example.data.repository.SkillsExchangeRepository.CashoutRequestResult
import com.example.data.repository.SkillsExchangeRepository.WithdrawalCancelResult
import com.example.data.repository.LedgerEntryType
import com.example.data.repository.WithdrawalStatus
import com.example.data.repository.WalletLedgerEntrySnapshot
import com.example.data.repository.WithdrawalSnapshot
import com.example.data.repository.WithdrawalTransitionOutcome
import com.example.data.repository.computeAvailableBalanceMinor
import com.example.data.repository.evaluateWithdrawalTransition
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AUDIT-W-CASHOUT: security tests for the worker wallet cashout backend boundary.
 *
 * Exercises the REAL repository boundary (via the internal `*Internal` methods, which take an
 * injected actor uid so unauthenticated/foreign-identity paths are reachable in a unit test) plus
 * the pure security core. Coverage maps to the mandated categories:
 *
 *  AUTHENTICATION  unauthenticated request/cancel -> fail closed
 *  IDENTITY        a caller cannot act as another worker (actor is the only identity source)
 *  AMOUNT          zero / negative / NaN / +Inf / -Inf / over-balance -> rejected; valid -> accepted
 *  OWNERSHIP       worker A cannot cancel B's withdrawal or read B's balance as A's
 *  STATE           worker cannot mark COMPLETED; terminal withdrawal immutable; invalid rejected
 *  IDEMPOTENCY     same key -> reused, not duplicated; second active withdrawal -> blocked
 *  BALANCE         request cannot create money or push available negative; failed -> RELEASE
 *  AUTHORIZATION   COMPLETED / FAILED / REVERSED require server authority
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkerCashoutSecurityTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: SkillsExchangeRepository

    private val workerA = "uid_worker_a"
    private val workerB = "uid_worker_b"
    private val momo = "+256 772 198 420"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SkillsExchangeRepository(db, FirebaseService())
        runBlocking {
            db.userProfileDao().insertUserProfile(
                UserProfileEntity(id = workerA, phoneNumber = "+256700000001", fullName = "Worker A", initialRole = "WORKER", currentActiveMode = "WORKER", isWorkerActivated = true)
            )
            db.userProfileDao().insertUserProfile(
                UserProfileEntity(id = workerB, phoneNumber = "+256700000002", fullName = "Worker B", initialRole = "WORKER", currentActiveMode = "WORKER", isWorkerActivated = true)
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun credit(uid: String, amount: Double, id: String) {
        db.walletLedgerDao().insertEntry(
            WalletLedgerEntity(
                id = id,
                workerId = uid,
                entryType = LedgerEntryType.CREDIT.value,
                amountMinor = com.example.util.Money.toMinorUnits(amount)!!
            )
        )
    }

    private fun balanceOf(uid: String): Double = runBlocking {
        com.example.util.Money.toMajorUnits(
            computeAvailableBalanceMinor(
                db.walletLedgerDao().getEntriesForWorker(uid).map { WalletLedgerEntrySnapshot(it.entryType, it.amountMinor, it.withdrawalId) }
            )
        )
    }

    // ---------------------------------------------------------------- AUTHENTICATION

    @Test
    fun unauthenticatedRequest_failsClosed() = runBlocking {
        credit(workerA, 100.0, "c1")
        val res = repository.requestWorkerCashoutInternal(50.0, momo, "k1", null)
        assertTrue(res is CashoutRequestResult.NotAuthenticated)
        assertTrue(db.walletWithdrawalDao().getWithdrawalsForWorker(workerA).isEmpty())
    }

    @Test
    fun unauthenticatedCancel_failsClosed() = runBlocking {
        credit(workerA, 100.0, "c1")
        repository.requestWorkerCashoutInternal(50.0, momo, "k1", workerA)
        val id = db.walletWithdrawalDao().getWithdrawalsForWorker(workerA).single().id
        val res = repository.cancelWorkerWithdrawalInternal(id, null)
        assertTrue(res is WithdrawalCancelResult.NotAuthenticated)
    }

    // ---------------------------------------------------------------- IDENTITY

    @Test
    fun callerCannotSpoofAnotherWorkerUid() = runBlocking {
        credit(workerA, 100.0, "c1")
        // Worker B has no funds. If a caller-supplied id were trusted, B could withdraw against
        // A's balance. The boundary resolves the actor internally, so B's request sees 0.0.
        val res = repository.requestWorkerCashoutInternal(50.0, momo, "kb", workerB)
        assertTrue(res is CashoutRequestResult.AmountExceedsAvailable)
        assertTrue(db.walletWithdrawalDao().getWithdrawalsForWorker(workerB).isEmpty())
    }

    @Test
    fun syntheticAndBlankIdentity_rejected() = runBlocking {
        credit(workerA, 100.0, "c1")
        for (bad in listOf("", "   ", "user_default_me", "You (Nakawa)", "Robert K. (Sunnyside)", "system")) {
            val res = repository.requestWorkerCashoutInternal(10.0, momo, "k_$bad", bad)
            assertTrue("identity=[$bad]", res is CashoutRequestResult.NotAuthenticated)
        }
    }

    // ---------------------------------------------------------------- AMOUNT

    @Test
    fun invalidAmounts_rejected() = runBlocking {
        credit(workerA, 100.0, "c1")
        for (amt in listOf(0.0, -5.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val res = repository.requestWorkerCashoutInternal(amt, momo, "k_$amt", workerA)
            assertTrue("amount=$amt", res is CashoutRequestResult.InvalidAmount)
        }
        assertTrue(db.walletWithdrawalDao().getWithdrawalsForWorker(workerA).isEmpty())
    }

    @Test
    fun amountExceedingAvailable_rejected() = runBlocking {
        credit(workerA, 100.0, "c1")
        val res = repository.requestWorkerCashoutInternal(100.01, momo, "k_over", workerA)
        assertTrue(res is CashoutRequestResult.AmountExceedsAvailable)
    }

    @Test
    fun validAmount_accepted() = runBlocking {
        credit(workerA, 100.0, "c1")
        val res = repository.requestWorkerCashoutInternal(60.0, momo, "k_ok", workerA)
        assertTrue(res is CashoutRequestResult.RequestRecorded)
        val row = db.walletWithdrawalDao().getWithdrawalsForWorker(workerA).single()
        assertEquals(workerA, row.workerId)
        assertEquals(6000L, row.amountMinor)
        assertEquals(WithdrawalStatus.PENDING.value, row.status)
    }

    @Test
    fun blankOrMalformedDestination_rejected() = runBlocking {
        credit(workerA, 100.0, "c1")
        assertTrue(repository.requestWorkerCashoutInternal(10.0, "", "k_d1", workerA) is CashoutRequestResult.DestinationNotSet)
        assertTrue(repository.requestWorkerCashoutInternal(10.0, "not-a-number", "k_d2", workerA) is CashoutRequestResult.DestinationInvalid)
        assertTrue(repository.requestWorkerCashoutInternal(10.0, "123", "k_d3", workerA) is CashoutRequestResult.DestinationInvalid)
    }

    // ---------------------------------------------------------------- OWNERSHIP

    @Test
    fun workerACannotCancelWorkerBWithdrawal() = runBlocking {
        credit(workerA, 100.0, "c1")
        repository.requestWorkerCashoutInternal(40.0, momo, "k_a", workerA)
        val idA = db.walletWithdrawalDao().getWithdrawalsForWorker(workerA).single().id
        // Worker B tries to cancel A's withdrawal -> NotAuthorized, status unchanged.
        val res = repository.cancelWorkerWithdrawalInternal(idA, workerB)
        assertTrue(res is WithdrawalCancelResult.NotAuthorized)
        assertEquals(WithdrawalStatus.PENDING.value, db.walletWithdrawalDao().getWithdrawalById(idA)!!.status)
    }

    // ---------------------------------------------------------------- STATE MACHINE

    @Test
    fun workerCannotMarkOwnWithdrawalCompleted() = runBlocking {
        credit(workerA, 100.0, "c1")
        repository.requestWorkerCashoutInternal(50.0, momo, "k1", workerA)
        val row = db.walletWithdrawalDao().getWithdrawalsForWorker(workerA).single()
        val snap = WithdrawalSnapshot(row.id, row.workerId, row.amountMinor, row.status)
        val outcome = evaluateWithdrawalTransition(snap, WithdrawalStatus.PENDING, WithdrawalStatus.COMPLETED, workerA, actorIsServerAuthority = false)
        assertTrue(outcome is WithdrawalTransitionOutcome.Reject)
    }

    @Test
    fun workerCannotAdvanceOrCancelProcessing() = runBlocking {
        val snap = WithdrawalSnapshot("w1", workerA, 1000L, WithdrawalStatus.PROCESSING.value)
        assertTrue(evaluateWithdrawalTransition(snap, WithdrawalStatus.PROCESSING, WithdrawalStatus.CANCELLED, workerA, actorIsServerAuthority = false) is WithdrawalTransitionOutcome.Reject)
        assertTrue(evaluateWithdrawalTransition(snap, WithdrawalStatus.PROCESSING, WithdrawalStatus.COMPLETED, workerA, actorIsServerAuthority = false) is WithdrawalTransitionOutcome.Reject)
    }

    @Test
    fun terminalWithdrawalCannotBeModified() = runBlocking {
        val snap = WithdrawalSnapshot("w1", workerA, 1000L, WithdrawalStatus.CANCELLED.value)
        // Even a server authority cannot resurrect a terminal withdrawal (no double-pay).
        val outcome = evaluateWithdrawalTransition(snap, WithdrawalStatus.CANCELLED, WithdrawalStatus.COMPLETED, workerA, actorIsServerAuthority = true)
        assertTrue(outcome is WithdrawalTransitionOutcome.NoOpTerminal)
    }

    @Test
    fun mismatchedFromState_rejected() = runBlocking {
        val snap = WithdrawalSnapshot("w1", workerA, 1000L, WithdrawalStatus.PENDING.value)
        // Claiming the row is PROCESSING when it is actually PENDING -> reject (no bypass).
        val outcome = evaluateWithdrawalTransition(snap, WithdrawalStatus.PROCESSING, WithdrawalStatus.CANCELLED, workerA, actorIsServerAuthority = false)
        assertTrue(outcome is WithdrawalTransitionOutcome.Reject)
    }

    @Test
    fun foreignActorCannotTransitionAnotherWorkersWithdrawal() = runBlocking {
        val snap = WithdrawalSnapshot("w1", workerA, 1000L, WithdrawalStatus.PENDING.value)
        val outcome = evaluateWithdrawalTransition(snap, WithdrawalStatus.PENDING, WithdrawalStatus.CANCELLED, workerB, actorIsServerAuthority = true)
        assertTrue(outcome is WithdrawalTransitionOutcome.Reject)
    }

    @Test
    fun validPendingToCancelled_requiresOwner() = runBlocking {
        val snap = WithdrawalSnapshot("w1", workerA, 1000L, WithdrawalStatus.PENDING.value)
        val outcome = evaluateWithdrawalTransition(snap, WithdrawalStatus.PENDING, WithdrawalStatus.CANCELLED, workerA, actorIsServerAuthority = false)
        val apply = outcome as WithdrawalTransitionOutcome.Apply
        assertEquals(WithdrawalStatus.CANCELLED, apply.nextStatus)
        assertTrue(apply.reversalRequired)
    }

    // ---------------------------------------------------------------- IDEMPOTENCY

    @Test
    fun duplicateSameKey_reusedNotDuplicated() = runBlocking {
        credit(workerA, 100.0, "c1")
        val first = repository.requestWorkerCashoutInternal(40.0, momo, "key_1", workerA)
        val second = repository.requestWorkerCashoutInternal(40.0, momo, "key_1", workerA)
        assertTrue(first is CashoutRequestResult.RequestRecorded)
        assertTrue(second is CashoutRequestResult.RequestRecorded)
        assertEquals((first as CashoutRequestResult.RequestRecorded).withdrawalId, (second as CashoutRequestResult.RequestRecorded).withdrawalId)
        assertEquals(1, db.walletWithdrawalDao().getWithdrawalsForWorker(workerA).size)
    }

    @Test
    fun secondDistinctActiveWithdrawal_blocked() = runBlocking {
        credit(workerA, 100.0, "c1")
        repository.requestWorkerCashoutInternal(10.0, momo, "key_a", workerA)
        val second = repository.requestWorkerCashoutInternal(10.0, momo, "key_b", workerA)
        assertTrue(second is CashoutRequestResult.DuplicateActiveWithdrawal)
        assertEquals(1, db.walletWithdrawalDao().getWithdrawalsForWorker(workerA).size)
    }

    @Test
    fun blankIdempotencyKey_rejected() = runBlocking {
        credit(workerA, 100.0, "c1")
        val res = repository.requestWorkerCashoutInternal(10.0, momo, "   ", workerA)
        assertTrue(res is CashoutRequestResult.InvalidAmount)
        assertTrue(db.walletWithdrawalDao().getWithdrawalsForWorker(workerA).isEmpty())
    }

    // ---------------------------------------------------------------- BALANCE INTEGRITY

    @Test
    fun withdrawalCannotCreateMoney() = runBlocking {
        credit(workerA, 100.0, "c1")
        repository.requestWorkerCashoutInternal(60.0, momo, "k1", workerA)
        // 100 credited, 60 reserved -> available is 40, never more than the credit.
        assertEquals(40.0, balanceOf(workerA), 0.0)
    }

    @Test
    fun withdrawalCannotMakeAvailableNegative() = runBlocking {
        credit(workerA, 50.0, "c1")
        // 100 > 50 available -> rejected; available stays 50.
        val res = repository.requestWorkerCashoutInternal(100.0, momo, "k1", workerA)
        assertTrue(res is CashoutRequestResult.AmountExceedsAvailable)
        assertEquals(50.0, balanceOf(workerA), 0.0)
    }

    @Test
    fun cancelReleasesReservation_restoringFunds() = runBlocking {
        credit(workerA, 100.0, "c1")
        repository.requestWorkerCashoutInternal(60.0, momo, "k1", workerA)
        assertEquals(40.0, balanceOf(workerA), 0.0)
        val id = db.walletWithdrawalDao().getWithdrawalsForWorker(workerA).single().id
        val res = repository.cancelWorkerWithdrawalInternal(id, workerA)
        assertTrue(res is WithdrawalCancelResult.Success)
        // Reservation released -> full balance restored, funds not destroyed.
        assertEquals(100.0, balanceOf(workerA), 0.0)
        assertEquals(WithdrawalStatus.CANCELLED.value, db.walletWithdrawalDao().getWithdrawalById(id)!!.status)
    }

    @Test
    fun failedWithdrawal_releasesReservation() = runBlocking {
        credit(workerA, 100.0, "c1")
        repository.requestWorkerCashoutInternal(60.0, momo, "k1", workerA)
        val row = db.walletWithdrawalDao().getWithdrawalsForWorker(workerA).single()
        val snap = WithdrawalSnapshot(row.id, row.workerId, row.amountMinor, row.status)
        val outcome = evaluateWithdrawalTransition(snap, WithdrawalStatus.PENDING, WithdrawalStatus.FAILED, workerA, actorIsServerAuthority = true)
        val apply = outcome as WithdrawalTransitionOutcome.Apply
        // A FAILED disbursement must reverse the reservation, not destroy the funds.
        assertTrue(apply.reversalRequired)
    }

    @Test
    fun completedWithdrawal_debitsExactlyOnce() = runBlocking {
        credit(workerA, 100.0, "c1")
        repository.requestWorkerCashoutInternal(60.0, momo, "k1", workerA)
        val row = db.walletWithdrawalDao().getWithdrawalsForWorker(workerA).single()
        val snap = WithdrawalSnapshot(row.id, row.workerId, row.amountMinor, row.status)
        val outcome = evaluateWithdrawalTransition(snap, WithdrawalStatus.PENDING, WithdrawalStatus.COMPLETED, workerA, actorIsServerAuthority = true)
        val apply = outcome as WithdrawalTransitionOutcome.Apply
        // Completion is NOT a reversal; the DEBIT is applied exactly once by the disbursement path.
        assertFalse(apply.reversalRequired)
        // Completing an already-COMPLETED withdrawal is a no-op terminal -> cannot pay twice.
        val already = WithdrawalSnapshot(row.id, row.workerId, row.amountMinor, WithdrawalStatus.COMPLETED.value)
        val second = evaluateWithdrawalTransition(already, WithdrawalStatus.COMPLETED, WithdrawalStatus.COMPLETED, workerA, actorIsServerAuthority = true)
        assertTrue(second is WithdrawalTransitionOutcome.NoOpTerminal)
    }

    // ---------------------------------------------------------------- AUTHORIZATION

    @Test
    fun completionRequiresServerAuthority() = runBlocking {
        val snap = WithdrawalSnapshot("w1", workerA, 1000L, WithdrawalStatus.PENDING.value)
        val asWorker = evaluateWithdrawalTransition(snap, WithdrawalStatus.PENDING, WithdrawalStatus.COMPLETED, workerA, actorIsServerAuthority = false)
        val asServer = evaluateWithdrawalTransition(snap, WithdrawalStatus.PENDING, WithdrawalStatus.COMPLETED, workerA, actorIsServerAuthority = true)
        assertTrue(asWorker is WithdrawalTransitionOutcome.Reject)
        assertTrue(asServer is WithdrawalTransitionOutcome.Apply)
    }

    @Test
    fun cancelReleasesFunds_andNextRequestCanUseThem() = runBlocking {
        credit(workerA, 100.0, "c1")
        repository.requestWorkerCashoutInternal(100.0, momo, "k1", workerA)
        val id = db.walletWithdrawalDao().getWithdrawalsForWorker(workerA).single().id
        repository.cancelWorkerWithdrawalInternal(id, workerA)
        assertEquals(100.0, balanceOf(workerA), 0.0)
        // After cancellation the worker can request again against the restored balance.
        val res = repository.requestWorkerCashoutInternal(100.0, momo, "k2", workerA)
        assertTrue(res is CashoutRequestResult.RequestRecorded)
    }

    // ---------------------------------------------------------------- PURE CORE

    @Test
    fun availableBalance_mathIsAuditable() {
        val entries = listOf(
            WalletLedgerEntrySnapshot(LedgerEntryType.CREDIT.value, 10000L),
            WalletLedgerEntrySnapshot(LedgerEntryType.RESERVE.value, 3000L),
            WalletLedgerEntrySnapshot(LedgerEntryType.RELEASE.value, 3000L),
            WalletLedgerEntrySnapshot(LedgerEntryType.DEBIT.value, 2000L)
        )
        // 100 - 30 (reserve) + 30 (release) - 20 (debit) = 80
        assertEquals(8000L, computeAvailableBalanceMinor(entries))
    }

    @Test
    fun negativeAvailableBalance_clampedToZero() {
        val entries = listOf(WalletLedgerEntrySnapshot(LedgerEntryType.DEBIT.value, 5000L))
        assertEquals(0L, computeAvailableBalanceMinor(entries))
    }
}



