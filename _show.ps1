$p = 'd:\skills exchange\app\src\main\java\com\example\ui\components\AccountCenterSheets.kt'
$lines = Get-Content -LiteralPath $p
for ($i = 304; $i -lt 380 -and $i -lt $lines.Count; $i++) { "{0}`t{1}" -f ($i + 1), $lines[$i] }
