package com.example.data.repository

/**
 * AUDIT-W-CASHOUT: pure, side-effect-free worker cashout security core.
 *
 * This object holds every money/authorization decision for worker wallet cashout so it can be
 * exhaustively unit-tested (`WorkerCashoutSecurityCoreTest`) without Room, Firebase or a provider.
 * The repository boundary ([SkillsExchangeRepository.requestWorkerCashout]) is the ONLY production
 * consumer and simply orchestrates these rules; it never re-implements or weakens them.
 *
 * Trust boundary (the point of this milestone):
 *  1. The backend/repository is the authority. The authenticated Firebase UID owns the wallet.
 *     A UI-supplied amount and destination are REQUESTS, never authority; the available balance is
 *     computed from the authoritative wallet ledger, never from a caller-supplied number.
 *  2. Amounts must be finite, positive and within the available balance. Zero, negative, NaN,
 *     +Inf, -Inf and over-withdrawal are all rejected — a withdrawal can never create money or
 *     push the available balance negative.
 *  3. The withdrawal runs through an explicit state machine. A worker can never mark their own
 *     withdrawal COMPLETED; the terminal COMPLETED/FAILED/REVERSED transitions are reserved for
 *     the server/provider authority (the future MoMo disbursement integration).
 *  4. Idempotency: a repeated press / retry / restart with the same idempotency key returns the
 *     SAME pending withdrawal instead of creating a second one; only ONE active reservation may
 *     exist per wallet at a time. This is enforced here (money logic), not merely by a UI boolean.
 *  5. Funds are moved atomically with the request via a RESERVE ledger entry, so the reservation
 *     is enforced by the same transaction that records the withdrawal. A FAILED/CANCELLED
 *     withdrawal REVERSES the reservation (the money is not permanently destroyed).
 *
 * This is the smallest secure foundation. It does NOT claim any mobile-money money movement:
 * requesting a withdrawal only RESERVES funds and records a PENDING request. Real disbursement
 * (MTN MoMo / Airtel Money) is a separate, clearly isolated server-authority integration step.
 */

/**
 * AUDIT-W-CASHOUT: withdrawal lifecycle.
 *
 * Only the states this milestone actually needs. A worker may move their OWN request through the
 * worker-reachable states (PENDING -> PROCESSING / CANCELLED); the terminal money-moving states
 * (COMPLETED / FAILED / REVERSED) are reachable ONLY from server/provider authority.
 */
enum class WithdrawalStatus(val value: String) {
    PENDING("PENDING"),
    PROCESSING("PROCESSING"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED"),
    REVERSED("REVERSED");

    companion object {
        fun fromValue(value: String): WithdrawalStatus? = entries.find { it.value == value }
    }
}

/**
 * A withdrawal the WORKER is allowed to request themselves. These states never move money out;
 * the funds stay reserved (or are released back on cancel) until the server acts.
 */
val WORKER_REQUESTABLE_WITHDRAWAL_STATUSES: Set<WithdrawalStatus> = setOf(
    WithdrawalStatus.PENDING,
    WithdrawalStatus.PROCESSING,
    WithdrawalStatus.CANCELLED
)

/**
 * A withdrawal in one of these states still holds a live reservation against the wallet. A wallet
 * may have at most ONE such withdrawal at a time (single in-flight idempotency gate).
 */
val ACTIVE_WITHDRAWAL_STATUSES: Set<WithdrawalStatus> = setOf(
    WithdrawalStatus.PENDING,
    WithdrawalStatus.PROCESSING
)

/** A withdrawal that can never change again (money has settled or been returned). */
val TERMINAL_WITHDRAWAL_STATUSES: Set<WithdrawalStatus> = setOf(
    WithdrawalStatus.COMPLETED,
    WithdrawalStatus.FAILED,
    WithdrawalStatus.CANCELLED,
    WithdrawalStatus.REVERSED
)

/**
 * Ledger entry kinds. The available balance is the audit trail of these entries:
 *  - CREDIT:    money earned/released to the wallet (increases available).
 *  - DEBIT:     money that has actually left the wallet (decreases available). NOTE: for a worker
 *               cashout the DEBIT is only written at DISBURSE time (server authority), not at
 *               request time.
 *  - RESERVE:   money held for a pending withdrawal (decreases available while reserved).
 *  - RELEASE:   the reservation was cancelled/failed, so the reserved amount is handed back
 *               (increases available).
 *  - ADJUSTMENT: an explicit server-side correction (never produced by a worker request).
 */
enum class LedgerEntryType(val value: String) {
    CREDIT("CREDIT"),
    DEBIT("DEBIT"),
    RESERVE("RESERVE"),
    RELEASE("RELEASE"),
    ADJUSTMENT("ADJUSTMENT");

    companion object {
        fun fromValue(value: String): LedgerEntryType? = entries.find { it.value == value }

        /** Amounts that RAISE the available balance. */
        val INCREASING: Set<LedgerEntryType> = setOf(CREDIT, RELEASE)

        /** Amounts that LOWER the available balance (spend or held-in-reserve). */
        val DECREASING: Set<LedgerEntryType> = setOf(DEBIT, RESERVE)
    }
}

/** A single immutable ledger entry as consumed by the pure core (decoupled from Room). */
data class WalletLedgerEntrySnapshot(
    val entryType: String,
    val amount: Double,
    val withdrawalId: String? = null
)

/** A withdrawal row as consumed by the pure core (decoupled from Room). */
data class WithdrawalSnapshot(
    val id: String,
    val workerId: String,
    val amount: Double,
    val status: String,
    /**
     * AUDIT-W-CASHOUT: the caller's retry token as stored on the row. The idempotency gate MUST
     * match on this field — never on [id], because the row id is the key with a `wd_` prefix
     * applied, so `id == key` can never be true for a prefixed row and the reuse branch would be
     * dead code (every retry would instead look like a second, distinct withdrawal).
     */
    val idempotencyKey: String = ""
)

/**
 * Why a withdrawal request was rejected. The repository maps these onto the user-facing result;
 * the UI never becomes the authorization boundary.
 */
enum class CashoutRejectReason(val value: String) {
    NOT_AUTHENTICATED("NOT_AUTHENTICATED"),
    INVALID_AMOUNT("INVALID_AMOUNT"),
    AMOUNT_EXCEEDS_AVAILABLE("AMOUNT_EXCEEDS_AVAILABLE"),
    DESTINATION_NOT_SET("DESTINATION_NOT_SET"),
    DESTINATION_INVALID("DESTINATION_INVALID");

    companion object {
        fun fromValue(value: String): CashoutRejectReason? = entries.find { it.value == value }
    }
}

/** The outcome of the pure amount/destination validation step. */
sealed class CashoutRequestValidation {
    data class Valid(val normalizedAmount: Double, val normalizedDestination: String) : CashoutRequestValidation()
    data class Invalid(val reason: CashoutRejectReason) : CashoutRequestValidation()
}

/** The outcome of the pure state-transition step. */
sealed class WithdrawalTransitionOutcome {
    data class Apply(val nextStatus: WithdrawalStatus, val reversalRequired: Boolean) : WithdrawalTransitionOutcome()
    data class Reject(val reason: CashoutRejectReason) : WithdrawalTransitionOutcome()
    object NoOpTerminal : WithdrawalTransitionOutcome()
}

/** The outcome of the pure idempotency/active-reservation step. */
sealed class CashoutIdempotencyOutcome {
    /** A pending withdrawal already carries this key — return it, do NOT create a second one. */
    data class ReuseExisting(val withdrawalId: String) : CashoutIdempotencyOutcome()

    /** Another withdrawal still holds a live reservation — block until it settles. */
    object ActiveWithdrawalExists : CashoutIdempotencyOutcome()

    /** No duplicate — safe to create a brand-new withdrawal + reservation. */
    object Proceed : CashoutIdempotencyOutcome()
}

/**
 * Normalizes a caller-supplied mobile-money destination to the digit form the disbursement
 * provider needs (leading `+` stripped, separators dropped), while PRESERVING any country code.
 * Returns null when the result is not a plausible MSISDN (fewer than 9 digits, or any surviving
 * non-digit character), so a name, an email or a garbage string can never become a destination.
 */
fun normalizeMomoDestination(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val allowedSeparators = setOf(' ', '-', '(', ')', '+', '.')
    // Reject anything that is not just digits / separators / '+'. A name, email or free-text
    // string can never become a destination even if it happens to contain 9+ digits.
    if (trimmed.any { !it.isDigit() && it !in allowedSeparators }) return null
    // Keep digits only, then drop a leading trunk '0' (e.g. 0772... -> 772...) so the stored
    // MSISDN is in the bare national form the disbursement payload expects; a '+256...' number
    // keeps its country code because there is no leading '0' to strip.
    val digitsOnly = trimmed.filter { it.isDigit() }.trimStart('0')
    if (digitsOnly.length < 9) return null
    return digitsOnly
}

/**
 * Validates a withdrawal REQUEST (amount + destination) in isolation. The [availableBalance] MUST
 * be the authoritative balance computed from the ledger — never a caller-supplied number.
 *
 * Rejects: zero, negative, NaN, +Inf, -Inf, amount greater than available, blank/short destination.
 */
fun validateCashoutRequest(
    amount: Double,
    destination: String,
    availableBalance: Double
): CashoutRequestValidation {
    if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
        return CashoutRequestValidation.Invalid(CashoutRejectReason.INVALID_AMOUNT)
    }
    // Guard against a non-finite authoritative balance (corrupt ledger) so we never authorize a
    // withdrawal against an unknown amount.
    if (availableBalance.isNaN() || availableBalance.isInfinite() || availableBalance < 0.0) {
        return CashoutRequestValidation.Invalid(CashoutRejectReason.AMOUNT_EXCEEDS_AVAILABLE)
    }
    if (amount > availableBalance) {
        return CashoutRequestValidation.Invalid(CashoutRejectReason.AMOUNT_EXCEEDS_AVAILABLE)
    }
    val normalizedDestination = normalizeMomoDestination(destination)
        ?: return CashoutRequestValidation.Invalid(
            if (destination.isBlank()) CashoutRejectReason.DESTINATION_NOT_SET
            else CashoutRejectReason.DESTINATION_INVALID
        )
    return CashoutRequestValidation.Valid(amount, normalizedDestination)
}

/**
 * Computes the authoritative AVAILABLE balance from the full ledger audit trail.
 *
 * available = sum(CREDIT) + sum(RELEASE) - sum(RESERVE) - sum(DEBIT)
 *
 * This is the ONLY number a withdrawal may be checked against. It is deterministic and auditable:
 * any change to the wallet must appear as a ledger entry, so a worker can never inflate this by
 * editing a balance field. ADJUSTMENT entries are server-authoritative and fold their sign into
 * [WalletLedgerEntrySnapshot.amount] (negative = reduce, positive = increase).
 */
fun computeAvailableBalance(entries: List<WalletLedgerEntrySnapshot>): Double {
    var available = 0.0
    for (entry in entries) {
        val type = LedgerEntryType.fromValue(entry.entryType) ?: continue
        val amount = entry.amount
        if (amount.isNaN() || amount.isInfinite()) continue
        when {
            type in LedgerEntryType.INCREASING -> available += amount
            type in LedgerEntryType.DECREASING -> available -= amount
            type == LedgerEntryType.ADJUSTMENT -> available += amount
        }
    }
    // Never expose a negative available balance; a well-formed ledger can never produce one, but
    // clamp so a corrupt row can't be read as "spendable negative money".
    return if (available.isNaN() || available.isInfinite() || available < 0.0) 0.0 else available
}

/**
 * Decides whether a worker may move their OWN withdrawal from [from] to [to].
 *
 * Rules:
 *  - A worker may only ever move a withdrawal through the WORKER_REQUESTABLE states
 *    (PENDING -> PROCESSING, PENDING -> CANCELLED). They can NEVER reach COMPLETED or FAILED, and
 *    can NEVER move a withdrawal that is already terminal (no resurrect / double-pay).
 *  - [actorIsServerAuthority] is the ONLY way to reach COMPLETED / FAILED / REVERSED. It is set
 *    exclusively by the server/provider disbursement path, never by a client call.
 *  - [actorWorkerId] must own the withdrawal ([workerId]); another account's withdrawal is
 *    rejected outright (ownership enforced here, not by the caller).
 *  - A reversal ([reversalRequired]) is only permitted for the server authority.
 */
fun evaluateWithdrawalTransition(
    withdrawal: WithdrawalSnapshot,
    from: WithdrawalStatus,
    to: WithdrawalStatus,
    actorWorkerId: String,
    actorIsServerAuthority: Boolean
): WithdrawalTransitionOutcome {
    // 0. Ownership: the actor must own this withdrawal. (Fail closed for a mismatched/blank actor.)
    if (actorWorkerId.isBlank() || withdrawal.workerId.isBlank() || withdrawal.workerId != actorWorkerId) {
        return WithdrawalTransitionOutcome.Reject(CashoutRejectReason.NOT_AUTHENTICATED)
    }

    // 1. Terminal states are immutable (no re-entry, no double-pay, no resurrection).
    if (from in TERMINAL_WITHDRAWAL_STATUSES) {
        return WithdrawalTransitionOutcome.NoOpTerminal
    }

    // 2. Identity of the source must match the transition's declared [from].
    val currentStatus = WithdrawalStatus.fromValue(withdrawal.status)
    if (currentStatus == null || currentStatus != from) {
        return WithdrawalTransitionOutcome.Reject(CashoutRejectReason.INVALID_AMOUNT)
    }

    // 3. Server/provider authority is the ONLY path into COMPLETED / FAILED / REVERSED.
    val moneyMoving = to in setOf(
        WithdrawalStatus.COMPLETED,
        WithdrawalStatus.FAILED,
        WithdrawalStatus.REVERSED
    )
    if (moneyMoving && !actorIsServerAuthority) {
        // A worker can never mark their own withdrawal completed/failed/reversed.
        return WithdrawalTransitionOutcome.Reject(CashoutRejectReason.NOT_AUTHENTICATED)
    }

    // 4. Worker-reachable transitions (worker is NOT server authority here).
    if (!actorIsServerAuthority) {
        val allowed = when (from) {
            WithdrawalStatus.PENDING -> setOf(WithdrawalStatus.PROCESSING, WithdrawalStatus.CANCELLED)
            else -> emptySet() // PROCESSING is server-driven; a worker cannot advance or cancel it.
        }
        return if (to in allowed) {
            // PENDING -> CANCELLED must hand the reservation back to the wallet.
            WithdrawalTransitionOutcome.Apply(nextStatus = to, reversalRequired = to == WithdrawalStatus.CANCELLED)
        } else {
            WithdrawalTransitionOutcome.Reject(CashoutRejectReason.NOT_AUTHENTICATED)
        }
    }

    // 5. Server authority transitions. Reversal is the safe default for FAILED so funds are never
    //    permanently destroyed by a provider error; a COMPLETED disbursement debits for real.
    return when (to) {
        WithdrawalStatus.COMPLETED -> WithdrawalTransitionOutcome.Apply(
            nextStatus = WithdrawalStatus.COMPLETED,
            reversalRequired = false
        )
        WithdrawalStatus.PROCESSING -> WithdrawalTransitionOutcome.Apply(
            nextStatus = WithdrawalStatus.PROCESSING,
            reversalRequired = false
        )
        WithdrawalStatus.FAILED, WithdrawalStatus.REVERSED -> WithdrawalTransitionOutcome.Apply(
            nextStatus = to,
            reversalRequired = true
        )
        WithdrawalStatus.PENDING -> WithdrawalTransitionOutcome.Reject(CashoutRejectReason.NOT_AUTHENTICATED)
        WithdrawalStatus.CANCELLED -> WithdrawalTransitionOutcome.Reject(CashoutRejectReason.NOT_AUTHENTICATED)
    }
}

/**
 * The idempotency / single-active-reservation gate.
 *
 *  - If a withdrawal already carries [idempotencyKey], it is REUSED (the same operation retried) —
 *    no second withdrawal is created.
 *  - Otherwise, if the wallet already has a PENDING or PROCESSING withdrawal, a second one is
 *    blocked ([ActiveWithdrawalExists]) so a repeated button press, network retry or app restart
 *    can never stack two live reservations against the same balance.
 *  - Otherwise it is safe to proceed.
 *
 * [idempotencyKey] is scoped to the wallet's OWN withdrawals (the caller only ever passes the
 * authenticated worker's rows), so one worker can never reuse or collide with another's key.
 */
fun evaluateCashoutIdempotency(
    existingWithdrawals: List<WithdrawalSnapshot>,
    idempotencyKey: String
): CashoutIdempotencyOutcome {
    // 1. Exact-key reuse (safe retry of the SAME intended operation). Matched against the STORED
    //    idempotency key, not the row id: the row id is the key with a `wd_` prefix applied, so
    //    an id comparison would never match and every retry would be treated as a new request.
    val matching = existingWithdrawals.firstOrNull { it.idempotencyKey == idempotencyKey }
    if (matching != null) {
        return CashoutIdempotencyOutcome.ReuseExisting(matching.id)
    }
    // 2. Single active reservation gate.
    val hasActive = existingWithdrawals.any {
        val status = WithdrawalStatus.fromValue(it.status)
        status in ACTIVE_WITHDRAWAL_STATUSES
    }
    if (hasActive) {
        return CashoutIdempotencyOutcome.ActiveWithdrawalExists
    }
    // 3. Safe to create.
    return CashoutIdempotencyOutcome.Proceed
}