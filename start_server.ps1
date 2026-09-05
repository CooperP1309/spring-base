#Requires -Version 5.1

$ScriptDir = $PSScriptRoot
$Props     = Join-Path $ScriptDir "src\main\resources\application.properties"

Clear-Host
Write-Host
Write-Host "--------- Starting Spring Server ---------"
Write-Host

# ---- Prerequisites ----

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Docker is not installed or not in PATH."
    exit 1
}

$mvnw = Join-Path $ScriptDir "mvnw.cmd"
if (-not (Test-Path $mvnw)) {
    Write-Error "./mvnw.cmd not found in $ScriptDir."
    exit 1
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "Java is not installed or not in PATH (needed by mvnw)."
    exit 1
}

if (-not (Test-Path $Props)) {
    Write-Error "$Props not found. Run .\setup_server.ps1 first."
    exit 1
}

# ---- Read a single key=value from application.properties ----
function Get-Prop {
    param([string]$Key)
    foreach ($line in [System.IO.File]::ReadLines($Props)) {
        $line = $line.TrimEnd("`r").Trim()
        if ($line -like "$Key=*") { return $line.Substring($Key.Length + 1) }
    }
    return $null
}

$db_container = Get-Prop 'db.container.name'
if (-not $db_container) {
    Write-Error "'db.container.name' is not set in application.properties. Re-run .\setup_server.ps1 (or add the line manually)."
    exit 1
}

$db_password = Get-Prop 'spring.datasource.password'
if (-not $db_password) {
    Write-Error "'spring.datasource.password' is not set in application.properties."
    exit 1
}

$db_url      = Get-Prop 'spring.datasource.url'
$db_hostport = ($db_url -replace '^jdbc:mysql://', '') -replace '/.*', ''
$db_port     = $db_hostport -replace '^.*:', ''

# ---- Ensure Docker daemon is running ----
docker info 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker daemon is NOT running."
    exit 1
}
Write-Host "Docker daemon is running."

# ---- Ensure MySQL container is running ----
$isRunning = docker ps    --format '{{.Names}}' 2>$null | Where-Object { $_ -eq $db_container }
$exists    = docker ps -a --format '{{.Names}}' 2>$null | Where-Object { $_ -eq $db_container }

if ($isRunning) {
    Write-Host "MySQL container '$db_container' is already running."
} elseif ($exists) {
    Write-Host "Starting existing MySQL container '$db_container'..."
    docker start $db_container | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to start container '$db_container'."
        exit 1
    }
} else {
    Write-Error "MySQL container '$db_container' does not exist. Run .\setup_server.ps1 to create it."
    exit 1
}

# ---- Wait for MySQL to accept connections ----
Write-Host
Write-Host "Waiting for MySQL to accept connections (first boot can take 15-30s)..."

for ($i = 1; $i -le 60; $i++) {
    $running = docker ps --format '{{.Names}}' 2>$null | Where-Object { $_ -eq $db_container }
    if (-not $running) {
        Write-Error "MySQL container '$db_container' exited during startup."
        docker logs --tail 20 $db_container 2>&1 | Write-Host
        exit 1
    }
    docker exec $db_container mysqladmin ping -uroot "-p$db_password" --silent 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "MySQL is ready."
        break
    }
    if ($i -eq 60) {
        Write-Error "MySQL did not become ready within 120s."
        docker logs --tail 20 $db_container 2>&1 | Write-Host
        exit 1
    }
    Start-Sleep -Seconds 2
}

# ---- Launch Spring Boot ----
Write-Host
Write-Host "--------- Launching Spring Boot ---------"
Write-Host "Container:  $db_container"
if ($db_port) { Write-Host "MySQL port: $db_port" }
Write-Host

Set-Location $ScriptDir
& $mvnw spring-boot:run
