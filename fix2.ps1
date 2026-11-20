$ErrorActionPreference = 'Stop'

function Fix([string]$path, [object[]]$pairs) {
    $bytes = [IO.File]::ReadAllBytes($path)
    $hasBom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
    $text = [IO.File]::ReadAllText($path)
    foreach ($p in $pairs) {
        $before = $text
        $text = $text.Replace($p[0], $p[1])
        $tag = if ($before -eq $text) { 'MISS' } else { 'OK  ' }
        $label = $p[0]
        if ($label.Length -gt 45) { $label = $label.Substring(0, 45) }
        Write-Output ('{0} {1} :: {2}' -f $tag, (Split-Path $path -Leaf), ($label -replace "`r|`n", ' '))
    }
    [IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($hasBom)))
}

$as = 'd:\skills exchange\AGENT_STATE.md'
Fix $as @(
    , @('`required()` no longer', '`validateNationalIdNumber()` no longer')
    , @('uses `value.toSet().size` for the same', 'uses `value.toSet().size <= 1` for the same')
    , @("6 of the 18 tests cover that branch.", 'No test asserts that branch directly (the two NIN tests cover length and the accept path); behaviour is unchanged, but a follow-up test would lock the rewritten rule in.')
    , @('| Blocker | Reason | Needed |
|---|---|---|', "| Blocker | Reason | Needed |
|---|---|---|
| REG-002 code is not committed yet | ``git status`` on 2026-09-27 lists 16 modified files plus **3 untracked** files - ``GuarantorValidators.kt``, ``GuarantorVerificationSheet.kt``, ``GuarantorValidatorsTest.kt``. This session built and documented; it did not commit | ``git add`` those three files and commit, otherwise the feature is absent from git history even though it builds |
| Untested validator branch | The rewritten ``validateNationalIdNumber()`` rule (reject a NIN of one repeated character) has no direct test | A one-line test asserting a repeated-character NIN is rejected |")
)

$cl = 'd:\skills exchange\CHANGELOG.md'
Fix $cl @(
    , @('(19 tests)', '(18 tests - the 19 here on 2026-09-26 was assumed; the JUnit XML reports 18)')
)

$prd = 'd:\skills exchange\prd.json'
Write-Output '--- JSON validity after edits ---'
try { $j = Get-Content $prd -Raw | ConvertFrom-Json; Write-Output ('JSON OK - stories: ' + $j.stories.Count + ', completed: ' + $j.completed_stories.Count) }
catch { Write-Output ('JSON INVALID: ' + $_.Exception.Message) }
