import os

base = r"D:\skills exchange\app\src\main\java\com\example"

# 1. Color.kt - Add missing colors
path = os.path.join(base, "ui", "theme", "Color.kt")
with open(path, "r") as f:
    content = f.read()

content += """
val Emerald100 = Color(0xFFD1FAE5)
val Emerald500 = Color(0xFF10B981)
val Emerald600 = Color(0xFF059669)
val Emerald700 = Color(0xFF047857)
val Emerald50 = Color(0xFFECFDF5)

val Amber100 = Color(0xFFFEF3C7)
val Amber500 = Color(0xFFF59E0B)
val Amber700 = Color(0xFFB45309)
val Amber800 = Color(0xFF92400E)

val Blue50 = Color(0xFFEFF6FF)
val Blue500 = Color(0xFF3B82F6)

val ForestGreen = Color(0xFF15803D)
val ForestGreen100 = Color(0xFFDCFCE7)
val ForestGreen500 = Color(0xFF22C55E)
val ForestGreen600 = Color(0xFF16A34A)

val PrimaryIndigo = Color(0xFF4F46E5)
"""

with open(path, "w") as f:
    f.write(content)

print("Fixed Color.kt")

# 2. FirebaseService.kt - Add functions import
path = os.path.join(base, "data", "remote", "FirebaseService.kt")
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    "import com.google.firebase.storage.FirebaseStorage",
    "import com.google.firebase.storage.FirebaseStorage\nimport com.google.firebase.functions.FirebaseFunctions"
)

with open(path, "w") as f:
    f.write(content)

print("Fixed FirebaseService.kt")

# 3. MoMoPaymentService.kt - Fix setLevel assignment
path = os.path.join(base, "data", "remote", "MoMoPaymentService.kt")
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    "    private val logging = HttpLoggingInterceptor { message -> Log.d(\"MoMoPaymentService\", message) }\n    logging.setLevel(HttpLoggingInterceptor.Level.BODY)",
    "    private val logging = HttpLoggingInterceptor { message -> Log.d(\"MoMoPaymentService\", message) }.apply {\n        setLevel(HttpLoggingInterceptor.Level.BODY)\n    }"
)

with open(path, "w") as f:
    f.write(content)

print("Fixed MoMoPaymentService.kt")

# 4. SkillsExchangeRepository.kt - Add flow map import
path = os.path.join(base, "data", "repository", "SkillsExchangeRepository.kt")
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    "import kotlinx.coroutines.flow.Flow\nimport kotlinx.coroutines.flow.first",
    "import kotlinx.coroutines.flow.Flow\nimport kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.flow.map"
)

with open(path, "w") as f:
    f.write(content)

print("Fixed SkillsExchangeRepository.kt")

# 5. AccountComplianceDialog.kt - Add missing imports
path = os.path.join(base, "ui", "components", "AccountComplianceDialog.kt")
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    "import androidx.compose.foundation.background",
    "import androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable"
)

content = content.replace(
    "import androidx.compose.material.icons.outlined.CameraAlt",
    "import androidx.compose.material.icons.outlined.CameraAlt\nimport androidx.compose.material.icons.outlined.Shield"
)

with open(path, "w") as f:
    f.write(content)

print("Fixed AccountComplianceDialog.kt")

# 6. AuthModal.kt - Add missing imports
path = os.path.join(base, "ui", "components", "AuthModal.kt")
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    "import androidx.compose.foundation.background",
    "import androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable"
)

content = content.replace(
    "import androidx.compose.material.icons.filled.*",
    "import androidx.compose.material.icons.filled.*\nimport androidx.compose.material.icons.outlined.Shield"
)

with open(path, "w") as f:
    f.write(content)

print("Fixed AuthModal.kt")

# 7. GpsLocationBanner.kt - Add clickable import
path = os.path.join(base, "ui", "components", "GpsLocationBanner.kt")
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    "import androidx.compose.foundation.background",
    "import androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable"
)

with open(path, "w") as f:
    f.write(content)

print("Fixed GpsLocationBanner.kt")

# 8. InteractiveMapView.kt - Add missing imports
path = os.path.join(base, "ui", "components", "InteractiveMapView.kt")
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    "import androidx.compose.foundation.background",
    "import androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable"
)

content = content.replace(
    "import androidx.compose.ui.graphics.Color",
    "import androidx.compose.ui.draw.clip\nimport androidx.compose.ui.draw.shadow\nimport androidx.compose.ui.graphics.Color"
)

with open(path, "w") as f:
    f.write(content)

print("Fixed InteractiveMapView.kt")

# 9. NotificationDrawer.kt - Add clickable import
path = os.path.join(base, "ui", "components", "NotificationDrawer.kt")
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    "import androidx.compose.foundation.background",
    "import androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable"
)

with open(path, "w") as f:
    f.write(content)

print("Fixed NotificationDrawer.kt")

# 10. ProviderDetailSheet.kt - Add missing imports, remove duplicate context
path = os.path.join(base, "ui", "components", "ProviderDetailSheet.kt")
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    "import androidx.compose.material.icons.filled.*",
    "import androidx.compose.material.icons.filled.*\nimport androidx.compose.material.icons.outlined.Shield"
)

content = content.replace(
    "import com.example.util.CurrencyRegion",
    "import com.example.util.CurrencyRegion\nimport com.example.ui.theme.Emerald600\nimport com.example.ui.theme.Blue50"
)

# Remove duplicate context
content = content.replace(
    "    }\n    val context = LocalContext.current\n\n    ModalBottomSheet(",
    "    }\n\n    ModalBottomSheet("
)

with open(path, "w") as f:
    f.write(content)

print("Fixed ProviderDetailSheet.kt")

# 11. QuickRequestDrawer.kt - Add missing color imports
path = os.path.join(base, "ui", "components", "QuickRequestDrawer.kt")
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    "import com.example.util.CurrencyRegion",
    "import com.example.util.CurrencyRegion\nimport com.example.ui.theme.Emerald100\nimport com.example.ui.theme.Emerald500\nimport com.example.ui.theme.Emerald700"
)

with open(path, "w") as f:
    f.write(content)

print("Fixed QuickRequestDrawer.kt")

# 12. ServiceCatalogPicker.kt - Add missing color imports
path = os.path.join(base, "ui", "components", "ServiceCatalogPicker.kt")
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    "import com.example.ui.theme.*",
    "import com.example.ui.theme.*\nimport com.example.ui.theme.ForestGreen\nimport com.example.ui.theme.Blue50\nimport com.example.ui.theme.Emerald50\nimport com.example.ui.theme.Emerald100"
)

with open(path, "w") as f:
    f.write(content)

print("Fixed ServiceCatalogPicker.kt")

# 13. WorkerVerificationStatusView.kt - Add missing color imports
path = os.path.join(base, "ui", "components", "WorkerVerificationStatusView.kt")
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    "import com.example.ui.theme.*",
    "import com.example.ui.theme.*\nimport com.example.ui.theme.ForestGreen\nimport com.example.ui.theme.ForestGreen100\nimport com.example.ui.theme.ForestGreen500\nimport com.example.ui.theme.ForestGreen600\nimport com.example.ui.theme.Emerald100\nimport com.example.ui.theme.Emerald500\nimport com.example.ui.theme.Emerald600\nimport com.example.ui.theme.Amber100\nimport com.example.ui.theme.Amber500\nimport com.example.ui.theme.Amber700\nimport com.example.ui.theme.Amber800\nimport com.example.ui.theme.PrimaryIndigo"
)

with open(path, "w") as f:
    f.write(content)

print("Fixed WorkerVerificationStatusView.kt")

# 14. MainScreen.kt - Add onTriggerAuth callback
path = os.path.join(base, "ui", "screens", "MainScreen.kt")
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    "    val locationPermissionLauncher = rememberLauncherForActivityResult(",
    "    val onTriggerAuth: (String) -> Unit = { viewModel.triggerAuthFlow(it) }\n\n    val locationPermissionLauncher = rememberLauncherForActivityResult("
)

with open(path, "w") as f:
    f.write(content)

print("Fixed MainScreen.kt")

# 15. SkillsExchangeViewModel.kt - Add flow import and fix duplicate job variable
path = os.path.join(base, "ui", "viewmodel", "SkillsExchangeViewModel.kt")
with open(path, "r") as f:
    content = f.read()

content = content.replace(
    "import kotlinx.coroutines.flow.*",
    "import kotlinx.coroutines.flow.*\nimport kotlinx.coroutines.flow.first"
)

# Fix duplicate job variable in submitDispute
content = content.replace(
    "                // Update job status to DISPUTED\n                val job = repository.jobs.first().find { it.id == jobId }\n                job?.let {\n                    repository.updateJobStatus(jobId, \"DISPUTED\")\n                }",
    "                // Update job status to DISPUTED\n                val disputeJob = repository.jobs.first().find { it.id == jobId }\n                disputeJob?.let {\n                    repository.updateJobStatus(jobId, \"DISPUTED\")\n                }"
)

with open(path, "w") as f:
    f.write(content)

print("Fixed SkillsExchangeViewModel.kt")

print("\nAll fixes applied!")
