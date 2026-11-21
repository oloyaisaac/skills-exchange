package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PAY-002: the Android UI may only ever display payment phases the TRUSTED backend
 * reported. These tests pin the pure mapper that turns a backend status string into
 * the UI phase, with the fail-closed rule at its centre: anything that is not
 * explicitly PENDING, SUCCESSFUL or FAILED — including the backend's own UNKNOWN and
 * NEEDS_RECONCILIATION, null, and unexpected garbage — maps to NEEDS_ATTENTION and
 * NEVER to success.
 */
class MoMoPaymentUiStateTest {

    @Test
    fun `backend PENDING maps to PENDING`() {
        assertEquals(MoMoPaymentPhase.PENDING, mapBackendStatusToPhase("PENDING"))
    }

    @Test
    fun `backend SUCCESSFUL maps to SUCCESSFUL`() {
        assertEquals(MoMoPaymentPhase.SUCCESSFUL, mapBackendStatusToPhase("SUCCESSFUL"))
    }

    @Test
    fun `backend FAILED maps to FAILED`() {
        assertEquals(MoMoPaymentPhase.FAILED, mapBackendStatusToPhase("FAILED"))
    }

    @Test
    fun `backend UNKNOWN maps to NEEDS_ATTENTION - never success`() {
        assertEquals(MoMoPaymentPhase.NEEDS_ATTENTION, mapBackendStatusToPhase("UNKNOWN"))
    }

    @Test
    fun `backend NEEDS_RECONCILIATION maps to NEEDS_ATTENTION - never success`() {
        assertEquals(MoMoPaymentPhase.NEEDS_ATTENTION, mapBackendStatusToPhase("NEEDS_RECONCILIATION"))
    }

    @Test
    fun `null status maps to NEEDS_ATTENTION - never success`() {
        assertEquals(MoMoPaymentPhase.NEEDS_ATTENTION, mapBackendStatusToPhase(null))
    }

    @Test
    fun `garbage status maps to NEEDS_ATTENTION - never success`() {
        assertEquals(MoMoPaymentPhase.NEEDS_ATTENTION, mapBackendStatusToPhase("totally unexpected"))
        assertEquals(MoMoPaymentPhase.NEEDS_ATTENTION, mapBackendStatusToPhase(""))
    }

    @Test
    fun `mapping is case and whitespace insensitive for the three real states`() {
        assertEquals(MoMoPaymentPhase.PENDING, mapBackendStatusToPhase(" pending "))
        assertEquals(MoMoPaymentPhase.SUCCESSFUL, mapBackendStatusToPhase("Successful"))
        assertEquals(MoMoPaymentPhase.FAILED, mapBackendStatusToPhase(" FAILED"))
    }
}
