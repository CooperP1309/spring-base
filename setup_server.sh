#!/usr/bin/env bash

clear
echo
echo "Checking required services and tools..."

if ! command -v mvn &> /dev/null; then
    echo "Error: Maven (mvn) is not installed or not in PATH." >&2
    exit 1
fi

if ! command -v docker &> /dev/null; then
    echo "Error: Docker is not installed or not in PATH." >&2
    exit 1
fi

if ! command -v awk &> /dev/null; then
    echo "Error: awk is not installed or not in PATH." >&2
    exit 1
fi

if docker info >/dev/null 2>&1; then
    echo "Docker daemon is running."
else
    echo "Docker daemon is NOT running."
    exit 1
fi

# Returns 0 (success) if the given TCP port is already taken, either by a
# listening socket on the host or by a running Docker container's published port.
port_in_use() {
    local port=$1
    if command -v ss >/dev/null 2>&1; then
        ss -Htln | awk '{print $4}' | grep -qE "[:.]${port}\$" && return 0
    elif command -v lsof >/dev/null 2>&1; then
        lsof -iTCP:"$port" -sTCP:LISTEN -Pn >/dev/null 2>&1 && return 0
    fi
    docker ps --format '{{.Ports}}' | grep -qE "(^|,| )[0-9.]*:${port}->" && return 0
    return 1
}

# Prompts for a valid, free port, re-prompting on bad or taken input.
prompt_free_port() {
    local label=$1 port
    while true; do
        read -rp "Enter the $label port: " port || { echo "Error: no input." >&2; exit 1; }
        if ! [[ $port =~ ^[0-9]+$ ]] || (( port < 1 || port > 65535 )); then
            echo "Error: '$port' is not a valid port number." >&2
            continue
        fi
        if port_in_use "$port"; then
            echo "Error: port $port is already in use, please choose another." >&2
            continue
        fi
        printf '%s\n' "$port"
        return
    done
}

# Resolves a port for a service: uses the default if free, otherwise lets the
# user pick a custom port or auto-select the next free one above the default.
resolve_port() {
    local label=$1 default=$2 port choice

    if ! port_in_use "$default"; then
        printf '%s\n' "$default"
        return
    fi

    echo "The default $label port ($default) is already in use. Would you like to:" >&2
    while true; do
        echo "[c] Choose a custom port" >&2
        echo "[a] Auto-select the next free port above $default" >&2
        echo >&2
        read -rp "(c/a): " choice \
            || { echo "Error: no input." >&2; exit 1; }
        case "$choice" in
            [Cc]* )
                prompt_free_port "$label"
                return
                ;;
            [Aa]* )
                port=$default
                while port_in_use "$port"; do
                    port=$((port + 1))
                done
                echo "Auto-selected free $label port: $port" >&2
                printf '%s\n' "$port"
                return
                ;;
            * )
                echo "Please answer 'c' or 'a'." >&2
                ;;
        esac
    done
}

# Prompts for an SMTP provider and echoes "host port tlsmode" on stdout, where
# tlsmode is one of: starttls | ssl | none. Provider-specific hints and any
# extra prompts (custom host/port) are written to stderr.
prompt_smtp_provider() {
    local choice host port tls
    echo "Select your SMTP provider:" >&2
    echo "  [1] Gmail / Google Workspace  (smtp.gmail.com:587, STARTTLS)" >&2
    echo "  [2] Microsoft 365 / Outlook   (smtp.office365.com:587, STARTTLS)" >&2
    echo "  [3] Amazon SES                (region host:587, STARTTLS)" >&2
    echo "  [4] SendGrid                  (smtp.sendgrid.net:587, STARTTLS)" >&2
    echo "  [5] Custom / Other" >&2
    echo >&2
    while true; do
        read -rp "(1-5): " choice || { echo "Error: no input." >&2; exit 1; }
        case "$choice" in
            1 )
                host=smtp.gmail.com; port=587; tls=starttls
                echo >&2
                echo "Note: Gmail requires an App Password (enable 2-Step Verification first)." >&2
                echo "      Enter it exactly as Google shows it, e.g. 'abcd efgh ijkl mnop'" >&2
                echo "      (16 characters as 4 space-separated groups; keep the spaces)." >&2
                break
                ;;
            2 )
                host=smtp.office365.com; port=587; tls=starttls
                echo "Note: The account must have SMTP AUTH enabled; use an app password if MFA is on." >&2
                break
                ;;
            3 )
                read -rp "Enter your SES SMTP host (e.g. email-smtp.us-east-1.amazonaws.com): " host \
                    || { echo "Error: no input." >&2; exit 1; }
                port=587; tls=starttls
                echo "Note: Use your generated SES SMTP credentials, not your AWS console login." >&2
                break
                ;;
            4 )
                host=smtp.sendgrid.net; port=587; tls=starttls
                echo "Note: The SMTP username is literally 'apikey'; the password is your API key." >&2
                break
                ;;
            5 )
                read -rp "Enter the SMTP host: " host || { echo "Error: no input." >&2; exit 1; }
                while true; do
                    read -rp "Enter the SMTP port (e.g. 587): " port \
                        || { echo "Error: no input." >&2; exit 1; }
                    if [[ $port =~ ^[0-9]+$ ]] && (( port >= 1 && port <= 65535 )); then
                        break
                    fi
                    echo "Error: '$port' is not a valid port number." >&2
                done
                while true; do
                    echo "TLS mode: [s] STARTTLS   [l] SSL/TLS   [n] none" >&2
                    read -rp "(s/l/n): " tls || { echo "Error: no input." >&2; exit 1; }
                    case "$tls" in
                        [Ss]* ) tls=starttls; break ;;
                        [Ll]* ) tls=ssl; break ;;
                        [Nn]* ) tls=none; break ;;
                        * ) echo "Please answer 's', 'l', or 'n'." >&2 ;;
                    esac
                done
                break
                ;;
            * )
                echo "Please choose a number from 1 to 5." >&2
                ;;
        esac
    done
    printf '%s %s %s\n' "$host" "$port" "$tls"
}

clear
echo "--------- Welcome to the Server Setup ---------"
echo
echo
while true; do
    read -rsp "Enter your JWT secret (must be 64 characters): " jwt_secret
    echo
    if [[ ${#jwt_secret} -eq 64 ]]; then
        break
    else
        clear
        echo "--------- Welcome to the Server Setup ---------"
        echo
        echo
        echo "Error: Secret must be exactly 64 characters (you entered ${#jwt_secret})" >&2
        echo
    fi
done
echo
echo "Enter your database container name (WARNING: Overwrites existing container with same name):"
echo
read -rp "" db_container_name
echo
while true; do
    read -rsp "Enter your database password: " db_password
    echo
    read -rsp "Confirm your database password: " db_password_confirm
    echo
    if [[ "$db_password" == "$db_password_confirm" ]]; then
        break
    else
        clear
        echo "--------- Welcome to the Server Setup ---------"
        echo
        echo
        echo "Error: Passwords do not match, please try again." >&2
        echo
    fi
done
clear
echo
server_port=$(resolve_port "Spring server" 8005) || exit 1
db_port=$(resolve_port "MySQL container host" 3307) || exit 1

clear
echo "--------- SMTP / Mail Setup ---------"
echo
echo
read -r smtp_host smtp_port smtp_tls < <(prompt_smtp_provider)
echo
read -rp "Enter the sender email address: " smtp_sender
smtp_username=""
while [[ -z "$smtp_username" ]]; do
    read -rp "Enter the SMTP username (same as the sending address for default cases): " smtp_username
    smtp_username=${smtp_username:-$smtp_sender}
done
while true; do
    read -rsp "Enter the SMTP password: " smtp_password
    echo
    read -rsp "Confirm the SMTP password: " smtp_password_confirm
    echo
    if [[ "$smtp_password" == "$smtp_password_confirm" ]]; then
        break
    else
        echo "Error: Passwords do not match, please try again." >&2
        echo
    fi
done

case "$smtp_tls" in
    starttls ) smtp_tls_prop="spring.mail.properties.mail.smtp.starttls.enable=true" ;;
    ssl )      smtp_tls_prop="spring.mail.properties.mail.smtp.ssl.enable=true" ;;
    * )        smtp_tls_prop="" ;;
esac

clear
echo "--------- Public Base URL ---------"
echo
echo
echo "When a new user registers, the server emails them an account-verification"
echo "link. This setting is the scheme and host that link points to - i.e. the"
echo "address a browser on the user's machine uses to reach this server."
echo
echo "The verification path is appended automatically; enter only the protocol"
echo "and host (no trailing path). For example:"
echo
echo "    http://123.45.67.89"
echo "    https://listr.example.com"
echo
echo "If you are testing locally, you can use:"
echo
echo "    http://localhost:$server_port"
echo
while true; do
    read -rp "Public base URL: " verification_base_url \
        || { echo "Error: no input." >&2; exit 1; }
    verification_base_url=${verification_base_url%/}
    if [[ $verification_base_url =~ ^https?://[A-Za-z0-9.-]+(:[0-9]+)?$ ]]; then
        break
    fi
    echo "Error: enter something like 'http://host' or 'https://host:port'." >&2
    echo
done

clear
echo "--------- Admin Portal Setup ---------"
echo
echo
read -rp "Enter the admin portal email (doesn't have to be a real email): " admin_email
while true; do
    read -rsp "Enter the admin portal password: " admin_password
    echo
    read -rsp "Confirm the admin portal password: " admin_password_confirm
    echo
    if [[ "$admin_password" == "$admin_password_confirm" ]]; then
        break
    else
        echo "Error: Passwords do not match, please try again." >&2
        echo
    fi
done

clear
echo
echo "--------- Configuration Summary ---------"
printf '  %-26s %s\n' "Database container name:" "$db_container_name"
printf '  %-26s %s\n' "Spring server port:" "$server_port"
printf '  %-26s %s\n' "MySQL host port:" "$db_port"
printf '  %-26s %s\n' "SMTP host:" "$smtp_host"
printf '  %-26s %s\n' "SMTP port:" "$smtp_port"
printf '  %-26s %s\n' "SMTP username:" "$smtp_username"
printf '  %-26s %s\n' "Sender email:" "$smtp_sender"
printf '  %-26s %s\n' "Public base URL:" "$verification_base_url"
printf '  %-26s %s\n' "Admin portal email:" "$admin_email"
echo
echo "Note: All of the above (including passwords) is written in plain text to"
echo "      src/main/resources/application.properties and can be viewed or"
echo "      changed there later."
echo
read -rp "Are you sure you want to continue? (y/n): " confirm
case "$confirm" in
    [Yy]* ) echo "Proceeding...";;
    [Nn]* ) echo "Exiting..."; exit 1;;
    * ) echo "Invalid response";;
esac
echo
mkdir -p ./src/main/resources
echo "# Spring Specific
spring.application.name=springboot
server.port=$server_port
logging.level.root=INFO
logging.level.org.springframework.security.config.annotation.authentication.configuration.InitializeUserDetailsBeanManagerConfigurer=ERROR

# Database Configuration
spring.datasource.url=jdbc:mysql://localhost:$db_port/taskdb?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false&createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=$db_password

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
$smtp_tls_prop

# Connection timeouts to prevent threads from hanging permanently
spring.mail.properties[mail.smtp.connectiontimeout]=5000
spring.mail.properties[mail.smtp.timeout]=3000
spring.mail.properties[mail.smtp.writetimeout]=5000

# Admin portal credentials
admin.email=$admin_email
admin.password=$admin_password
" > ./src/main/resources/application.properties

# deploying MySQL container
echo "A MySQL Docker container is about to be deployed on port $db_port."
read -rp "Would you like to proceed? (y/n): " deploy_confirm
case "$deploy_confirm" in
[Yy]* )
    if docker ps -a --format '{{.Names}}' | grep -Fxq -- "$db_container_name"; then
        docker rm -f "$db_container_name"
  fi
  docker run -d -e MYSQL_ROOT_PASSWORD="$db_password" -e MYSQL_DATABASE=taskdb \
        --name "$db_container_name" -p "$db_port":3306 mysql:8.0
  ;;
esac

# testing deployment of the containers
clear
echo "--------- Springboot Setup ---------"
echo
echo
echo "Waiting for MySQL to accept connections (first boot can take 15-30s)..."
echo

for i in $(seq 1 60); do
    if ! docker ps --format '{{.Names}}' | grep -Fxq -- "$db_container_name"; then
        echo "Error: MySQL container exited during startup." >&2
        docker logs --tail 20 "$db_container_name" >&2
        exit 1
    fi
    if docker exec "$db_container_name" \
        mysqladmin ping -uroot -p"$db_password" --silent >/dev/null 2>&1; then
        echo "MySQL is ready."
        echo
        break
    fi
    if [[ $i -eq 60 ]]; then
        echo "Error: MySQL did not become ready within 120s." >&2
        docker logs --tail 20 "$db_container_name" >&2
        exit 1
    fi
    sleep 2
done

clear
echo
echo "--------- Setup Complete ---------"
echo "Spring server port: $server_port"
echo "MySQL host port:    $db_port"
echo "SMTP host:          $smtp_host:$smtp_port"
echo
echo "To start the server, run the following command:"
echo
echo "mvn spring-boot:run"
