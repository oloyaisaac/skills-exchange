param(
  [string]$Url = 'https://repo1.maven.org/maven2/com/google/firebase/firebase-bom/34.15.0/firebase-bom-34.15.0.pom',
  [string]$Out = 'd:\skills exchange\bom-ktx.xml'
)
Invoke-WebRequest -Uri $Url -OutFile $Out
$Xml = [xml] (Get-Content $Out)
$Ns = New-Object System.Xml.XmlNamespaceManager($Xml.NameTable)
$Ns.AddNamespace('ns', 'http://maven.apache.org/POM/4.0.0')
$Props = $Xml.SelectNodes('//ns:properties', $Ns)
$Props | Select-Object -ExpandProperty 'Property' | Where-Object { $_.key -match 'firebase-auth-ktx' } | Format-List
