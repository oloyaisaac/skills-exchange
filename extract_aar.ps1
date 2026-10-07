param(
  [string]$Aar = 'd:\gradle-home\caches\modules-2\files-2.1\com.google.firebase\firebase-auth\24.1.0\fa1507898894d1e5ddd31ac764eda08a6235e115\firebase-auth-24.1.0.aar',
  [string]$Out = 'd:\skills exchange\auth-unpack'
)
if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Path $Out | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::ExtractToDirectory($Aar, $Out)
Get-ChildItem $Out -Recurse -Filter '*.jar' | Select-Object -ExpandProperty FullName
