$ErrorActionPreference = 'Stop'
$path = 'd:\skills exchange\AGENT_STATE.md'
$bytes = [IO.File]::ReadAllBytes($path)
$hasBom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
$t = [IO.File]::ReadAllText($path)
$log = @()

# 1. Correct the function name in the Files Recently Modified list.
$old = 'required()'
$new = 'validateNationalIdNumber()'
if ($t.Contains($old)) {
    $t = $t.Replace($old, $new)
    $t = $t.Replace('`validateNationalIdNumber()` now uses `value.toSet().size` instead of', '`validateNationalIdNumber()` now uses `value.toSet().size <= 1` instead of')
    $log += 'OK   renamed required() -> validateNationalIdNumber()'
} else { $log += 'MISS required() not found' }

# 2. Insert two open items into the Known Blockers table (after its header separator line).
$anchorPos = $t.IndexOf('## Known Blockers')
$log += ('anchor index: ' + $anchorPos)
if ($anchorPos -ge 0) {
    $sep = $t.IndexOf('---|', $anchorPos)
    if ($sep -lt 0) { $log += 'MISS separator' }
    else {
        $eol = $t.IndexOf("`n", $sep)
        $nl = "`r`n"
        if ($eol -gt 0 -and $t[$eol - 1] -ne "`r") { $nl = "`n" }
        $rows = $nl + '| REG-002 code is not committed yet | `git status` on 2026-09-27 lists 16 modified files plus **3 untracked** files - `GuarantorValidators.kt`, `GuarantorVerificationSheet.kt`, `GuarantorValidatorsTest.kt`. The 2026-09-27 session built and documented; it did not commit | `git add` those three files and commit, or the feature stays out of git history even though it builds |'
        $rows += $nl + '| Untested validator branch | The rewritten `validateNationalIdNumber()` rule (reject a NIN of one repeated character) has no direct test - the two NIN tests cover length and the accept path | One extra assertion in `GuarantorValidatorsTest` locking the rule in; behaviour today is unchanged |'
        $t = $t.Insert($eol + 1, $rows)
        $log += 'OK   inserted 2 Known Blockers rows after separator (sep=' + $sep + ', eol=' + $eol + ')'
    }
}

[IO.File]::WriteAllText($path, $t, (New-Object System.Text.UTF8Encoding($hasBom)))

# 3. Echo the patched Known Blockers section for review.
$lines = Get-Content $path
$start = ($lines | Select-String -Pattern '^## Known Blockers' | Select-Object -First 1).LineNumber
for ($i = $start; $i -le [Math]::Min($start + 7, $lines.Count); $i++) {
    $l = $lines[$i - 1]
    if ($l.Length -gt 120) { $l = $l.Substring(0, 120) + '…' }
    $log += ('  ' + $i + ': ' + $l)
}
$log += ('remaining required() count: ' + ([regex]::Matches((Get-Content $path -Raw), 'required\(\)')).Count)
$log | Set-Content -Encoding UTF8 'd:\skills exchange\diag-out.txt'
