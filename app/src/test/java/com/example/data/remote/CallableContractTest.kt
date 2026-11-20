package com.example.data.remote

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * R05-P2-06: every callable the app invokes has a typed contract.
 *
 * The client used to build an untyped `Map<String, Any>` at each call site; a renamed or forgotten
 * field only surfaced as a generic server error. These tests check three things:
 *
 *  1. the registry rejects missing, unknown and wrongly typed fields *before* the network call;
 *  2. every `getHttpsCallable("...")` name in the app source is registered;
 *  3. every registered name exists as an exported handler in `functions/src/index.ts`.
 *
 * They are static assertions over source; they cannot prove the deployed server matches the
 * registry. That needs a real deploy (`firebase deploy --only functions`), which this environment
 * does not have, so end-to-end verification remains UNVERIFIED.
 */
@RunWith(JUnit4::class)
class CallableContractTest {

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val current = dir ?: error("$relative not found from the test working directory")
            val candidate = File(current, relative)
            if (candidate.exists()) return candidate
            dir = current.parentFile
        }
        throw AssertionError("$relative not found above ${System.getProperty("user.dir")}")
    }

    private fun appSources(): List<File> =
        repoFile("app/src/main/java/com/example").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `payload validates required fields`() {
        val payload = CallableContracts.payload(
            "releaseEscrowToWorker",
            "jobId" to "job_1",
            "workerId" to "w_1"
        )
        assertEquals(mapOf("jobId" to "job_1", "workerId" to "w_1"), payload)
    }

    @Test
    fun `a missing required field is refused before the call`() {
        try {
            CallableContracts.payload("releaseEscrowToWorker", "jobId" to "job_1")
            fail("a missing workerId must be refused")
        } catch (error: CallableContractException) {
            assertTrue(error.message!!.contains("workerId"))
        }
    }

    @Test
    fun `an unknown field is refused instead of sent to the server`() {
        try {
            CallableContracts.payload("getMoMoCollectionStatus", "transactionRef" to "t", "amount" to 5)
            fail("an unregistered field must be refused")
        } catch (error: CallableContractException) {
            assertTrue(error.message!!.contains("amount"))
        }
    }

    @Test
    fun `a wrongly typed field is refused`() {
        try {
            CallableContracts.payload("setWorkerActivationStatus", "userId" to "u_1", "activate" to "yes")
            fail("activate must be a boolean")
        } catch (error: CallableContractException) {
            assertTrue(error.message!!.contains("activate"))
        }
    }

    @Test
    fun `null optionals are dropped and real optionals are kept`() {
        assertEquals(
            mapOf("status" to "OPEN"),
            CallableContracts.payload("getDisputesQueue", "status" to "OPEN")
        )
        assertEquals(
            emptyMap<String, Any>(),
            CallableContracts.payload("getDisputesQueue", "status" to null)
        )
    }

    @Test
    fun `payloads with no fields are allowed only for callables that take none`() {
        assertEquals(emptyMap<String, Any>(), CallableContracts.payload("exportMyData"))
        try {
            CallableContracts.payload("sendOtp")
            fail("sendOtp requires phoneNumber")
        } catch (error: CallableContractException) {
            assertTrue(error.message!!.contains("phoneNumber"))
        }
    }

    @Test
    fun `an unregistered callable name fails loudly`() {
        try {
            CallableContracts.payload("deleteEverything", "x" to 1)
            fail("unknown callables must be refused")
        } catch (error: CallableContractException) {
            assertTrue(error.message!!.contains("deleteEverything"))
        }
    }

    @Test
    fun `every call site in the app is registered`() {
        val pattern = Regex("getHttpsCallable\\(\\s*\"([A-Za-z0-9]+)\"")
        val invoked = appSources()
            .flatMap { file -> pattern.findAll(file.readText()).map { it.groupValues[1] }.toList() }
            .toSet()
        assertTrue("expected call sites to be found", invoked.isNotEmpty())
        val unregistered = invoked - CallableContracts.ALL.map { it.name }.toSet()
        assertEquals("callables used without a typed contract: $unregistered", emptySet<String>(), unregistered)
    }

    @Test
    fun `every registered callable is exported by the functions module`() {
        val index = repoFile("functions/src/index.ts").readText()
        val missing = CallableContracts.ALL
            .map { it.name }
            .filter { name -> !index.contains("export const $name") }
        assertEquals("registered callables with no server handler: $missing", emptyList<String>(), missing)
    }

    @Test
    fun `no call site builds a raw map payload any more`() {
        val offenders = appSources().filter { file ->
            val text = file.readText()
            text.contains(".call(mapOf") || text.contains(".call(payload)")
        }.map { it.name }
        assertEquals("raw map payloads must go through CallableContracts: $offenders", emptyList<String>(), offenders)
    }
}
