$ErrorActionPreference = 'SilentlyContinue'
$out = @()
$lines = Get-Content 'd:\skills exchange\AGENT_STATE.md'
foreach ($r in @(@(54, 96), @(416, 477), @(478, 531), @(382, 394))) {
    $out += ('--- lines ' + $r[0] + '-' + $r[1] + ' ---')
    for ($i = $r[0]; $i -le $r[1] -and $i -le $lines.Count; $i++) {
        $l = $lines[$i - 1]
        if ($null -eq $l) { $l = '' }
        if ($l.Length -gt 190) { $l = $l.Substring(0, 190) + '…' }
        $out += ($i.ToString() + ': ' + $l)
    }
}
$out | Set-Content -Encoding UTF8 'd:\skills exchange\inspect-out.txt'
