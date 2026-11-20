package com.example.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * SKILL-002 / H5 unit coverage for [ServiceTaxonomyCatalog.validateWorkerSkills] — the
 * trade-family compatibility engine that [com.example.data.repository.SkillsExchangeRepository.saveWorkerSkills]
 * now gates the worker's own profile-row write behind. Fails closed on unknown skills,
 * incompatible families, or too many additional skills.
 *
 * Pure-JVM test (no Android framework), mirroring [com.example.util.ProfileValidatorsTest].
 */
@RunWith(JUnit4::class)
class ServiceTaxonomyValidationTest {

    // Real catalog entries (see ServiceTaxonomy.kt).
    private val electricalPrimary = "Domestic Electrical Wiring"      // srv_001, family Electrical
    private val electricalAdditional = "Solar Panel Installation & Maintenance" // srv_002, Electrical
    private val plumbingSkill = "Pipe Leak & Burst Repair"            // srv_008, family Plumbing
    private val computingSkill = "Laptop Screen & Keyboard Repair"    // srv_061, family Computing
    private val housekeepingPrimary = "Deep Home & Move-In Cleaning"  // srv_036, family Housekeeping
    private val gardeningSkill = "Compound Leaf Raking & Sweeping"    // srv_051, family Gardening

    @Test
    fun `same-family additional skill is valid`() {
        val result = ServiceTaxonomyCatalog.validateWorkerSkills(
            electricalPrimary,
            listOf(electricalAdditional)
        )
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `primary skill resolves by id and additional by id is valid`() {
        val result = ServiceTaxonomyCatalog.validateWorkerSkills(
            "srv_001",
            listOf("srv_002")
        )
        assertTrue(result.isValid)
    }

    @Test
    fun `two additional skills within same family are valid`() {
        val result = ServiceTaxonomyCatalog.validateWorkerSkills(
            electricalPrimary,
            listOf(electricalAdditional, "Generator Servicing & Repair")
        )
        assertTrue(result.isValid)
    }

    @Test
    fun `more than two additional skills is rejected`() {
        val result = ServiceTaxonomyCatalog.validateWorkerSkills(
            electricalPrimary,
            listOf(electricalAdditional, "Generator Servicing & Repair", "Inverter & Battery Backup Setup")
        )
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("maximum of 2"))
    }

    @Test
    fun `unknown primary skill is rejected`() {
        val result = ServiceTaxonomyCatalog.validateWorkerSkills(
            "Teleportation Services",
            emptyList()
        )
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("not found"))
    }

    @Test
    fun `incompatible trade family is rejected`() {
        // Electrical primary + Plumbing additional: neither same family nor a compatible pair.
        val result = ServiceTaxonomyCatalog.validateWorkerSkills(
            electricalPrimary,
            listOf(plumbingSkill)
        )
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("same trade family"))
    }

    @Test
    fun `compatible cross-family pair is allowed`() {
        // Electrical + Computing is a declared compatible crossover.
        val result = ServiceTaxonomyCatalog.validateWorkerSkills(
            electricalPrimary,
            listOf(computingSkill)
        )
        assertTrue(result.isValid)
    }

    @Test
    fun `housekeeping and gardening compatible pair is allowed`() {
        val result = ServiceTaxonomyCatalog.validateWorkerSkills(
            housekeepingPrimary,
            listOf(gardeningSkill)
        )
        assertTrue(result.isValid)
    }

    @Test
    fun `unknown additional skill is rejected`() {
        val result = ServiceTaxonomyCatalog.validateWorkerSkills(
            electricalPrimary,
            listOf("Rocket Re-entry Heat Shield Fitting")
        )
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("unknown skill"))
    }

    @Test
    fun `findService resolves by id and name`() {
        assertNotNull(ServiceTaxonomyCatalog.findService("srv_001"))
        assertNotNull(ServiceTaxonomyCatalog.findService(electricalPrimary))
        assertNull(ServiceTaxonomyCatalog.findService("does-not-exist"))
    }

    @Test
    fun `catalog exposes all 125 services`() {
        assertEquals(125, ServiceTaxonomyCatalog.services.size)
    }
}
