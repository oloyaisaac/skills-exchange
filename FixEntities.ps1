$content = Get-Content -Path "D:/skills exchange/app/src/main/java/com/example/data/db/Entities.kt"
$content = $content -replace '(?m)^(\s*)val ', '$1var '
$content | Set-Content -Path "D:/skills exchange/app/src/main/java/com/example/data/db/Entities.kt"