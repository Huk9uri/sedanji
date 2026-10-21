param([switch]$Test, [switch]$Package)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
if (-not $Test -and (Test-Path -LiteralPath $envPath)) {
    foreach ($line in Get-Content -LiteralPath $envPath -Encoding utf8) {
        if ($line -match '^\s*([A-Z][A-Z0-9_]*)\s*=(.*)$') {
            $variableName = $Matches[1]
            $variableValue = $Matches[2].Trim().Trim('"').Trim("'")
            if (-not [Environment]::GetEnvironmentVariable($variableName, 'Process')) {
                [Environment]::SetEnvironmentVariable($variableName, $variableValue, 'Process')
            }
        }
    }
}
Push-Location (Join-Path $projectRoot 'backend')
try {
    $gradleHome = Join-Path $projectRoot '.tools\gradle-home'
    if ($Test) { & .\gradlew.bat --gradle-user-home $gradleHome --no-daemon test }
    elseif ($Package) { & .\gradlew.bat --gradle-user-home $gradleHome --no-daemon bootJar }
    else { & .\gradlew.bat --gradle-user-home $gradleHome --no-daemon bootRun }
    exit $LASTEXITCODE
} finally { Pop-Location }
