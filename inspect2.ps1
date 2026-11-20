$ErrorActionPreference = 'SilentlyContinue'
$out = @()
$patterns = @('Covers the NIN', 'GuarantorValidators\.kt\`\*\*', 'Known Issue', '## Dependencies', 'pure-JVM')
foreach ($f in @('d:\skills exchange\AGENT_STATE.md', 'd:\skills exchange\prd.json')) {
    $out += '##### ' + (Split-Path $f -Leaf)
    foreach ($p in $patterns) {
        $ms = Select-String -Path $f -Pattern $p
        foreach ($m in $ms) {
            $line = $m.Line.Trim()
            if ($line.Length -gt 330) { $line = $line.Substring(0, 330) }
            $out += ('[' + $p + '] line ' + $m.LineNumber + ' >>' + $line + '<<')
        }
    }
}
$out | Set-Content -Encoding UTF8 'd:\skills exchange\inspect-out.txt'
