$procs = Get-Process -Name java -ErrorAction SilentlyContinue |
    Select-Object Id, ProcessName, StartTime, Path |
    Sort-Object StartTime
$procs | Format-Table -AutoSize
Write-Host "---"
$procs | Out-String | Select-String -Pattern 'gradle|eclipse|vs code|adoptium' | ForEach-Object { $_.Line }
