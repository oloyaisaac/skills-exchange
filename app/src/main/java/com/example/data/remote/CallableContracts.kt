package com.example.data.remote

/**
 * R05-P2-06: one typed field of a callable request.
 *
 * A callable payload used to be an untyped `Map<String, Any>` built at each call site, so a renamed
 * or forgotten field only failed after a round trip — as a generic server error the user could not
 * act on. Every callable the app invokes is now described here, and [CallableContracts.payload]
 * refuses to send a request that does not match the description.
 */
enum class CallableValueType {
    STRING,
    NUMBER,
    BOOLEAN,
    MAP,
    LIST;

    /** True when [value] can be carried by a callable field of this type. */
    fun accepts(value: Any?): Boolean = when (this) {
        STRING -> value is String
        NUMBER -> value is Number
        BOOLEAN -> value is Boolean
        MAP -> value is Map<*, *>
        LIST -> value is List<*>
    }
}

/** Description of one callable: its function name plus required/optional typed fields. */
data class CallableContract(
    val name: String,
    val required: Map<String, CallableValueType>,
    val optional: Map<String, CallableValueType> = emptyMap()
) {
    val allowedFields: Set<String> get() = required.keys + optional.keys
}

/** A request that cannot be sent as typed; carries the offending field, never the value. */
class CallableContractException(message: String) : IllegalArgumentException(message)

/**
 * The registry of every callable this app invokes, with the field types the server expects.
 *
 * Kept as data (not reflection) so a unit test can compare it against the `getHttpsCallable("...")`
 * call sites in the client and the `export const ...` handlers in `functions/src/index.ts`.
 */
object CallableContracts {

    val ALL: List<CallableContract> = listOf(
        CallableContract(
            name = "sendOtp",
            required = mapOf("phoneNumber" to CallableValueType.STRING)
        ),
        CallableContract(
            name = "verifyOtp",
            required = mapOf(
                "phoneNumber" to CallableValueType.STRING,
                "code" to CallableValueType.STRING
            )
        ),
        CallableContract(
            name = "registerPushToken",
            required = mapOf("token" to CallableValueType.STRING),
            optional = mapOf("platform" to CallableValueType.STRING)
        ),
        CallableContract(
            name = "searchLocalPlaces",
            required = mapOf("query" to CallableValueType.STRING),
            optional = mapOf("district" to CallableValueType.STRING)
        ),
        CallableContract(
            name = "transcribeVoiceJob",
            required = mapOf("audioBase64" to CallableValueType.STRING),
            optional = mapOf("mimeType" to CallableValueType.STRING)
        ),
        CallableContract(
            name = "releaseEscrowToWorker",
            required = mapOf(
                "jobId" to CallableValueType.STRING,
                "workerId" to CallableValueType.STRING
            ),
            optional = mapOf("provider" to CallableValueType.STRING)
        ),
        CallableContract(
            name = "revokeMyTokens",
            optional = mapOf("reason" to CallableValueType.STRING)
        ),
        CallableContract(
            name = "recordConsent",
            required = mapOf(
                "version" to CallableValueType.STRING,
                "grants" to CallableValueType.MAP
            )
        ),
        CallableContract(name = "exportMyData"),
        CallableContract(name = "getAdminAccess"),
        CallableContract(name = "getPendingVerificationQueue"),
        CallableContract(
            name = "reviewWorkerApplication",
            required = mapOf(
                "userId" to CallableValueType.STRING,
                "decision" to CallableValueType.STRING
            ),
            optional = mapOf("feedback" to CallableValueType.STRING)
        ),
        CallableContract(
            name = "setWorkerActivationStatus",
            required = mapOf(
                "userId" to CallableValueType.STRING,
                "activate" to CallableValueType.BOOLEAN
            )
        ),
        CallableContract(
            name = "getDisputesQueue",
            optional = mapOf("status" to CallableValueType.STRING)
        ),
        CallableContract(
            name = "resolveDispute",
            required = mapOf(
                "disputeId" to CallableValueType.STRING,
                "resolution" to CallableValueType.STRING
            ),
            optional = mapOf(
                "resolutionType" to CallableValueType.STRING,
                "resolutionAmount" to CallableValueType.NUMBER,
                "adminNotes" to CallableValueType.STRING
            )
        ),
        CallableContract(
            name = "createMoMoCollection",
            required = mapOf("jobId" to CallableValueType.STRING),
            optional = mapOf(
                "amountUgx" to CallableValueType.NUMBER,
                "phoneNumber" to CallableValueType.STRING
            )
        ),
        CallableContract(
            name = "getMoMoCollectionStatus",
            required = mapOf("transactionRef" to CallableValueType.STRING)
        )
    )

    private val byName: Map<String, CallableContract> = ALL.associateBy { it.name }

    fun contractFor(name: String): CallableContract =
        byName[name] ?: throw CallableContractException(
            "No typed contract is registered for callable '$name'. Add it to CallableContracts.ALL."
        )

    /**
     * Validates [fields] against the contract for [name] and returns the wire payload.
     *
     * Null values are dropped (a null is "not supplied"), so an optional field can be passed as
     * `null` without polluting the request. Missing required fields, unknown fields and wrongly
     * typed values all throw [CallableContractException] *before* the network call.
     */
    fun payload(name: String, vararg fields: Pair<String, Any?>): Map<String, Any> {
        val contract = contractFor(name)
        val supplied = fields
            .filter { (_, value) -> value != null }
            .toMap()

        val unknown = supplied.keys - contract.allowedFields
        if (unknown.isNotEmpty()) {
            throw CallableContractException(
                "Callable '$name' does not accept field(s): ${unknown.sorted().joinToString(", ")}"
            )
        }

        val missing = contract.required.keys - supplied.keys
        if (missing.isNotEmpty()) {
            throw CallableContractException(
                "Callable '$name' requires field(s): ${missing.sorted().joinToString(", ")}"
            )
        }

        val misTyped = supplied.filter { (field, value) ->
            val expected = contract.required[field] ?: contract.optional[field]
            expected != null && !expected.accepts(value)
        }.keys
        if (misTyped.isNotEmpty()) {
            throw CallableContractException(
                "Callable '$name' received the wrong type for: ${misTyped.sorted().joinToString(", ")}"
            )
        }

        val result = LinkedHashMap<String, Any>()
        supplied.forEach { (field, value) -> if (value != null) result[field] = value }
        return result
    }
}
