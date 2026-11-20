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
        if ($label.Length -gt 55) { $label = $label.Substring(0, 55) }
        Write-Output ('{0} {1} :: {2}' -f $tag, (Split-Path $path -Leaf), ($label -replace "`r|`n", ' '))
    }
    [IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($hasBom)))
}

$as = 'd:\skills exchange\AGENT_STATE.md'
Fix $as @(
    , @('SkillsExchangeViewModel.kt:110 Unresolved reference', 'SkillsExchangeViewModel.kt Unresolved reference')
    , @('(new). Covers the NIN structural rule including the one-repeated-digit branch fixed during this verification.', '(new). Covers phone equality, duplicate/self-guarantor guards, photo reuse, consent and the two-guarantor set; NIN structure has 2 direct tests (too-short rejected, well-formed accepted) and the one-repeated-character branch rewritten during this verification is not asserted by any test yet')
    , @('**`app/src/main/java/com/example/util/GuarantorValidators.kt`** - `required()` no longer calls', '**`app/src/main/java/com/example/util/GuarantorValidators.kt`** (untracked, added in the REG-002 session) - `validateNationalIdNumber()` no longer calls')
    , @('## Known Issues', "## Known Issues`r`n- **Nothing for REG-002 is committed yet.** ``git status`` shows 16 modified files plus **3 untracked** files - ``GuarantorValidators.kt``, ``GuarantorVerificationSheet.kt``, ``GuarantorValidatorsTest.kt``. This session ran builds and edited documentation only; committing is a mechanical follow-up for the user to approve.`r`n- **Small test gap**: no test asserts the ``validateNationalIdNumber()`` rule that rejects a NIN made of one repeated character (the branch rewritten on 2026-09-27). Behaviour is unchanged, but a follow-up test would lock it in.")
    , @("`r`n## Dependencies", "`r`n- 4. **Optional (user's call)**: commit the 16 modified + 3 untracked REG-002 files - the feature is not in git history yet.`r`n`r`n## Dependencies")
)

$cl = 'd:\skills exchange\CHANGELOG.md'
Fix $cl @(
    , @('- **PRD state**: REG-002 moved to `completed`', "- **Repository state**: nothing was committed by this session - ``git status`` reports 16 modified files plus 3 untracked REG-002 files (``GuarantorValidators.kt``, ``GuarantorVerificationSheet.kt``, ``GuarantorValidatorsTest.kt``); committing them is a mechanical follow-up.`r`n- **PRD state**: REG-002 moved to ``completed``")
)

$prd = 'd:\skills exchange\prd.json'
Fix $prd @(
    , @('19 pure-JVM unit tests', '18 pure-JVM unit tests')
    , @('GuarantorValidators.required called', 'GuarantorValidators.validateNationalIdNumber called')
)

Write-Output '--- JSON check ---'
try { $j = Get-Content $prd -Raw | ConvertFrom-Json; Write-Output ('JSON OK - stories: ' + $j.stories.Count + ' completed: ' + $j.completed_stories.Count) }
catch { Write-Output ('JSON INVALID: ' + $_.Exception.Message) }
