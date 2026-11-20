package com.example.data.remote

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * R08-P2-02 / R08-P2-07: the callable surface for the server wallet, cashouts and refund status.
 *
 * Every decision is made by the backend: the balance comes from the server ledger, the cashout
 * amount is checked against the server balance and the velocity limits, the payout destination comes
 * from the verified profile, and the provider payout goes through the same disbursement core as the
 * escrow release. The app only reports what the server said.
 */
class ServerWalletService(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {

    /** The worker's authoritative balance, limits and recent statement lines. */
    suspend fun fetchWallet(): Result<ServerWalletSnapshot> = call("getWallet") { data ->
        ServerWalletParser.parseWallet(data)
            ?: throw IllegalStateException("The wallet response could not be read.")
    }

    /**
     * Request a cashout. [requestId] is the idempotency key: retrying the same request after a
     * network failure resolves to the same withdrawal instead of paying twice.
     */
    suspend fun requestCashout(amountUgx: Long, requestId: String): Result<ServerCashoutResult> {
        require(amountUgx > 0) { "amountUgx must be positive" }
        require(requestId.isNotBlank()) { "requestId is required for idempotency" }
        return call("requestWalletCashout") { data ->
            ServerWalletParser.parseCashout(data)
                ?: throw IllegalStateException("The withdrawal response could not be read.")
        }
    }

    /** Cancel a cashout that is still with us; the server refuses once the provider holds it. */
    suspend fun cancelCashout(requestId: String): Result<Unit> {
        require(requestId.isNotBlank()) { "requestId is required" }
        return call("cancelWalletCashout", "requestId" to requestId) { Unit }
    }

    /** Provider-verified refund status (R08-P2-07). */
    suspend fun fetchRefundStatus(refundRef: String): Result<ServerRefundStatus> {
        require(refundRef.isNotBlank()) { "refundRef is required" }
        return call("getRefundStatus") { data ->
            ServerWalletParser.parseRefund(data)
                ?: throw IllegalStateException("The refund response could not be read.")
        }
    }

    /**
     * One callable round trip with the payload validated against the typed contract first.
     *
     * A user-facing failure is reported with the server's message: an `HttpsError` from the backend
     * is a deliberate refusal (below the minimum, over the daily limit, unverified number), so it is
     * shown verbatim rather than replaced by a generic string.
     */
    private suspend fun <T> call(
        name: String,
        vararg fields: Pair<String, Any?>,
        parse: (Any?) -> T
    ): Result<T> {
        return try {
            // The payload is validated against the typed contract before it is sent.
            val request = CallableContracts.payload(name, *fields)
            val result = functions.getHttpsCallable(name).call(request).await()
            Result.success(parse(result.data))
        } catch (error: FirebaseFunctionsException) {
            Log.w("ServerWalletService", "$name refused: ${error.code} ${error.message}")
            Result.failure(ServerWalletException(error.message ?: "The request was refused.", error.code.name))
        } catch (error: Exception) {
            Log.e("ServerWalletService", "$name failed: ${error.message}")
            Result.failure(ServerWalletException(error.message ?: "The request failed.", "UNAVAILABLE"))
        }
    }
}

/** A refusal that is safe to show the user: the message comes from the server. */
class ServerWalletException(
    override val message: String,
    val code: String
) : Exception(message)
