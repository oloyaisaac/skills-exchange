$ErrorActionPreference = 'Stop'
$path = 'd:\skills exchange\AGENT_STATE.md'
$text = [IO.File]::ReadAllText($path)
Write-Output ('file length: ' + $text.Length)
$needle = 'cover that branch'
$ix = $text.IndexOf($needle)
Write-Output ('IndexOf [cover that branch] = ' + $ix)
if ($ix -ge 0) {
    $start = $ix - 120
    if ($start -lt 0) { $start = 0 }
    $chunk = $text.Substring($start, [Math]::Min(180, $text.Length - $start))
    Write-Output ('chunk: [' + $chunk + ']')
    $codes = @()
    foreach ($ch in $chunk.ToCharArray()) { $codes += [int][char]$ch }
    Write-Output ('codepoints: ' + ($codes -join ','))
}
Write-Output ('contains 6-of-18 phrase: ' + $text.Contains('6 of the 18 tests cover that branch.'))
Write-Output ('contains required-paren: ' + $text.Contains('required()'))
Write-Output ('BOM check: ' + ([IO.File]::ReadAllBytes($path)[0..2] -join ' '))
