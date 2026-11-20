package com.example.data.remote

/**
 * R08-P2-02: the server-side wallet, as the app sees it.
 *
 * The payable balance used to exist only in Room, so reinstalling the app erased it, two devices
 * showed two different balances, and nothing server-side could prove what a worker was owed. The
 * authoritative ledger and balance now live in Firestore behind the `getWallet` callable, and cashout
 * requests are decided by `requestWalletCashout` (amount, velocity limits, verified destination and
 * the provider payout are all server-side). The Room ledger stays as an offline record of what this
 * device last saw; it is never the source of truth for money.
 *
 * [ServerWalletParser] is deliberately free of Firebase and Android types so it can be unit-tested
 * with plain maps.
 */
data class ServerWalletEntry(
    val id: String,
    val kind: String,
    val amountUgx: Long,
    val direction: String,
    val description: String?,
    val createdAtMillis: Long,
    val withdrawable: Boolean
) {
    /** Negative for a debit, so the statement can be rendered without re-deriving the sign. */
    val signedAmountUgx: Long get() = if (direction.equals("DEBIT", ignoreCase = true)) -amountUgx else amountUgx
}

data class ServerWalletSnapshot(
    val balanceUgx: Long,
    val availableUgx: Long,
    val lifetimeEarnedUgx: Long,
    val lifetimeWithdrawnUgx: Long,
    val payoutNumberMasked: String,
    val cashoutMinUgx: Long,
    val cashoutDailyLimitUgx: Long,
    val cashoutMaxPerDay: Int,
    val entries: List<ServerWalletEntry>
)

data class ServerCashoutResult(
    val withdrawalId: String,
    val payoutRef: String,
    val status: String,
    val amountUgx: Long,
    val balanceUgx: Long,
    val message: String
)

data class ServerRefundStatus(
    val refundRef: String,
    val status: String,
    val amountUgx: Long,
    val message: String
) {
    val isTerminal: Boolean get() = status == "SUCCESS" || status == "FAILED"
}

/** Parsing of the callable responses, with no Firebase or Android dependency. */
object ServerWalletParser {

    private fun Any?.asMap(): Map<*, *>? = this as? Map<*, *>

    private fun Any?.asLong(default: Long = 0L): Long =
        (this as? Number)?.toLong() ?: (this as? String)?.toLongOrNull() ?: default

    private fun Any?.asString(default: String = ""): String = this as? String ?: default

    private fun Any?.asBoolean(default: Boolean = false): Boolean = this as? Boolean ?: default

    fun parseWallet(data: Any?): ServerWalletSnapshot? {
        val map = data.asMap() ?: return null
        val rawEntries = map["entries"] as? List<*> ?: emptyList<Any?>()
        val entries = rawEntries.mapNotNull { raw ->
            val entry = raw.asMap() ?: return@mapNotNull null
            ServerWalletEntry(
                id = entry["id"].asString(),
                kind = entry["kind"].asString("ADJUSTMENT"),
                amountUgx = entry["amountUgx"].asLong(),
                direction = entry["direction"].asString("CREDIT"),
                description = entry["description"] as? String,
                createdAtMillis = entry["createdAt"].asLong(),
                // A missing flag means spendable: only an explicit `false` marks a statement line.
                withdrawable = entry["withdrawable"].asBoolean(true)
            )
        }
        return ServerWalletSnapshot(
            balanceUgx = map["balanceUgx"].asLong(),
            availableUgx = map["availableUgx"].asLong(),
            lifetimeEarnedUgx = map["lifetimeEarnedUgx"].asLong(),
            lifetimeWithdrawnUgx = map["lifetimeWithdrawnUgx"].asLong(),
            payoutNumberMasked = map["payoutNumberMasked"].asString(),
            cashoutMinUgx = map["cashoutMinUgx"].asLong(),
            cashoutDailyLimitUgx = map["cashoutDailyLimitUgx"].asLong(),
            cashoutMaxPerDay = map["cashoutMaxPerDay"].asLong().toInt(),
            entries = entries
        )
    }

    fun parseCashout(data: Any?): ServerCashoutResult? {
        val map = data.asMap() ?: return null
        val withdrawalId = map["withdrawalId"].asString()
        if (withdrawalId.isEmpty()) return null
        return ServerCashoutResult(
            withdrawalId = withdrawalId,
            payoutRef = map["payoutRef"].asString(),
            status = map["status"].asString("PAYOUT_PENDING"),
            amountUgx = map["amountUgx"].asLong(),
            balanceUgx = map["balanceUgx"].asLong(),
            message = map["message"].asString()
        )
    }

    fun parseRefund(data: Any?): ServerRefundStatus? {
        val map = data.asMap() ?: return null
        val refundRef = map["refundRef"].asString()
        if (refundRef.isEmpty()) return null
        return ServerRefundStatus(
            refundRef = refundRef,
            status = map["status"].asString("PENDING"),
            amountUgx = map["amount"].asLong(),
            message = map["message"].asString()
        )
    }
}
