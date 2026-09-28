$ErrorActionPreference = 'SilentlyContinue'
$out = @()
$lines = Get-Content 'd:\skills exchange\AGENT_STATE.md'
$out += '=== AGENT_STATE.md lines 20-52 (index, length, head) ==='
for ($i = 20; $i -le 52; $i++) {
    $l = $lines[$i - 1]
    if ($null -eq $l) { $l = '' }
    $head = if ($l.Length -gt 70) { $l.Substring(0, 70) } else { $l }
    $out += ('{0}  len={1}  {2}' -f $i, $l.Length, $head)
}
$out += '=== CHANGELOG.md lines 3-16 ==='
$cl = Get-Content 'd:\skills exchange\CHANGELOG.md'
for ($i = 3; $i -le 16; $i++) {
    $l = $cl[$i - 1]
    if ($null -eq $l) { $l = '' }
    $head = if ($l.Length -gt 70) { $l.Substring(0, 70) } else { $l }
    $out += ('{0}  len={1}  {2}' -f $i, $l.Length, $head)
}
$out += '=== prd.json lines containing pure-JVM ==='
Select-String -Path 'd:\skills exchange\prd.json' -Pattern 'pure-JVM' | ForEach-Object { $out += ('line ' + $_.LineNumber + ' len=' + $_.Line.Length) }
$out | Set-Content -Encoding UTF8 'd:\skills exchange\inspect-out.txt'
