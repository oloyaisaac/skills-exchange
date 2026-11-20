$ErrorActionPreference = 'SilentlyContinue'
$out = @()
$f = 'd:\skills exchange\AGENT_STATE.md'
$lines = Get-Content $f
$out += ('TOTAL LINES: ' + $lines.Count)
$out += '--- HEADERS ---'
for ($i = 1; $i -le $lines.Count; $i++) {
    if ($lines[$i - 1] -match '^#{1,3} ') { $out += ($i.ToString() + ': ' + $lines[$i - 1].Trim()) }
}
$out += '--- LINES 1-60 (first 150 chars each) ---'
for ($i = 1; $i -le 60 -and $i -le $lines.Count; $i++) {
    $l = $lines[$i - 1]
    if ($l.Length -gt 150) { $l = $l.Substring(0, 150) + '…' }
    $out += ($i.ToString() + ': ' + $l)
}
$out | Set-Content -Encoding UTF8 'd:\skills exchange\inspect-out.txt'
