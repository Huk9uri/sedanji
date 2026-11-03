#requires -Version 5.1
<#
.SYNOPSIS
Run existing Sedanji checks without installing dependencies or using the development DB.
.EXAMPLE
powershell -NoProfile -File scripts/verify.ps1 -Frontend -Python
#>
[CmdletBinding()]
param([switch]$Backend, [switch]$Frontend, [switch]$Python)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
$script:RepoRoot = Split-Path -Parent $PSScriptRoot
$script:Results = New-Object 'System.Collections.Generic.List[object]'
$script:OverallExit = 0
$all = -not ($Backend -or $Frontend -or $Python)

function Add-VerifyResult {
    param([string]$Check, [string]$Status, [string]$Kind, [string]$Reason,
          $NativeExit = $null, [bool]$Selected = $true)
    $script:Results.Add([pscustomobject]@{
        Check = $Check; Status = $Status; Kind = $Kind; Exit = $NativeExit; Reason = $Reason
    })
    Write-Host ('[{0}] {1} ({2}): {3}' -f $Status, $Check, $Kind, $Reason)
    if ($Selected -and $Status -ne 'PASS' -and $script:OverallExit -eq 0) {
        if ($null -ne $NativeExit -and $NativeExit -ne 0) { $script:OverallExit = [int]$NativeExit }
        else { $script:OverallExit = 1 }
    }
}

function Invoke-VerifyCommand {
    param([string]$Executable, [string[]]$Arguments, [string]$Directory, [switch]$Quiet)
    $lines = New-Object 'System.Collections.Generic.List[string]'
    Push-Location -LiteralPath $Directory
    try {
        # In Windows PowerShell 5.1 native stderr is an ErrorRecord. Do not let it
        # bypass exit-code capture. PowerShell 7 native exit handling is local too.
        $ErrorActionPreference = 'Continue'
        $PSNativeCommandUseErrorActionPreference = $false
        # Native commands update the global variable in Windows PowerShell 5.1;
        # a local LASTEXITCODE would hide it when the process writes no output.
        $global:LASTEXITCODE = $null
        & $Executable @Arguments 2>&1 | ForEach-Object {
            $line = $_.ToString()
            if ($line -eq 'System.Management.Automation.RemoteException') { $line = '' }
            $lines.Add($line)
            if (-not $Quiet) { Write-Host $line }
        }
        $nativeExit = $global:LASTEXITCODE
        return [pscustomobject]@{ Code = $nativeExit; Lines = @($lines.ToArray()) }
    } catch {
        # Do not print exception text: it may contain configured endpoints/credentials.
        return [pscustomobject]@{ Code = $null; Lines = @('Command could not be started.') }
    } finally { Pop-Location }
}

function Get-VerifyExecutable {
    param([string]$Name)
    $command = Get-Command -Name $Name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { return $command.Source }
    return $null
}

function Stop-VerifyArea {
    param([string[]]$Checks, [string]$Reason, $NativeExit = $null)
    foreach ($check in $Checks) { Add-VerifyResult $check 'NOT RUN' 'ENVIRONMENT' $Reason $NativeExit }
}

function Invoke-FrontendChecks {
    $checks = @('Frontend/typecheck', 'Frontend/test', 'Frontend/build')
    $directory = Join-Path $script:RepoRoot 'frontend'
    $node = Get-VerifyExecutable 'node.exe'
    $npm = Get-VerifyExecutable 'npm.cmd'
    if (-not $node -or -not $npm) { Stop-VerifyArea $checks 'Node.js or npm is missing; nothing was installed.'; return }
    foreach ($file in @('typescript/bin/tsc', 'vitest/vitest.mjs', 'vite/bin/vite.js', '@types/react/index.d.ts', '@types/react-dom/index.d.ts')) {
        if (-not (Test-Path -LiteralPath (Join-Path $directory ('node_modules/' + $file)))) {
            Stop-VerifyArea $checks 'Required node_modules files are missing; nothing was installed.'; return
        }
    }
    $nodeProbe = Invoke-VerifyCommand $node @('-e', 'const [a,b]=process.versions.node.split(String.fromCharCode(46)).map(Number);process.exit((a===20&&b>=19)||(a===22&&b>=12)||a>22?0:1)') $directory -Quiet
    if ($nodeProbe.Code -ne 0 -or $null -eq $nodeProbe.Code) {
        Stop-VerifyArea $checks 'Node.js cannot run or does not meet the current Vite 7 Node requirement.' $nodeProbe.Code; return
    }
    $dependencies = Invoke-VerifyCommand $npm @('ls', '--all', '--offline', '--json') $directory -Quiet
    if ($dependencies.Code -ne 0 -or $null -eq $dependencies.Code) {
        Stop-VerifyArea $checks 'Installed npm dependency tree is incomplete or invalid; no install was attempted.' $dependencies.Code; return
    }
    foreach ($step in @(@('typecheck', 'TYPECHECK'), @('test', 'TEST'), @('build', 'BUILD'))) {
        Write-Host ('Running npm run ' + $step[0])
        $result = Invoke-VerifyCommand $npm @('run', $step[0], '--offline') $directory
        if ($null -eq $result.Code) { Stop-VerifyArea @('Frontend/' + $step[0]) 'Command could not be started.' }
        elseif ($result.Code -eq 0) { Add-VerifyResult ('Frontend/' + $step[0]) 'PASS' $step[1] 'Command completed.' 0 }
        elseif (($result.Lines -join "`n") -match 'EPERM:|EACCES:|ENOENT:|ERR_MODULE_NOT_FOUND|Cannot find module|Cannot find package') {
            Stop-VerifyArea @('Frontend/' + $step[0]) 'A dependency or process/filesystem environment error prevented verification; see output above.' $result.Code
        }
        else { Add-VerifyResult ('Frontend/' + $step[0]) 'FAIL' $step[1] 'Command failed; see output above.' $result.Code }
    }
}

function Invoke-PythonChecks {
    $pythonExe = Get-VerifyExecutable 'python.exe'
    if (-not $pythonExe) { Stop-VerifyArea @('Python/test') 'Python is missing; nothing was installed.'; return }
    $probe = Invoke-VerifyCommand $pythonExe @('-B', '-c', 'import sys; sys.exit(0 if sys.version_info >= (3, 8) else 1)') $script:RepoRoot -Quiet
    if ($probe.Code -ne 0 -or $null -eq $probe.Code) {
        Stop-VerifyArea @('Python/test') 'Python 3.8+ could not be used.' $probe.Code; return
    }
    Write-Host 'Running the two explicit Python unittest modules (mock external APIs).'
    $result = Invoke-VerifyCommand $pythonExe @('-B', '-m', 'unittest', '-v', 'test_youtube_poc', 'test_kakao_local_poc') $script:RepoRoot
    if ($null -eq $result.Code) { Stop-VerifyArea @('Python/test') 'Python test process could not be started.' }
    elseif ($result.Code -eq 0) { Add-VerifyResult 'Python/test' 'PASS' 'TEST' 'Command completed.' 0 }
    else { Add-VerifyResult 'Python/test' 'FAIL' 'TEST' 'Python unittest failed; see output above.' $result.Code }
}

function Get-ContainerProperties {
    # Local classpath < user-home < environment, matching Testcontainers priority.
    # Only simple relevant Java property values are needed; never print their values.
    $properties = @{}
    $files = @((Join-Path $script:RepoRoot 'backend/src/main/resources/testcontainers.properties'),
               (Join-Path $script:RepoRoot 'backend/src/test/resources/testcontainers.properties'),
               (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.testcontainers.properties'))
    foreach ($file in $files) {
        if (Test-Path -LiteralPath $file) {
            foreach ($line in Get-Content -LiteralPath $file) {
                if ($line -match '^\s*([a-zA-Z][a-zA-Z0-9_.-]*)\s*[=:]\s*(.*?)\s*$') {
                    $key = $Matches[1]; $value = $Matches[2]
                    if ($value -match '\\$|\\u[0-9a-fA-F]{4}') {
                        throw 'Complex Testcontainers property encoding requires manual verification.'
                    }
                    $properties[$key] = $value -replace '\\([:= \\])', '$1'
                }
            }
        }
    }
    return $properties
}

function Get-ContainerSetting {
    param([hashtable]$Properties, [string]$Key, [string]$Default = '')
    $value = [Environment]::GetEnvironmentVariable('TESTCONTAINERS_' + $Key.ToUpperInvariant().Replace('.', '_'))
    if (-not [string]::IsNullOrEmpty($value)) { return $value }
    if ($Properties.ContainsKey($Key)) { return $Properties[$Key] }
    return $Default
}

function Test-BackendDocker {
    $docker = Get-VerifyExecutable 'docker.exe'
    if (-not $docker) { return 'Docker CLI is missing; the Engine preflight cannot run.' }
    $properties = Get-ContainerProperties
    $strategy = Get-ContainerSetting $properties 'docker.client.strategy'
    if ($strategy -and $strategy -notmatch '\.(NpipeSocketClientProviderStrategy|EnvironmentAndSystemPropertyClientProviderStrategy)$') {
        return 'Custom Testcontainers Docker strategy cannot be safely preflighted; configuration was preserved.'
    }
    if ((Get-ContainerSetting $properties 'dockerconfig.source') -eq 'autoIgnoringUserProperties') {
        foreach ($key in @('docker.host', 'docker.tls.verify', 'docker.cert.path')) { $properties.Remove($key) }
    }
    foreach ($key in @('pull.policy', 'image.substitutor', 'hub.image.name.prefix')) {
        if (Get-ContainerSetting $properties $key) {
            return 'Custom image pull/substitution settings need manual offline verification; configuration was preserved.'
        }
    }
    $dockerHost = $env:DOCKER_HOST
    if (-not $dockerHost) { $dockerHost = Get-ContainerSetting $properties 'docker.host' }
    if (-not $dockerHost) { $dockerHost = 'npipe:////./pipe/docker_engine' }
    $savedEnvironment = @{}
    try {
        # Docker contexts are CLI-only; Testcontainers uses its own host strategies.
        # CLI changes stay in this process and are restored before Gradle starts.
        foreach ($name in @('DOCKER_CONTEXT', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH')) {
            $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        }
        [Environment]::SetEnvironmentVariable('DOCKER_CONTEXT', $null, 'Process')
        foreach ($pair in @(@('DOCKER_TLS_VERIFY', 'docker.tls.verify'), @('DOCKER_CERT_PATH', 'docker.cert.path'))) {
            if (-not [Environment]::GetEnvironmentVariable($pair[0], 'Process')) {
                $value = Get-ContainerSetting $properties $pair[1]
                if ($value) { [Environment]::SetEnvironmentVariable($pair[0], $value, 'Process') }
            }
        }
        $probe = Invoke-VerifyCommand $docker @('--host', $dockerHost, 'info', '--format', '{{.OSType}}') $script:RepoRoot -Quiet
        if ($null -eq $probe.Code -or $probe.Code -ne 0) {
            return 'Docker Engine is inaccessible using the Testcontainers host/Windows named pipe; no service was started.'
        }
        if (($probe.Lines -join '').Trim() -ne 'linux') { return 'The current PostgreSQL tests require a Linux Docker Engine.' }
        # Defaults verified against the cached Testcontainers 2.0.5 classes.
        $images = @('postgres:17-alpine')
        if ((Get-ContainerSetting $properties 'ryuk.disabled') -ne 'true') {
            $images += Get-ContainerSetting $properties 'ryuk.container.image' 'testcontainers/ryuk:0.14.0'
        }
        if ((Get-ContainerSetting $properties 'checks.disable') -ne 'true') {
            $images += Get-ContainerSetting $properties 'tinyimage.container.image' 'alpine:3.17'
        }
        foreach ($image in $images) {
            $inspection = Invoke-VerifyCommand $docker @('--host', $dockerHost, 'image', 'inspect', $image, '--format', '{{.Id}}') $script:RepoRoot -Quiet
            if ($null -eq $inspection.Code -or $inspection.Code -ne 0) {
                return 'A required PostgreSQL/Testcontainers helper image is not cached; no image was pulled.'
            }
        }
        return $null
    } finally {
        foreach ($name in $savedEnvironment.Keys) {
            [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
        }
    }
}

function Invoke-BackendChecks {
    $directory = Join-Path $script:RepoRoot 'backend'
    $java = $null
    if ($env:JAVA_HOME) { $java = Join-Path $env:JAVA_HOME.Trim('"') 'bin/java.exe' }
    else { $java = Get-VerifyExecutable 'java.exe' }
    if (-not $java -or -not (Test-Path -LiteralPath $java)) { Stop-VerifyArea @('Backend/test') 'Java is missing or JAVA_HOME is invalid.'; return }
    $javaProbe = Invoke-VerifyCommand $java @('-version') $directory -Quiet
    if ($null -eq $javaProbe.Code -or $javaProbe.Code -ne 0) { Stop-VerifyArea @('Backend/test') 'Java could not be started.' $javaProbe.Code; return }
    $gradleHome = Join-Path $script:RepoRoot '.tools/gradle-home'
    $wrapperProperties = Get-Content -LiteralPath (Join-Path $directory 'gradle/wrapper/gradle-wrapper.properties') -Raw
    if ($wrapperProperties -notmatch 'gradle-(\d+\.\d+\.\d+)-bin\.zip') { Stop-VerifyArea @('Backend/test') 'Unsupported Gradle Wrapper distribution; no download attempted.'; return }
    $version = $Matches[1]
    $distribution = Join-Path $gradleHome ('wrapper/dists/gradle-' + $version + '-bin')
    $cached = @()
    if (Test-Path -LiteralPath $distribution) {
        $cached = @(Get-ChildItem -LiteralPath $distribution -Directory | Where-Object {
            (Test-Path -LiteralPath (Join-Path $_.FullName ('gradle-' + $version + '/bin/gradle.bat'))) -and
            (Test-Path -LiteralPath (Join-Path $_.FullName ('gradle-' + $version + '-bin.zip.ok')))
        })
    }
    if ($cached.Count -eq 0 -or -not (Test-Path -LiteralPath (Join-Path $directory 'gradle/wrapper/gradle-wrapper.jar'))) {
        Stop-VerifyArea @('Backend/test') 'Verified Gradle Wrapper cache is missing; no distribution was downloaded.'; return
    }
    if ((Get-Content -LiteralPath (Join-Path $directory 'build.gradle') -Raw) -notmatch 'testcontainers-bom:2\.0\.5') {
        Stop-VerifyArea @('Backend/test') 'Update the offline image preflight for the configured Testcontainers version.'; return
    }
    $dockerProblem = Test-BackendDocker
    if ($dockerProblem) { Stop-VerifyArea @('Backend/test') $dockerProblem; return }
    Write-Host 'Running offline Gradle tests with temporary Testcontainers PostgreSQL (not the development DB).'
    $started = [DateTime]::UtcNow
    $result = Invoke-VerifyCommand (Join-Path $directory 'gradlew.bat') @('--gradle-user-home', $gradleHome, '--offline', '--no-daemon', '-Porg.gradle.java.installations.auto-download=false', '--rerun-tasks', 'test') $directory
    if ($null -eq $result.Code) { Stop-VerifyArea @('Backend/test') 'Gradle could not be started.'; return }
    $text = $result.Lines -join "`n"
    $environmentPattern = 'No cached version|available for offline mode|Cannot find a Java installation|No matching toolchains|Could not find a valid Docker environment|Could not connect to Ryuk|Could not start Ryuk|Could not pull image|ContainerLaunchException|DockerException|DockerClientProviderStrategy'
    if ($result.Code -ne 0) {
        if ($text -match $environmentPattern) { Stop-VerifyArea @('Backend/test') 'Gradle/Testcontainers infrastructure failed; this is not an assertion-test result.' $result.Code }
        elseif ($text -match 'There were failing tests') { Add-VerifyResult 'Backend/test' 'FAIL' 'TEST' 'Gradle reported failing tests.' $result.Code }
        else { Add-VerifyResult 'Backend/test' 'FAIL' 'BUILD/COMMAND' 'Gradle failed before a successful test result; inspect output.' $result.Code }
        return
    }
    # A successful task process is insufficient if tests were skipped or absent.
    $reportsPath = Join-Path $directory 'build/test-results/test'
    $reports = @(Get-ChildItem -LiteralPath $reportsPath -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue | Where-Object { $_.LastWriteTimeUtc -ge $started.AddSeconds(-2) })
    $tests = 0; $failures = 0; $skipped = 0
    foreach ($report in $reports) {
        [xml]$xml = Get-Content -LiteralPath $report.FullName -Raw
        $tests += [int]$xml.testsuite.tests
        $failures += [int]$xml.testsuite.failures + [int]$xml.testsuite.errors
        $skipped += [int]$xml.testsuite.skipped
    }
    if ($failures -gt 0) { Add-VerifyResult 'Backend/test' 'FAIL' 'TEST' 'JUnit reports contain failures despite process exit 0.' 1 }
    elseif ($tests -eq 0 -or $skipped -gt 0) { Stop-VerifyArea @('Backend/test') 'No fresh executed tests, or skipped tests were reported; full verification is incomplete.' }
    else { Add-VerifyResult 'Backend/test' 'PASS' 'TEST' ($tests.ToString() + ' tests; no failures or skips.') 0 }
}

foreach ($area in @('Backend', 'Frontend', 'Python')) {
    $selected = $all -or ($area -eq 'Backend' -and $Backend) -or ($area -eq 'Frontend' -and $Frontend) -or ($area -eq 'Python' -and $Python)
    if (-not $selected) { Add-VerifyResult $area 'NOT RUN' 'NOT SELECTED' 'Area not selected.' $null $false; continue }
    try {
        switch ($area) {
            'Backend' { Invoke-BackendChecks }
            'Frontend' { Invoke-FrontendChecks }
            'Python' { Invoke-PythonChecks }
        }
    } catch { Stop-VerifyArea @($area + '/verification') 'Preflight/report processing failed; inspect the environment. Other areas continue.' }
}

Write-Host "`nVerification summary"
$script:Results | Format-Table Check, Status, Kind, Exit, Reason -AutoSize -Wrap | Out-Host
Write-Host ('Overall exit code: ' + $script:OverallExit)
exit $script:OverallExit
