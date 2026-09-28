$ErrorActionPreference = 'Stop'
$out = @()
foreach ($spec in @(
        @{ f = 'd:\skills exchange\AGENT_STATE.md'; p = '6 of the 18' },
        @{ f = 'd:\skills exchange\AGENT_STATE.md'; p = 'required\(\)' },
        @{ f = 'd:\skills exchange\CHANGELOG.md'; p = '19 tests' },
        @{ f = 'd:\skills exchange\CHANGELOG.md'; p = 'required\(\)' }
    )) {
    $text = [IO.File]::ReadAllText($spec.f)
    foreach ($m in [regex]::Matches($text, $spec.p)) {
        $start = $m.Index - 90
        if ($start -lt 0) { $start = 0 }
        $len = [Math]::Min(200, $text.Length - $start)
        $chunk = $text.Substring($start, $len)
        $out += ((Split-Path $spec.f -Leaf) + ' [' + $spec.p + '] idx=' + $m.Index)
        $out += ('   ctx>>>' + ($chunk -replace "[`r`n]", '~') + '<<<')
    }
}
$out | Set-Content -Encoding UTF8 'd:\skills exchange\diag-out.txt'
