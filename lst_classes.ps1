param(
  [string]$Jar = 'd:\skills exchange\ktx-unpack\classes.jar'
)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$Zip = [System.IO.Compression.ZipFile]::OpenRead($Jar)
$Zip.Entries | Where-Object { $_.Name.EndsWith('.class') } | ForEach-Object {
  $name = $_.Name.Replace('.class', '').Replace('/', '.')
  Write-Host $name
}
$Zip.Dispose()