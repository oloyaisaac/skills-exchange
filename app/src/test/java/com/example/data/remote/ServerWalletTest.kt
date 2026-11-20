package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * R08-P2-02: the client reads the server wallet, and a malformed response is refused instead of
 * being guessed at.
 *
 * The wallet used to exist only in Room; these tests pin the contract with the new `getWallet` /
 * `requestWalletCashout` / `getRefundStatus` callables, including the display-side rule that a
 * statement line (money the provider already paid out) is never counted as spendable.
 */
@RunWith(JUnit4::class)
class ServerWalletTest {

    private fun walletPayload(): Map<String, Any> = mapOf(
        "balanceUgx" to 25_000L,
        "availableUgx" to 25_000,
        "lifetimeEarnedUgx" to 120_000.0,
        "lifetimeWithdrawnUgx" to "95000",
        "payoutNumberMasked" to "+25677***567",
        "cashoutMinUgx" to 5_000L,
        "cashoutDailyLimitUgx" to 2_000_000L,
        "cashoutMaxPerDay" to 3L,
        "entries" to listOf(
            mapOf(
                "id" to "EARN-PAYOUT-job_1",
                "kind" to "EARNING",
                "amountUgx" to 120_000L,
                "direction" to "CREDIT",
                "description" to "Escrow released to your wallet",
                "createdAt" to 1_700_000_000_000L,
                "withdrawable" to false
            ),
            mapOf(
                "id" to "WD-req_1",
                "kind" to "RESERVE",
                "amountUgx" to 10_000L,
                "direction" to "DEBIT",
                "createdAt" to 1_700_000_100_000L
            )
        )
    )

    @Test
    fun `a wallet response is parsed without trusting the number types`() {
        val wallet = ServerWalletParser.parseWallet(walletPayload())!!

        assertEquals(25_000L, wallet.balanceUgx)
        assertEquals(25_000L, wallet.availableUgx)
        assertEquals(120_000L, wallet.lifetimeEarnedUgx)
        assertEquals(95_000L, wallet.lifetimeWithdrawnUgx)
        assertEquals("+25677***567", wallet.payoutNumberMasked)
        assertEquals(2_000_000L, wallet.cashoutDailyLimitUgx)
        assertEquals(3, wallet.cashoutMaxPerDay)
        assertEquals(2, wallet.entries.size)
    }

    @Test
    fun `a statement line is not spendable while a debit is negative`() {
        val wallet = ServerWalletParser.parseWallet(walletPayload())!!
        val earning = wallet.entries[0]
        val reserve = wallet.entries[1]

        assertEquals(120_000L, earning.amountUgx)
        assertTrue("the release is a statement line", !earning.withdrawable)
        assertEquals(120_000L, earning.signedAmountUgx)

        // JUnit4 takes the message FIRST; the previous order does not compile.
        assertEquals("a debit must render with a minus sign", -10_000L, reserve.signedAmountUgx)
        assertTrue("an entry without a flag defaults to spendable", reserve.withdrawable)
    }

    @Test
    fun `a response that is not a wallet is refused`() {
        assertNull(ServerWalletParser.parseWallet(null))
        assertNull(ServerWalletParser.parseWallet("nonsense"))
        assertNull(ServerWalletParser.parseWallet(listOf(1, 2, 3)))

        val empty = ServerWalletParser.parseWallet(emptyMap<String, Any>())
        assertEquals(0L, empty?.balanceUgx)
        assertEquals(0L, empty?.availableUgx)
    }

    @Test
    fun `a cashout response requires the withdrawal id`() {
        val parsed = ServerWalletParser.parseCashout(
            mapOf(
                "withdrawalId" to "WD-uid-req_1",
                "payoutRef" to "WD-uid-req_1",
                "status" to "PAYOUT_PENDING",
                "amountUgx" to 15_000L,
                "balanceUgx" to 10_000L,
                "message" to "UGX 15000 is on its way."
            )
        )!!
        assertEquals("WD-uid-req_1", parsed.withdrawalId)
        assertEquals("PAYOUT_PENDING", parsed.status)
        assertEquals(15_000L, parsed.amountUgx)
        assertEquals(10_000L, parsed.balanceUgx)

        assertNull(ServerWalletParser.parseCashout(mapOf("status" to "PAYOUT_PENDING")))
    }

    @Test
    fun `a refund status is terminal only when the provider settled it`() {
        val refund = ServerWalletParser.parseRefund(
            mapOf("refundRef" to "REFUND-1", "status" to "SUCCESS", "amount" to 20_000L, "message" to "ok")
        )!!
        assertEquals("SUCCESS", refund.status)
        assertEquals(20_000L, refund.amountUgx)
        assertTrue(refund.isTerminal)

        val pending = ServerWalletParser.parseRefund(mapOf("refundRef" to "REFUND-2", "status" to "PENDING"))!!
        assertFalse(pending.isTerminal)

        assertNull(ServerWalletParser.parseRefund(mapOf("status" to "SUCCESS")))
    }

    @Test
    fun `the wallet callables are registered with typed fields`() {
        assertEquals(
            mapOf("amountUgx" to 5_000L, "requestId" to "req_1234"),
            CallableContracts.payload("requestWalletCashout", "amountUgx" to 5_000L, "requestId" to "req_1234")
        )
        assertEquals(emptyMap<String, Any>(), CallableContracts.payload("getWallet"))
        assertEquals(
            mapOf("refundRef" to "REFUND-1"),
            CallableContracts.payload("getRefundStatus", "refundRef" to "REFUND-1")
        )

        try {
            CallableContracts.payload("requestWalletCashout", "amountUgx" to 5_000L)
            fail("requestId is required for idempotency")
        } catch (error: CallableContractException) {
            assertTrue(error.message!!.contains("requestId"))
        }

        try {
            CallableContracts.payload("requestWalletCashout", "amountUgx" to 5_000L, "requestId" to "r", "balance" to 1)
            fail("a client cannot send a balance")
        } catch (error: CallableContractException) {
            assertTrue(error.message!!.contains("balance"))
        }

        try {
            CallableContracts.payload("requestWalletCashout", "amountUgx" to "5000", "requestId" to "r")
            fail("the amount must be a number")
        } catch (error: CallableContractException) {
            assertTrue(error.message!!.contains("amountUgx"))
        }
    }
}
