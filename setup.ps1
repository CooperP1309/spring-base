#Requires -Version 5.1

Clear-Host
Write-Host @'
 _____            _              ______
/  ___|          (_)             | ___ \
\ `--. _ __  _ __ _ _ __   __ _  | |_/ / __ _ ___  ___
 `--. \ '_ \| '__| | '_ \ / _` | | ___ \/ _` / __|/ _ \
/\__/ / |_) | |  | | | | | (_| | | |_/ / (_| \__ \  __/
\____/| .__/|_|  |_|_| |_|\__, | \____/ \__,_|___/\___|
      | |                  __/ |
      |_|                 |___/
'@
Write-Host
Write-Host "(Setup)"
Write-Host
Write-Host "--------- Database Configuration ---------"
Write-Host
Write-Host
Write-Host "How should the MySQL database be setup?"
Write-Host
Write-Host "  [1] Let Spring Boot's docker compose handle it  (quick setup)"
Write-Host "  [2] I'll provide my own MySQL server (no Docker needed)"
Write-Host
$use_docker = $true
while ($true) {
    $modeChoice = Read-Host "(1/2)"
    switch ($modeChoice.Trim()) {
        '1' { $use_docker = $true;  break }
        '2' { $use_docker = $false; break }
        default { Write-Host "Please answer '1' or '2'." -ForegroundColor Red; continue }
    }
    break
}

Write-Host
Write-Host "Checking required services and tools..."

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "Java is not installed or not in PATH (needed by mvnw)."
    exit 1
}

if ($use_docker) {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Error "Docker is not installed or not in PATH."
        exit 1
    }

    docker info 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Docker daemon is NOT running."
        exit 1
    }
    Write-Host "Docker daemon is running."

    docker compose version 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Error "The Docker Compose plugin ('docker compose') is not available."
        exit 1
    }
}

function Port-InUse {
    param([int]$Port)
    $tcpConn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($tcpConn) { return $true }
    if ($use_docker) {
        $dockerPorts = docker ps --format '{{.Ports}}' 2>$null
        if ($dockerPorts -match "(^|,| )[0-9.]*:${Port}->") { return $true }
    }
    return $false
}

# Returns $true if a TCP connection to host:port succeeds within 3s.
function Test-TcpReachable {
    param([string]$TargetHost, [int]$Port)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($TargetHost, $Port)
        return ($task.Wait(3000) -and $client.Connected)
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

# Prompts for the host and port of a user-provided MySQL server and checks
# that something is accepting connections there. Returns @{ Host; Port }.
function Prompt-MySqlServer {
    while ($true) {
        while ($true) {
            $dbHost = Read-Host "MySQL host [Default: localhost]"
            if ([string]::IsNullOrEmpty($dbHost)) { $dbHost = 'localhost' }
            if ($dbHost -match '^[A-Za-z0-9.-]+$') { break }
            Write-Host "Error: '$dbHost' is not a valid hostname or IPv4 address." -ForegroundColor Red
        }
        while ($true) {
            $raw = Read-Host "MySQL port [Default: 3306]"
            if ([string]::IsNullOrEmpty($raw)) { $raw = '3306' }
            if ($raw -match '^\d+$' -and [int]$raw -ge 1 -and [int]$raw -le 65535) {
                $dbPort = [int]$raw; break
            }
            Write-Host "Error: '$raw' is not a valid port number." -ForegroundColor Red
        }

        if (Test-TcpReachable $dbHost $dbPort) {
            Write-Host "MySQL server is reachable at ${dbHost}:${dbPort}."
            return @{ Host = $dbHost; Port = $dbPort }
        }

        Write-Host "Warning: nothing is accepting connections at ${dbHost}:${dbPort}." -ForegroundColor Yellow
        $retry = $false
        while ($true) {
            Write-Host "[r] Re-enter the host and port"
            Write-Host "[k] Keep them anyway (e.g. the server isn't running yet)"
            $choice = Read-Host "(r/k)"
            if ($choice -match '^[Rr]') { $retry = $true; break }
            if ($choice -match '^[Kk]') { break }
            Write-Host "Please answer 'r' or 'k'." -ForegroundColor Red
        }
        if (-not $retry) { return @{ Host = $dbHost; Port = $dbPort } }
        Write-Host
    }
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
                Write-Host
                Write-Host "      Refer to README.md for an quick explanation of this setup." -ForegroundColor Yellow
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
Write-Host "--------- Spring Base Setup ---------"
Write-Host
Write-Host
$jwt_secret = ''
while ($true) {
    $jwt_secret = Read-SecureInput "Enter your JWT secret (must be 64 characters)"
    Write-Host
    if ($jwt_secret.Length -eq 64) { break }
    Clear-Host
    Write-Host "--------- Spring Base Setup ---------"
    Write-Host
    Write-Host
    Write-Host "Error: Secret must be exactly 64 characters (you entered $($jwt_secret.Length))" -ForegroundColor Red
    Write-Host
}

# ---- MySQL server (self-managed mode) ----
Write-Host
if (-not $use_docker) {
    Write-Host "Enter the connection details of your MySQL server."
    Write-Host
    $mysqlServer = Prompt-MySqlServer
    $db_host     = $mysqlServer.Host
    $db_port     = $mysqlServer.Port
    $db_username = Read-Host "MySQL username [Default: root]"
    if ([string]::IsNullOrEmpty($db_username)) { $db_username = 'root' }
}

# ---- Existing Docker MySQL (Docker mode) ----
$db_password_prompt = "Enter your database password"
$reset_volume = $false
if ($use_docker) {
    # A running MySQL from an earlier setup would hold its host port (and its volume).
    $runningIds = @(docker ps -q --filter "label=com.docker.compose.project=springboot" 2>$null)
    if ($runningIds.Count -gt 0) {
        Write-Host "The MySQL container from an earlier setup is still running." -ForegroundColor Yellow
        Write-Host "Stop the Spring app first if it is running, then stop the container."
        $stopChoice = Read-Host "Stop the container now? (y/n)"
        if ($stopChoice -match '^[Yy]') {
            docker stop $runningIds | Out-Null
            if ($LASTEXITCODE -ne 0) { exit 1 }
        } else {
            Write-Host "Exiting..."
            exit 1
        }
        Write-Host
    }

    # MYSQL_ROOT_PASSWORD only takes effect when the volume is empty.
    docker volume inspect springboot_mysql-data 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Existing MySQL data found (Docker volume springboot_mysql-data)." -ForegroundColor Yellow
        Write-Host "MySQL only applies a new password to an empty volume. Would you like to:"
        while ($true) {
            Write-Host "[k] Keep the data (enter the CURRENT database password next)"
            Write-Host "[r] Reset: permanently delete ALL data, including all users, and set a new password"
            Write-Host
            $volumeChoice = Read-Host "(k/r)"
            if ($volumeChoice -match '^[Kk]') {
                $db_password_prompt = "Enter your CURRENT database password"
                break
            } elseif ($volumeChoice -match '^[Rr]') {
                $resetConfirm = Read-Host "This permanently deletes all users. Are you sure? (y/n)"
                if ($resetConfirm -match '^[Yy]') { $reset_volume = $true; break }
                Write-Host
            } else {
                Write-Host "Please answer 'k' or 'r'." -ForegroundColor Red
            }
        }
    }
}

# ---- Database password ----
Write-Host
while ($true) {
    $db_password         = Read-SecureInput $db_password_prompt
    Write-Host
    # The mysql image's first-boot script puts MYSQL_ROOT_PASSWORD into SQL
    # unescaped: ' breaks initialization and \ would act as an SQL escape.
    if ($use_docker -and ($db_password.Contains("'") -or $db_password.Contains('\'))) {
        Write-Host "Error: in Docker mode the database password can't contain ' or \" -ForegroundColor Red
        Write-Host "       (the MySQL image can't initialize with them). Please choose another." -ForegroundColor Red
        Write-Host
        continue
    }
    $db_password_confirm = Read-SecureInput "Confirm your database password"
    Write-Host
    if ($db_password -eq $db_password_confirm) { break }
    Clear-Host
    Write-Host "--------- Spring Base Setup ---------"
    Write-Host
    Write-Host
    Write-Host "Error: Passwords do not match, please try again." -ForegroundColor Red
    Write-Host
}

# ---- Port resolution ----
Clear-Host
Write-Host
$server_port = Resolve-Port "Spring server"        8005
if ($use_docker) {
    $db_port = Resolve-Port "MySQL container host" 3307
}

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
Write-Host "    https://app.example.com"
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

# ---- Cookie Security ----
Clear-Host
Write-Host "--------- Cookie Security ---------"
Write-Host
Write-Host
Write-Host "The login session cookie can be marked 'Secure', which tells browsers to"
Write-Host "only ever send it over HTTPS. This is recommended whenever the server is"
Write-Host "reachable over HTTPS - either directly, or behind a reverse proxy that"
Write-Host "terminates TLS in front of it."
Write-Host
Write-Host "Answer 'n' if this deployment is only reachable over plain HTTP - for"
Write-Host "example local development, or a local/LAN server with no HTTPS in front"
Write-Host "of it. Answering 'y' in that case will silently break login: the browser"
Write-Host "will refuse to send the cookie back, since the connection isn't HTTPS."
Write-Host

$jwt_cookie_secure = 'false'
while ($true) {
    $secureChoice = Read-Host "Will users always reach this server over HTTPS? (y/n)"
    switch -Wildcard ($secureChoice.ToLower()) {
        'y*' { $jwt_cookie_secure = 'true';  break }
        'n*' { $jwt_cookie_secure = 'false'; break }
        default { Write-Host "Please answer 'y' or 'n'." -ForegroundColor Red; continue }
    }
    break
}

# ---- Captcha (Cloudflare Turnstile) ----
Clear-Host
Write-Host "--------- Captcha (Cloudflare Turnstile) ---------"
Write-Host
Write-Host
Write-Host "Cloudflare Turnstile can protect the register, login and forgot-password"
Write-Host "forms from bots. This is optional - if you skip it, the forms work exactly"
Write-Host "as before and no requests are ever made to Cloudflare."
Write-Host
Write-Host "To enable it you need a free Cloudflare account and a Turnstile widget"
Write-Host "(Cloudflare dashboard -> Turnstile -> Add widget). Add the hostname users"
Write-Host "reach this server on to the widget, then copy its Site Key and Secret Key."
Write-Host
Write-Host "For local testing, Cloudflare's always-pass test keys can be used:"
Write-Host
Write-Host "    Site key:   1x00000000000000000000AA"
Write-Host "    Secret key: 1x0000000000000000000000000000000AA"
Write-Host

$turnstile_enabled = 'false'
while ($true) {
    $captchaChoice = Read-Host "Enable Cloudflare Turnstile? (y/N)"
    switch -Wildcard ($captchaChoice.ToLower()) {
        'y*' { $turnstile_enabled = 'true';  break }
        'n*' { $turnstile_enabled = 'false'; break }
        ''   { $turnstile_enabled = 'false'; break }
        default { Write-Host "Please answer 'y' or 'n'." -ForegroundColor Red; continue }
    }
    break
}

$turnstile_site_key   = ''
$turnstile_secret_key = ''
if ($turnstile_enabled -eq 'true') {
    Write-Host
    while ([string]::IsNullOrEmpty($turnstile_site_key)) {
        $turnstile_site_key = Read-Host "Enter the Turnstile site key"
    }
    while ($true) {
        $turnstile_secret_key         = Read-SecureInput "Enter the Turnstile secret key"
        Write-Host
        $turnstile_secret_key_confirm = Read-SecureInput "Confirm the Turnstile secret key"
        Write-Host
        if ([string]::IsNullOrEmpty($turnstile_secret_key)) {
            Write-Host "Error: the secret key cannot be empty." -ForegroundColor Red
        } elseif ($turnstile_secret_key -eq $turnstile_secret_key_confirm) {
            break
        } else {
            Write-Host "Error: Secret keys do not match, please try again." -ForegroundColor Red
        }
        Write-Host
    }
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
if ($use_docker) {
    Write-Host ("  {0,-26} {1}" -f "Database mode:",           "Docker (Spring Boot Docker Compose)")
} else {
    Write-Host ("  {0,-26} {1}" -f "Database mode:",           "Self-managed MySQL")
}
Write-Host ("  {0,-26} {1}" -f "Spring server port:",      $server_port)
if ($use_docker) {
    Write-Host ("  {0,-26} {1}" -f "MySQL host port:",     $db_port)
    if ($reset_volume) {
        Write-Host ("  {0,-26} {1}" -f "Existing MySQL data:", "DELETE (reset)")
    } else {
        docker volume inspect springboot_mysql-data 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host ("  {0,-26} {1}" -f "Existing MySQL data:", "keep")
        }
    }
} else {
    Write-Host ("  {0,-26} {1}" -f "MySQL server:",        "${db_host}:${db_port}")
    Write-Host ("  {0,-26} {1}" -f "MySQL username:",      $db_username)
}
Write-Host ("  {0,-26} {1}" -f "SMTP host:",               $smtp_host)
Write-Host ("  {0,-26} {1}" -f "SMTP port:",               $smtp_port)
Write-Host ("  {0,-26} {1}" -f "SMTP username:",           $smtp_username)
Write-Host ("  {0,-26} {1}" -f "Sender email:",            $smtp_sender)
Write-Host ("  {0,-26} {1}" -f "Public base URL:",         $verification_base_url)
Write-Host ("  {0,-26} {1}" -f "Secure cookie (HTTPS-only):", $jwt_cookie_secure)
Write-Host ("  {0,-26} {1}" -f "Turnstile captcha:",       $turnstile_enabled)
if ($turnstile_enabled -eq 'true') {
    Write-Host ("  {0,-26} {1}" -f "Turnstile site key:",  $turnstile_site_key)
}
Write-Host ("  {0,-26} {1}" -f "Admin portal email:",      $admin_email)
Write-Host
Write-Host "Note: All of the above (including passwords) is written in plain text to"
Write-Host "      src/main/resources/application.properties and can be viewed or"
Write-Host "      changed there later."
if ($use_docker) {
    Write-Host "      In Docker mode the database password and port are written to"
    Write-Host "      compose.yaml instead, which is overwritten on every setup run."
}
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

if ($reset_volume) {
    # Runs before compose.yaml is regenerated, so 'down -v' uses the old file.
    Write-Host "Deleting existing MySQL data..."
    $oldCompose = Join-Path $PSScriptRoot "compose.yaml"
    if (Test-Path $oldCompose) {
        docker compose --file $oldCompose down -v
    }
    # Fallback (no compose file, or one with a different project): containers
    # still reference the volume, so remove them first.
    docker volume inspect springboot_mysql-data 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $oldIds = @(docker ps -aq --filter "label=com.docker.compose.project=springboot" 2>$null)
        if ($oldIds.Count -gt 0) { docker rm -f $oldIds | Out-Null }
        docker volume rm springboot_mysql-data | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Could not delete volume springboot_mysql-data."
            exit 1
        }
    }
    Write-Host "MySQL data deleted."
    Write-Host
}

if ($use_docker) {
    # Escape the password for compose.yaml: '$' -> '$$' (Compose interpolation),
    # then YAML single-quote it, doubling any embedded single quotes.
    $yaml_password = "'" + $db_password.Replace('$', '$$').Replace("'", "''") + "'"

    # compose.yaml and spring.docker.compose.enabled=true are always written
    # together: Compose support fails at startup without a compose file.
    # Must stay identical to the generator in setup.sh.
    $composeYaml = @"
# compose.yaml - GENERATED by setup. Do not commit. Re-running setup overwrites it.
name: springboot   # fixed project name, so the volume is always springboot_mysql-data

services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: $yaml_password
      MYSQL_DATABASE: taskdb
    ports:
      - "${db_port}:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      # 127.0.0.1 matters: MySQL's first-boot init runs with networking off, so this can't pass early
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -uroot -p\"`$`$MYSQL_ROOT_PASSWORD\" --silent"]
      interval: 5s
      timeout: 5s
      retries: 24
      start_period: 30s
    labels:
      org.springframework.boot.jdbc.parameters: "serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false"

volumes:
  mysql-data:
"@
    # LF endings, trailing newline and UTF-8 without BOM, so the file matches setup.sh's output.
    $composeFile = Join-Path $PSScriptRoot "compose.yaml"
    [System.IO.File]::WriteAllText($composeFile, ($composeYaml -replace "`r`n", "`n") + "`n",
        (New-Object System.Text.UTF8Encoding $false))

    $dbProps = @"
# Spring Boot Docker Compose support: Spring starts MySQL from compose.yaml
# and builds the datasource from it (user root, MYSQL_ROOT_PASSWORD,
# MYSQL_DATABASE and the mapped host port), so no spring.datasource.* here.
spring.docker.compose.enabled=true
# Also start MySQL for tests: the @SpringBootTest context needs a datasource.
spring.docker.compose.skip.in-tests=false
"@
} else {
    $dbProps = @"
# Spring Boot Docker Compose support is off: MySQL is managed outside of this app.
spring.docker.compose.enabled=false
spring.datasource.url=jdbc:mysql://${db_host}:${db_port}/taskdb?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false&createDatabaseIfNotExist=true
spring.datasource.username=$db_username
spring.datasource.password=$db_password
"@
}

@"
# Spring Specific
spring.application.name=springboot
server.port=$server_port
logging.level.root=INFO
logging.level.org.springframework.security.config.annotation.authentication.configuration.InitializeUserDetailsBeanManagerConfigurer=ERROR

# Database Configuration
$dbProps

## Hibernate properties
spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false

# JWT Configuration
security.jwt.secret-key=$jwt_secret
# 1h in millisecond
security.jwt.expiration-time=3600000
# Marks the jwt cookie Secure (HTTPS-only) so browsers refuse to send it over
# plain HTTP. Only turn this on if the app is reachable exclusively over
# HTTPS - directly, or via a reverse proxy that terminates TLS in front of
# it. Leave false for local/LAN HTTP-only deployments and local development,
# otherwise login will appear to silently fail (the browser will not send
# the cookie back at all).
security.jwt.cookie.secure=$jwt_cookie_secure

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

# Cloudflare Turnstile captcha on the register, login and forgot-password
# forms. When false, the widget is not rendered and tokens are not verified,
# so the keys below are ignored. Can be overridden by the environment
# variables CAPTCHA_TURNSTILE_ENABLED, CAPTCHA_TURNSTILE_SITE_KEY and
# CAPTCHA_TURNSTILE_SECRET_KEY.
captcha.turnstile.enabled=$turnstile_enabled
captcha.turnstile.site-key=$turnstile_site_key
captcha.turnstile.secret-key=$turnstile_secret_key

# Admin portal credentials
admin.email=$admin_email
admin.password=$admin_password
"@ | Out-File -FilePath $propsFile -Encoding utf8 -NoNewline

# ---- Done ----
Clear-Host
Write-Host
Write-Host "--------- Setup Complete ---------"
if ($use_docker) {
    Write-Host "Database mode:      Docker (Spring Boot Docker Compose)"
    Write-Host "Spring server port: $server_port"
    Write-Host "MySQL host port:    $db_port"
} else {
    Write-Host "Database mode:      Self-managed MySQL"
    Write-Host "Spring server port: $server_port"
    Write-Host "MySQL server:       ${db_host}:${db_port}"
}
Write-Host "SMTP host:          ${smtp_host}:${smtp_port}"
Write-Host
if (-not $use_docker -and (Test-Path (Join-Path $PSScriptRoot "compose.yaml"))) {
    Write-Host "Note: compose.yaml from an earlier Docker-mode setup was left in place."
    Write-Host "      It is ignored while spring.docker.compose.enabled=false."
    Write-Host
}
if ($use_docker) {
    Write-Host "MySQL is defined in the generated compose.yaml. Spring Boot starts it"
    Write-Host "on 'spring-boot:run' (Docker must be running) and stops it on shutdown."
    Write-Host
}
Write-Host "To start the server, run the following command:"
Write-Host
Write-Host ".\mvnw.cmd spring-boot:run"
Write-Host
