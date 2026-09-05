#Requires -Version 5.1

Clear-Host
Write-Host
Write-Host "Checking required services and tools..."

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Docker is not installed or not in PATH."
    exit 1
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "Java is not installed or not in PATH (needed by mvnw)."
    exit 1
}

docker info 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker daemon is NOT running."
    exit 1
}
Write-Host "Docker daemon is running."

function Port-InUse {
    param([int]$Port)
    $tcpConn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($tcpConn) { return $true }
    $dockerPorts = docker ps --format '{{.Ports}}' 2>$null
    if ($dockerPorts -match "(^|,| )[0-9.]*:${Port}->") { return $true }
    return $false
}

function Prompt-FreePort {
    param([string]$Label)
    while ($true) {
        $raw = Read-Host "Enter the $Label port"
        if ($raw -notmatch '^\d+$' -or [int]$raw -lt 1 -or [int]$raw -gt 65535) {
            Write-Host "Error: '$raw' is not a valid port number." -ForegroundColor Red
            continue
        }
        $port = [int]$raw
        if (Port-InUse $port) {
            Write-Host "Error: port $port is already in use, please choose another." -ForegroundColor Red
            continue
        }
        return $port
    }
}

function Resolve-Port {
    param([string]$Label, [int]$Default)
    if (-not (Port-InUse $Default)) { return $Default }
    Write-Host "The default $Label port ($Default) is already in use. Would you like to:" -ForegroundColor Yellow
    while ($true) {
        Write-Host "[c] Choose a custom port"
        Write-Host "[a] Auto-select the next free port above $Default"
        Write-Host
        $choice = Read-Host "(c/a)"
        switch -Wildcard ($choice.ToLower()) {
            'c*' { return Prompt-FreePort $Label }
            'a*' {
                $port = $Default
                while (Port-InUse $port) { $port++ }
                Write-Host "Auto-selected free $Label port: $port" -ForegroundColor Cyan
                return $port
            }
            default { Write-Host "Please answer 'c' or 'a'." -ForegroundColor Red }
        }
    }
}

function Prompt-SmtpProvider {
    Write-Host "Select your SMTP provider:"
    Write-Host "  [1] Gmail / Google Workspace  (smtp.gmail.com:587, STARTTLS)"
    Write-Host "  [2] Microsoft 365 / Outlook   (smtp.office365.com:587, STARTTLS)"
    Write-Host "  [3] Amazon SES                (region host:587, STARTTLS)"
    Write-Host "  [4] SendGrid                  (smtp.sendgrid.net:587, STARTTLS)"
    Write-Host "  [5] Custom / Other"
    Write-Host
    while ($true) {
        $choice = Read-Host "(1-5)"
        switch ($choice) {
            '1' {
                Write-Host
                Write-Host "Note: Gmail requires an App Password (enable 2-Step Verification first)." -ForegroundColor Yellow
                Write-Host "      Enter it exactly as Google shows it, e.g. 'abcd efgh ijkl mnop'" -ForegroundColor Yellow
                Write-Host "      (16 characters as 4 space-separated groups; keep the spaces)." -ForegroundColor Yellow
                return @{ Host = 'smtp.gmail.com'; Port = 587; Tls = 'starttls' }
            }
            '2' {
                Write-Host "Note: The account must have SMTP AUTH enabled; use an app password if MFA is on." -ForegroundColor Yellow
                return @{ Host = 'smtp.office365.com'; Port = 587; Tls = 'starttls' }
            }
            '3' {
                $sesHost = Read-Host "Enter your SES SMTP host (e.g. email-smtp.us-east-1.amazonaws.com)"
                Write-Host "Note: Use your generated SES SMTP credentials, not your AWS console login." -ForegroundColor Yellow
                return @{ Host = $sesHost; Port = 587; Tls = 'starttls' }
            }
            '4' {
                Write-Host "Note: The SMTP username is literally 'apikey'; the password is your API key." -ForegroundColor Yellow
                return @{ Host = 'smtp.sendgrid.net'; Port = 587; Tls = 'starttls' }
            }
            '5' {
                $customHost = Read-Host "Enter the SMTP host"
                $customPort = 0
                while ($true) {
                    $portInput = Read-Host "Enter the SMTP port (e.g. 587)"
                    if ($portInput -match '^\d+$' -and [int]$portInput -ge 1 -and [int]$portInput -le 65535) {
                        $customPort = [int]$portInput; break
                    }
                    Write-Host "Error: '$portInput' is not a valid port number." -ForegroundColor Red
                }
                $customTls = ''
                while ($true) {
                    Write-Host "TLS mode: [s] STARTTLS   [l] SSL/TLS   [n] none"
                    $tlsChoice = Read-Host "(s/l/n)"
                    switch -Wildcard ($tlsChoice.ToLower()) {
                        's*' { $customTls = 'starttls'; break }
                        'l*' { $customTls = 'ssl';      break }
                        'n*' { $customTls = 'none';     break }
                        default { Write-Host "Please answer 's', 'l', or 'n'." -ForegroundColor Red; continue }
                    }
                    break
                }
                return @{ Host = $customHost; Port = $customPort; Tls = $customTls }
            }
            default { Write-Host "Please choose a number from 1 to 5." -ForegroundColor Red }
        }
    }
}

function Read-SecureInput {
    param([string]$Prompt)
    $secure = Read-Host $Prompt -AsSecureString
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try   { return [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

# ---- JWT Secret ----
Clear-Host
Write-Host "--------- Welcome to the Server Setup ---------"
Write-Host
Write-Host
$jwt_secret = ''
while ($true) {
    $jwt_secret = Read-SecureInput "Enter your JWT secret (must be 64 characters)"
    Write-Host
    if ($jwt_secret.Length -eq 64) { break }
    Clear-Host
    Write-Host "--------- Welcome to the Server Setup ---------"
    Write-Host
    Write-Host
    Write-Host "Error: Secret must be exactly 64 characters (you entered $($jwt_secret.Length))" -ForegroundColor Red
    Write-Host
}

# ---- Database container name ----
Write-Host
Write-Host "Enter your database container name (WARNING: Overwrites existing container with same name):"
Write-Host
$db_container_name = Read-Host

# ---- Database password ----
Write-Host
while ($true) {
    $db_password         = Read-SecureInput "Enter your database password"
    Write-Host
    $db_password_confirm = Read-SecureInput "Confirm your database password"
    Write-Host
    if ($db_password -eq $db_password_confirm) { break }
    Clear-Host
    Write-Host "--------- Welcome to the Server Setup ---------"
    Write-Host
    Write-Host
    Write-Host "Error: Passwords do not match, please try again." -ForegroundColor Red
    Write-Host
}

# ---- Port resolution ----
Clear-Host
Write-Host
$server_port = Resolve-Port "Spring server"        8005
$db_port     = Resolve-Port "MySQL container host" 3307

# ---- SMTP setup ----
Clear-Host
Write-Host "--------- SMTP / Mail Setup ---------"
Write-Host
Write-Host
$smtp      = Prompt-SmtpProvider
$smtp_host = $smtp.Host
$smtp_port = $smtp.Port
$smtp_tls  = $smtp.Tls

Write-Host
$smtp_sender = Read-Host "Enter the sender email address"

$smtp_username = ''
while ([string]::IsNullOrEmpty($smtp_username)) {
    $smtp_username = Read-Host "Enter the SMTP username (same as the sending address for default cases)"
    if ([string]::IsNullOrEmpty($smtp_username)) { $smtp_username = $smtp_sender }
}

while ($true) {
    $smtp_password         = Read-SecureInput "Enter the SMTP password"
    Write-Host
    $smtp_password_confirm = Read-SecureInput "Confirm the SMTP password"
    Write-Host
    if ($smtp_password -eq $smtp_password_confirm) { break }
    Write-Host "Error: Passwords do not match, please try again." -ForegroundColor Red
    Write-Host
}

$smtp_tls_prop = switch ($smtp_tls) {
    'starttls' { 'spring.mail.properties.mail.smtp.starttls.enable=true' }
    'ssl'      { 'spring.mail.properties.mail.smtp.ssl.enable=true' }
    default    { '' }
}

# ---- Public base URL ----
Clear-Host
Write-Host "--------- Public Base URL ---------"
Write-Host
Write-Host
Write-Host "When a new user registers, the server emails them an account-verification"
Write-Host "link. This setting is the scheme and host that link points to - i.e. the"
Write-Host "address a browser on the user's machine uses to reach this server."
Write-Host
Write-Host "The verification path is appended automatically; enter only the protocol"
Write-Host "and host (no trailing path). For example:"
Write-Host
Write-Host "    http://123.45.67.89"
Write-Host "    https://listr.example.com"
Write-Host
Write-Host "If you are testing locally, you can use:"
Write-Host
Write-Host "    http://localhost:$server_port"
Write-Host

$verification_base_url = ''
while ($true) {
    $verification_base_url = (Read-Host "Public base URL").TrimEnd('/')
    if ($verification_base_url -match '^https?://[A-Za-z0-9.-]+(:[0-9]+)?$') { break }
    Write-Host "Error: enter something like 'http://host' or 'https://host:port'." -ForegroundColor Red
    Write-Host
}

# ---- Admin portal ----
Clear-Host
Write-Host "--------- Admin Portal Setup ---------"
Write-Host
Write-Host
$admin_email = Read-Host "Enter the admin portal email (doesn't have to be a real email)"

while ($true) {
    $admin_password         = Read-SecureInput "Enter the admin portal password"
    Write-Host
    $admin_password_confirm = Read-SecureInput "Confirm the admin portal password"
    Write-Host
    if ($admin_password -eq $admin_password_confirm) { break }
    Write-Host "Error: Passwords do not match, please try again." -ForegroundColor Red
    Write-Host
}

# ---- Summary ----
Clear-Host
Write-Host
Write-Host "--------- Configuration Summary ---------"
Write-Host ("  {0,-26} {1}" -f "Database container name:", $db_container_name)
Write-Host ("  {0,-26} {1}" -f "Spring server port:",      $server_port)
Write-Host ("  {0,-26} {1}" -f "MySQL host port:",         $db_port)
Write-Host ("  {0,-26} {1}" -f "SMTP host:",               $smtp_host)
Write-Host ("  {0,-26} {1}" -f "SMTP port:",               $smtp_port)
Write-Host ("  {0,-26} {1}" -f "SMTP username:",           $smtp_username)
Write-Host ("  {0,-26} {1}" -f "Sender email:",            $smtp_sender)
Write-Host ("  {0,-26} {1}" -f "Public base URL:",         $verification_base_url)
Write-Host ("  {0,-26} {1}" -f "Admin portal email:",      $admin_email)
Write-Host
Write-Host "Note: All of the above (including passwords) is written in plain text to"
Write-Host "      src/main/resources/application.properties and can be viewed or"
Write-Host "      changed there later."
Write-Host
$confirm = Read-Host "Are you sure you want to continue? (y/n)"
switch -Wildcard ($confirm.ToLower()) {
    'y*' { Write-Host "Proceeding..." }
    'n*' { Write-Host "Exiting..."; exit 1 }
    default { Write-Host "Invalid response"; exit 1 }
}
Write-Host

# ---- Write application.properties ----
$propsDir  = Join-Path $PSScriptRoot "src\main\resources"
New-Item -ItemType Directory -Force -Path $propsDir | Out-Null
$propsFile = Join-Path $propsDir "application.properties"

$tlsLine = if ($smtp_tls_prop) { $smtp_tls_prop } else { '' }

@"
# Spring Specific
spring.application.name=springboot
server.port=$server_port
logging.level.root=INFO
logging.level.org.springframework.security.config.annotation.authentication.configuration.InitializeUserDetailsBeanManagerConfigurer=ERROR

# Database Configuration
spring.datasource.url=jdbc:mysql://localhost:$db_port/taskdb?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false&createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=$db_password
# Name of the Docker container running MySQL (used by start_server.ps1)
db.container.name=$db_container_name

## Hibernate properties
spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false

# JWT Configuration
security.jwt.secret-key=$jwt_secret
# 1h in millisecond
security.jwt.expiration-time=3600000

# SMTP server config
smtp.sender.email=$smtp_sender
# Public scheme + host used to build verification links emailed to new users
smtp.verification.base-url=$verification_base_url

spring.mail.host=$smtp_host
spring.mail.port=$smtp_port
spring.mail.username=$smtp_username
spring.mail.password=$smtp_password
spring.mail.properties.mail.smtp.auth=true
$tlsLine

# Connection timeouts to prevent threads from hanging permanently
spring.mail.properties[mail.smtp.connectiontimeout]=5000
spring.mail.properties[mail.smtp.timeout]=3000
spring.mail.properties[mail.smtp.writetimeout]=5000

# Admin portal credentials
admin.email=$admin_email
admin.password=$admin_password
"@ | Out-File -FilePath $propsFile -Encoding utf8 -NoNewline

# ---- Deploy MySQL container ----
Write-Host "A MySQL Docker container is about to be deployed on port $db_port."
$deploy_confirm = Read-Host "Would you like to proceed? (y/n)"
if ($deploy_confirm -match '^[Yy]') {
    $exists = docker ps -a --format '{{.Names}}' 2>$null | Where-Object { $_ -eq $db_container_name }
    if ($exists) { docker rm -f $db_container_name | Out-Null }
    docker run -d `
        -e "MYSQL_ROOT_PASSWORD=$db_password" `
        -e MYSQL_DATABASE=taskdb `
        --name $db_container_name `
        -p "${db_port}:3306" `
        mysql:8.0
}

# ---- Wait for MySQL ----
Clear-Host
Write-Host "--------- Springboot Setup ---------"
Write-Host
Write-Host
Write-Host "Waiting for MySQL to accept connections (first boot can take 15-30s)..."
Write-Host

for ($i = 1; $i -le 60; $i++) {
    $running = docker ps --format '{{.Names}}' 2>$null | Where-Object { $_ -eq $db_container_name }
    if (-not $running) {
        Write-Error "MySQL container exited during startup."
        docker logs --tail 20 $db_container_name 2>&1 | Write-Host
        exit 1
    }
    docker exec $db_container_name mysqladmin ping -uroot "-p$db_password" --silent 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "MySQL is ready."
        Write-Host
        break
    }
    if ($i -eq 60) {
        Write-Error "MySQL did not become ready within 120s."
        docker logs --tail 20 $db_container_name 2>&1 | Write-Host
        exit 1
    }
    Start-Sleep -Seconds 2
}

# ---- Done ----
Clear-Host
Write-Host
Write-Host "--------- Setup Complete ---------"
Write-Host "Spring server port: $server_port"
Write-Host "MySQL host port:    $db_port"
Write-Host "SMTP host:          ${smtp_host}:${smtp_port}"
Write-Host
Write-Host "To start the server, run the following command:"
Write-Host
Write-Host ".\start_server.ps1"
