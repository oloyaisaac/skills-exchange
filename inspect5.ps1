$ErrorActionPreference = 'SilentlyContinue'
$out = @()
$lines = Get-Content 'd:\skills exchange\AGENT_STATE.md'
$out += '--- lines 416-500 (first 170 chars) ---'
for ($i = 416; $i -le 500 -and $i -le $lines.Count; $i++) {
    $l = $lines[$i - 1]
    if ($null -eq $l) { $l = '' }
    if ($l.Length -gt 170) { $l = $l.Substring(0, 170) + '…' }
    $out += ($i.ToString() + ': ' + $l)
}
$out += '--- suspicious claims across docs ---'
foreach ($f in @('AGENT_STATE.md', 'CHANGELOG.md', 'TASKS.md', 'DECISIONS.md', 'prd.json', 'AGENTS.md')) {
    foreach ($p in @('6 of the 18', '19 pure', '19 tests', 'required\(\)', 'Covers the NIN', 'distinct\(\)')) {
        $ms = Select-String -Path ('d:\skills exchange\' + $f) -Pattern $p
        foreach ($m in $ms) {
            $txt = $m.Line.Trim()
            if ($txt.Length -gt 150) { $txt = $txt.Substring(0, 150) + '…' }
            $out += ($f + ':' + $m.LineNumber + ' [' + $p + '] ' + $txt)
        }
    }
}
$out | Set-Content -Encoding UTF8 'd:\skills exchange\inspect-out.txt'
