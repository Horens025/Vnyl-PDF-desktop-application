$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$jdkCandidates = @(
    $env:JAVA_HOME,
    "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.10002-hotspot"
)
$jdk = $jdkCandidates | Where-Object { $_ -and (Test-Path (Join-Path $_ "bin\java.exe")) } | Select-Object -First 1
if (-not $jdk) {
    $jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "jdk-21*" } |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $jdk) {
    throw "JDK 21 not found. Set JAVA_HOME to a JDK 21 installation."
}

$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$env:PATH"
& "$PSScriptRoot\gradlew.bat" run --no-daemon @args
