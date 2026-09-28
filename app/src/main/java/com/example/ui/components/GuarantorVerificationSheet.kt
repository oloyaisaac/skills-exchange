package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.db.GuarantorEntity
import com.example.util.GuarantorInput
import com.example.util.GuarantorValidators

/** Local spacing scale — the app has no shared spacing tokens, components use literal dp. */
private val SpaceXS = 4.dp
private val SpaceSM = 8.dp
private val SpaceMD = 16.dp

/** Relationship choices offered for a guarantor. */
private val GuarantorRelationships = listOf(
    "Relative",
    "Community / Local Leader",
    "Employer",
    "Landlord",
    "Spouse / Partner",
    "Family Friend",
    "Other"
)

/** Districts offered for a guarantor — the same list the account compliance sheet uses. */
private val GuarantorDistricts = listOf(
    "Kampala Central",
    "Nakawa",
    "Makindye",
    "Kawempe",
    "Rubaga",
    "Wakiso",
    "Entebbe",
    "Mukono"
)

/**
 * REG-002: secure two-guarantor submission form. For each of the two guarantors: phone, full
 * name, relationship, National ID number, consent, a photo of the guarantor and a photo of the
 * guarantor with the worker; plus the group photo of the worker with both guarantors. Any part
 * of it can be saved as a draft and resumed later.
 *
 * The NIN field is masked, the notice states that the number is used for verification only and
 * never shown to clients, photos come from the system photo picker (no camera or storage
 * permission) and stay on the device until submission, and nothing is filed without consent.
 * Checks here are a courtesy only: repository, Firestore rules and admin review re-validate.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GuarantorVerificationSheet(
    workerId: String,
    initialGuarantors: List<GuarantorEntity>,
    initialGroupPhotoSource: String,
    isSubmitting: Boolean,
    submissionError: String?,
    submissionMessage: String?,
    onChange: (List<GuarantorEntity>, String) -> Unit,
    onSaveDraft: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    var drafts by remember(workerId) { mutableStateOf(initialGuarantors.toDrafts(workerId)) }
    var groupPhotoSource by remember(workerId) { mutableStateOf(initialGroupPhotoSource) }
    var submitAttempted by remember { mutableStateOf(false) }

    val blocker = GuarantorValidators.validateGuarantorSet(
        guarantors = drafts.map { it.toInput() },
        // The worker's own number is unknown here; the repository re-runs the check with it, so
        // a worker guaranteeing themselves is still rejected before anything is filed.
        workerPrimaryPhone = "",
        groupPhotoPath = groupPhotoSource
    )

    fun pushUp() = onChange(drafts.mapIndexed { index, draft -> draft.toEntity(workerId, index + 1) }, groupPhotoSource)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpaceMD)
                .padding(bottom = SpaceMD)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Guarantor Verification",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Two guarantors are required to verify your worker account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onDismiss) { Text("Close") }
            }

            Spacer(modifier = Modifier.height(SpaceMD))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(modifier = Modifier.padding(SpaceMD)) {
                    Text(text = "\u2139", color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Guarantor National ID numbers are encrypted, used only to verify " +
                            "the account, and are never shown to clients. Ask each guarantor for " +
                            "consent first, and take the photographs where the people are clearly " +
                            "visible.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(SpaceMD))

            val ready = drafts.count {
                it.fullName.isNotBlank() && it.nin.isNotBlank() && it.phone.isNotBlank() &&
                    it.consentProvided && it.guarantorPhotoPath.isNotBlank() &&
                    it.withWorkerPhotoPath.isNotBlank()
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VerificationStatusChip(label = "$ready of 2 guarantors ready", positive = ready == 2)
                VerificationStatusChip(label = "Group photo", positive = groupPhotoSource.isNotBlank())
            }

            Spacer(modifier = Modifier.height(SpaceMD))

            drafts.forEachIndexed { index, draft ->
                GuarantorDraftCard(
                    position = index + 1,
                    draft = draft,
                    onDraftChange = { updated ->
                        drafts = drafts.toMutableList().also { it[index] = updated }
                        pushUp()
                    }
                )
                Spacer(modifier = Modifier.height(SpaceMD))
            }

            GroupPhotoCard(
                groupPhotoSource = groupPhotoSource,
                onPicked = { uri ->
                    groupPhotoSource = uri
                    pushUp()
                }
            )

            if (submitAttempted && blocker != null) {
                Spacer(modifier = Modifier.height(SpaceSM))
                Text(
                    text = blocker,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!submissionError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(SpaceSM))
                Text(
                    text = submissionError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!submissionMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(SpaceSM))
                Text(
                    text = submissionMessage,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(SpaceMD))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        submitAttempted = false
                        pushUp()
                        onSaveDraft()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Draft")
                }
                Spacer(modifier = Modifier.width(SpaceMD))
                Button(
                    onClick = {
                        submitAttempted = true
                        pushUp()
                        if (blocker == null) onSubmit()
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Submit for Verification")
                    }
                }
            }

            Spacer(modifier = Modifier.height(SpaceSM))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "\u2713", color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Admins review guarantor submissions within 48 hours. Track the " +
                        "decision under Verification Status.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(SpaceMD))
        }
    }
}

/**
 * REG-002: one guarantor — phone, full name, relationship, district, National ID number,
 * the two photographs, and the consent checkbox.
 */
@Composable
private fun GuarantorDraftCard(
    position: Int,
    draft: GuarantorDraft,
    onDraftChange: (GuarantorDraft) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SpaceMD)) {
            Text(
                text = "Guarantor $position",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Someone who can confirm the worker's identity and has agreed to be " +
                    "listed. This cannot be the worker's own number.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(SpaceMD))
            OutlinedTextField(
                value = draft.phone,
                onValueChange = { raw ->
                    onDraftChange(draft.copy(phone = raw.filter { it.isDigit() || it == '+' }.take(15)))
                },
                label = { Text("Phone number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.fullName,
                onValueChange = { onDraftChange(draft.copy(fullName = it)) },
                label = { Text("Full name (as on the National ID)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            GuarantorChoiceField(
                label = "Relationship to worker",
                value = draft.relationship,
                options = GuarantorRelationships,
                onSelect = { onDraftChange(draft.copy(relationship = it)) }
            )
            GuarantorChoiceField(
                label = "District",
                value = draft.district,
                options = GuarantorDistricts,
                onSelect = { onDraftChange(draft.copy(district = it)) }
            )
            OutlinedTextField(
                value = draft.nin,
                onValueChange = { raw ->
                    onDraftChange(
                        draft.copy(
                            nin = raw.replace(" ", "").uppercase().take(GuarantorValidators.MAX_NIN_LENGTH)
                        )
                    )
                },
                label = { Text("National ID number") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = {
                    Text(
                        text = "Used for verification only and never shown to clients.",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(SpaceSM))
            GuarantorPhotoField(
                label = "Photo of the guarantor",
                hint = "A clear photo of the guarantor on their own.",
                source = draft.guarantorPhotoPath,
                onPicked = { uri -> onDraftChange(draft.copy(guarantorPhotoPath = uri)) }
            )
            Spacer(modifier = Modifier.height(SpaceSM))
            GuarantorPhotoField(
                label = "Photo of guarantor with worker",
                hint = "The guarantor and the worker together in one frame.",
                source = draft.withWorkerPhotoPath,
                onPicked = { uri -> onDraftChange(draft.copy(withWorkerPhotoPath = uri)) }
            )
            Spacer(modifier = Modifier.height(SpaceSM))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = draft.consentProvided,
                    onCheckedChange = { checked -> onDraftChange(draft.copy(consentProvided = checked)) }
                )
                Text(
                    text = "This guarantor knows they are listed here and agrees to be contacted " +
                        "to confirm the worker's identity.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}


/**
 * REG-002: one photo slot. The picker returns a temporary content URI which the repository
 * uploads under the worker's private Storage prefix; only the path is ever kept or displayed,
 * never a signed URL.
 */
@Composable
private fun GuarantorPhotoField(
    label: String,
    hint: String,
    source: String,
    onPicked: (String) -> Unit
) {
    var attached by remember { mutableStateOf(source.isNotBlank()) }
    var removeRequested by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            attached = true
            onPicked(uri.toString())
        }
    }
    LaunchedEffect(source) { attached = source.isNotBlank() }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(SpaceXS))
        if (attached) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(SpaceXS))
                Text(text = "Photo attached", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.width(SpaceSM))
                TextButton(onClick = { picker.launch("image/*") }) { Text("Replace") }
                TextButton(onClick = { removeRequested = true }) { Text("Remove") }
            }
        } else {
            OutlinedButton(onClick = { picker.launch("image/*") }) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(SpaceXS))
                Text("Choose photo")
            }
        }
    }
    PhotoRemoveDialog(
        opened = removeRequested,
        onConfirm = {
            removeRequested = false
            attached = false
            onPicked("")
        },
        onDismiss = { removeRequested = false }
    )
}

/**
 * REG-002: the single group photograph that must contain the worker together with both
 * guarantors.
 */
@Composable
private fun GroupPhotoCard(
    groupPhotoSource: String,
    onPicked: (String) -> Unit
) {
    var attached by remember { mutableStateOf(groupPhotoSource.isNotBlank()) }
    var removeRequested by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            attached = true
            onPicked(uri.toString())
        }
    }
    LaunchedEffect(groupPhotoSource) { attached = groupPhotoSource.isNotBlank() }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SpaceMD)) {
            Text(
                text = "Group photo",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "One photograph with the worker and both guarantors together. Faces must " +
                    "be clearly visible; it is reviewed by an admin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(SpaceSM))
            if (attached) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(SpaceXS))
                    Text(text = "Group photo attached", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(SpaceSM))
                    TextButton(onClick = { picker.launch("image/*") }) { Text("Replace") }
                    TextButton(onClick = { removeRequested = true }) { Text("Remove") }
                }
            } else {
                OutlinedButton(onClick = { picker.launch("image/*") }) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(SpaceXS))
                    Text("Choose group photo")
                }
            }
        }
    }
    PhotoRemoveDialog(
        opened = removeRequested,
        onConfirm = {
            removeRequested = false
            attached = false
            onPicked("")
        },
        onDismiss = { removeRequested = false }
    )
}

/** REG-002: read-only relationship / district picker. */
@Composable
private fun GuarantorChoiceField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowDropDown,
                        contentDescription = label
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

/**
 * REG-002: read-only state pill. Every value rendered here comes from the worker's own Room
 * rows or their own synced submission document — never from arbitrary client input.
 */
@Composable
private fun VerificationStatusChip(label: String, positive: Boolean) {
    val tint = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = SpaceSM, vertical = SpaceXS)
        )
    }
}

/** Confirmation before dropping an attached photo. */
@Composable
private fun PhotoRemoveDialog(
    opened: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!opened) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove photo?") },
        text = { Text("The attached photo will be removed from this form.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Remove") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep") }
        }
    )
}



/**
 * REG-002: the editable state of one guarantor inside the sheet. [guarantorPhotoPath] and
 * [withWorkerPhotoPath] hold either a temporary content URI (not yet uploaded) or the private
 * Storage path of an already uploaded photo.
 */
private data class GuarantorDraft(
    val id: String = "",
    val fullName: String = "",
    val phone: String = "",
    val relationship: String = "",
    val district: String = "",
    val nin: String = "",
    val consentProvided: Boolean = false,
    val guarantorPhotoPath: String = "",
    val withWorkerPhotoPath: String = ""
)

private fun GuarantorEntity.toDraft(): GuarantorDraft = GuarantorDraft(
    id = id,
    fullName = fullName,
    phone = phoneNumber,
    relationship = relationship,
    district = district,
    nin = nationalIdNumber,
    consentProvided = consentProvided,
    guarantorPhotoPath = guarantorPhotoStoragePath,
    withWorkerPhotoPath = workerWithGuarantorPhotoStoragePath
)

/** Restores stored guarantors for editing and always shows the required number of cards. */
private fun List<GuarantorEntity>.toDrafts(workerId: String): List<GuarantorDraft> {
    val restored = take(GuarantorValidators.REQUIRED_GUARANTOR_COUNT).map { it.toDraft() }
    val blanks = (restored.size until GuarantorValidators.REQUIRED_GUARANTOR_COUNT).map { index ->
        GuarantorDraft(id = "guarantor_${workerId}_${index + 1}")
    }
    return restored + blanks
}

private fun GuarantorDraft.toInput(): GuarantorInput = GuarantorInput(
    fullName = fullName,
    nationalIdNumber = nin,
    phoneNumber = phone,
    relationship = relationship,
    district = district,
    consentProvided = consentProvided,
    guarantorPhotoPath = guarantorPhotoPath,
    workerWithGuarantorPhotoPath = withWorkerPhotoPath
)

private fun GuarantorDraft.toEntity(workerKycId: String, position: Int): GuarantorEntity =
    GuarantorEntity(
        id = id.ifBlank { "guarantor_${workerKycId}_$position" },
        workerKycId = workerKycId,
        fullName = fullName.trim(),
        phoneNumber = phone.trim(),
        relationship = relationship.trim(),
        district = district.trim(),
        // KYC-003 identity-document slots stay unused: REG-002 asks for photographs instead,
        // and the admin review reads the fields below.
        idFrontStoragePath = "",
        idBackStoragePath = "",
        nationalIdNumber = nin.trim(),
        guarantorPhotoStoragePath = guarantorPhotoPath,
        workerWithGuarantorPhotoStoragePath = withWorkerPhotoPath,
        consentProvided = consentProvided,
        ninVerificationStatus = ninVerificationStatusPending()
    )

/**
 * The client may only ever write PENDING — the admin-owned NIN result is applied from the
 * synced submission document by the repository, never from this sheet.
 */
private fun ninVerificationStatusPending(): String = "PENDING"

