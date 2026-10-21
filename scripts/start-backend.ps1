param([switch]$Test, [switch]$Package)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envPath) {
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
    $localRepository = '-Dmaven.repo.local=' + (Join-Path $projectRoot '.tools\m2')
    if ($Test) { & mvn.cmd -B $localRepository test }
    elseif ($Package) { & mvn.cmd -B $localRepository package -DskipTests }
    else { & mvn.cmd $localRepository spring-boot:run }
    exit $LASTEXITCODE
} finally { Pop-Location }
