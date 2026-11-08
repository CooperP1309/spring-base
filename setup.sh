#!/usr/bin/env bash

clear
cat <<'EOF'
 _____            _              ______
/  ___|          (_)             | ___ \
\ `--. _ __  _ __ _ _ __   __ _  | |_/ / __ _ ___  ___
 `--. \ '_ \| '__| | '_ \ / _` | | ___ \/ _` / __|/ _ \
/\__/ / |_) | |  | | | | | (_| | | |_/ / (_| \__ \  __/
\____/| .__/|_|  |_|_| |_|\__, | \____/ \__,_|___/\___|
      | |                  __/ |
      |_|                 |___/
EOF
echo
echo "(Setup)"
echo
echo "--------- Database Configuration ---------"
echo
echo
echo "How should the MySQL database be setup?"
echo
echo "  [1] Let Spring Boot's docker compose handle it  (quick setup)"
echo "  [2] I'll provide my own MySQL server (no Docker needed)"
echo
while true; do
    read -rp "(1/2): " mode_choice || { echo "Error: no input." >&2; exit 1; }
    case "$mode_choice" in
        1 ) use_docker=true; break;;
        2 ) use_docker=false; break;;
        * ) echo "Please answer '1' or '2'." >&2;;
    esac
done

echo
echo "Checking required services and tools..."

if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed or not in PATH (needed by ./mvnw)." >&2
    exit 1
fi

if [[ $use_docker == true ]]; then
    if ! command -v docker &> /dev/null; then
        echo "Error: Docker is not installed or not in PATH." >&2
        exit 1
    fi

    if docker info >/dev/null 2>&1; then
        echo "Docker daemon is running."
    else
        echo "Docker daemon is NOT running."
        echo "(Or the install requires sudo... Or it's broken)"
        exit 1
    fi

    if ! docker compose version >/dev/null 2>&1; then
        echo "Error: the Docker Compose plugin ('docker compose') is not available." >&2
        exit 1
    fi
fi

# Returns 0 (success) if the given TCP port is already taken, either by a
# listening socket on the host or (in Docker mode) by a running Docker
# container's published port.
port_in_use() {
    local port=$1
    if command -v ss >/dev/null 2>&1; then
        ss -Htln | awk '{print $4}' | grep -qE "[:.]${port}\$" && return 0
    elif command -v lsof >/dev/null 2>&1; then
        lsof -iTCP:"$port" -sTCP:LISTEN -Pn >/dev/null 2>&1 && return 0
    fi
    if [[ $use_docker == true ]]; then
        docker ps --format '{{.Ports}}' | grep -qE "(^|,| )[0-9.]*:${port}->" && return 0
    fi
    return 1
}

# Returns 0 (success) if a TCP connection to host:port succeeds within 3s.
tcp_reachable() {
    local probe='exec 3<>"/dev/tcp/$1/$2"'
    if command -v timeout >/dev/null 2>&1; then
        timeout 3 bash -c "$probe" _ "$1" "$2" 2>/dev/null
    else
        bash -c "$probe" _ "$1" "$2" 2>/dev/null
    fi
}

# Prompts for the host and port of a user-provided MySQL server and checks
# that something is accepting connections there. Sets db_host and db_port.
prompt_mysql_server() {
    while true; do
        while true; do
            read -rp "MySQL host [Default: localhost]: " db_host || { echo "Error: no input." >&2; exit 1; }
            db_host=${db_host:-localhost}
            [[ $db_host =~ ^[A-Za-z0-9.-]+$ ]] && break
            echo "Error: '$db_host' is not a valid hostname or IPv4 address." >&2
        done
        while true; do
            read -rp "MySQL port [Default: 3306]: " db_port || { echo "Error: no input." >&2; exit 1; }
            db_port=${db_port:-3306}
            if [[ $db_port =~ ^[0-9]+$ ]] && (( db_port >= 1 && db_port <= 65535 )); then
                break
            fi
            echo "Error: '$db_port' is not a valid port number." >&2
        done

        if tcp_reachable "$db_host" "$db_port"; then
            echo "MySQL server is reachable at $db_host:$db_port."
            return
        fi

        echo "Warning: nothing is accepting connections at $db_host:$db_port." >&2
        while true; do
            echo "[r] Re-enter the host and port" >&2
            echo "[k] Keep them anyway (e.g. the server isn't running yet)" >&2
            read -rp "(r/k): " choice || { echo "Error: no input." >&2; exit 1; }
            case "$choice" in
                [Rr]* ) echo; continue 2 ;;
                [Kk]* ) return ;;
                * ) echo "Please answer 'r' or 'k'." >&2 ;;
            esac
        done
    done
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
echo "--------- Spring Base Setup ---------"
echo
echo
while true; do
    read -rsp "Enter your JWT secret (must be 64 characters): " jwt_secret
    echo
    if [[ ${#jwt_secret} -eq 64 ]]; then
        break
    else
        clear
        echo "--------- Spring Base Setup ---------"
        echo
        echo
        echo "Error: Secret must be exactly 64 characters (you entered ${#jwt_secret})" >&2
        echo
    fi
done
echo
if [[ $use_docker == false ]]; then
    echo "Enter the connection details of your MySQL server."
    echo
    prompt_mysql_server
    read -rp "MySQL username [Default: root]: " db_username || { echo "Error: no input." >&2; exit 1; }
    db_username=${db_username:-root}
fi

db_password_prompt="Enter your database password"
reset_volume=false
if [[ $use_docker == true ]]; then
    # A running MySQL from an earlier setup would hold its host port (and its volume).
    running_ids=$(docker ps -q --filter "label=com.docker.compose.project=springboot")
    if [[ -n $running_ids ]]; then
        echo "The MySQL container from an earlier setup is still running."
        echo "Stop the Spring app first if it is running, then stop the container."
        read -rp "Stop the container now? (y/n): " stop_choice || { echo "Error: no input." >&2; exit 1; }
        case "$stop_choice" in
            [Yy]* ) docker stop $running_ids >/dev/null || exit 1 ;;
            * ) echo "Exiting..."; exit 1 ;;
        esac
        echo
    fi

    # MYSQL_ROOT_PASSWORD only takes effect when the volume is empty.
    if docker volume inspect springboot_mysql-data >/dev/null 2>&1; then
        echo "Existing MySQL data found (Docker volume springboot_mysql-data)."
        echo "MySQL only applies a new password to an empty volume. Would you like to:"
        while true; do
            echo "[k] Keep the data (enter the CURRENT database password next)"
            echo "[r] Reset: permanently delete ALL data, including all users, and set a new password"
            echo
            read -rp "(k/r): " volume_choice || { echo "Error: no input." >&2; exit 1; }
            case "$volume_choice" in
                [Kk]* )
                    db_password_prompt="Enter your CURRENT database password"
                    break
                    ;;
                [Rr]* )
                    read -rp "This permanently deletes all users. Are you sure? (y/n): " reset_confirm \
                        || { echo "Error: no input." >&2; exit 1; }
                    if [[ $reset_confirm == [Yy]* ]]; then
                        reset_volume=true
                        break
                    fi
                    echo
                    ;;
                * ) echo "Please answer 'k' or 'r'." >&2 ;;
            esac
        done
    fi
fi
echo
while true; do
    read -rsp "$db_password_prompt: " db_password
    echo
    # The mysql image's first-boot script puts MYSQL_ROOT_PASSWORD into SQL
    # unescaped: ' breaks initialization and \ would act as an SQL escape.
    if [[ $use_docker == true && ( $db_password == *\'* || $db_password == *\\* ) ]]; then
        echo "Error: in Docker mode the database password can't contain ' or \\" >&2
        echo "       (the MySQL image can't initialize with them). Please choose another." >&2
        echo
        continue
    fi
    read -rsp "Confirm your database password: " db_password_confirm
    echo
    if [[ "$db_password" == "$db_password_confirm" ]]; then
        break
    else
        clear
        echo "--------- Spring Base Setup ---------"
        echo
        echo
        echo "Error: Passwords do not match, please try again." >&2
        echo
    fi
done
clear
echo
server_port=$(resolve_port "Spring server" 8005) || exit 1
if [[ $use_docker == true ]]; then
    db_port=$(resolve_port "MySQL container host" 3307) || exit 1
fi

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
echo "    https://app.example.com"
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
echo "--------- Cookie Security ---------"
echo
echo
echo "The login session cookie can be marked 'Secure', which tells browsers to"
echo "only ever send it over HTTPS. This is recommended whenever the server is"
echo "reachable over HTTPS - either directly, or behind a reverse proxy that"
echo "terminates TLS in front of it."
echo
echo "Answer 'n' if this deployment is only reachable over plain HTTP - for"
echo "example local development, or a local/LAN server with no HTTPS in front"
echo "of it. Answering 'y' in that case will silently break login: the browser"
echo "will refuse to send the cookie back, since the connection isn't HTTPS."
echo
while true; do
    read -rp "Will users always reach this server over HTTPS? (y/n): " secure_cookie_choice
    case "$secure_cookie_choice" in
        [Yy]* ) jwt_cookie_secure=true; break;;
        [Nn]* ) jwt_cookie_secure=false; break;;
        * ) echo "Please answer 'y' or 'n'." >&2;;
    esac
done

clear
echo "--------- Captcha (Cloudflare Turnstile) ---------"
echo
echo
echo "Cloudflare Turnstile can protect the register, login and forgot-password"
echo "forms from bots. This is optional - if you skip it, the forms work exactly"
echo "as before and no requests are ever made to Cloudflare."
echo
echo "To enable it you need a free Cloudflare account and a Turnstile widget"
echo "(Cloudflare dashboard -> Turnstile -> Add widget). Add the hostname users"
echo "reach this server on to the widget, then copy its Site Key and Secret Key."
echo
echo "For local testing, Cloudflare's always-pass test keys can be used:"
echo
echo "    Site key:   1x00000000000000000000AA"
echo "    Secret key: 1x0000000000000000000000000000000AA"
echo
while true; do
    read -rp "Enable Cloudflare Turnstile? (y/N): " captcha_choice
    case "$captcha_choice" in
        [Yy]* ) turnstile_enabled=true; break;;
        [Nn]* | "" ) turnstile_enabled=false; break;;
        * ) echo "Please answer 'y' or 'n'." >&2;;
    esac
done
turnstile_site_key=""
turnstile_secret_key=""
if [[ $turnstile_enabled == true ]]; then
    echo
    while [[ -z "$turnstile_site_key" ]]; do
        read -rp "Enter the Turnstile site key: " turnstile_site_key \
            || { echo "Error: no input." >&2; exit 1; }
    done
    while true; do
        read -rsp "Enter the Turnstile secret key: " turnstile_secret_key
        echo
        read -rsp "Confirm the Turnstile secret key: " turnstile_secret_key_confirm
        echo
        if [[ -z "$turnstile_secret_key" ]]; then
            echo "Error: the secret key cannot be empty." >&2
        elif [[ "$turnstile_secret_key" == "$turnstile_secret_key_confirm" ]]; then
            break
        else
            echo "Error: Secret keys do not match, please try again." >&2
        fi
        echo
    done
fi

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
if [[ $use_docker == true ]]; then
    printf '  %-26s %s\n' "Database mode:" "Docker (Spring Boot Docker Compose)"
else
    printf '  %-26s %s\n' "Database mode:" "Self-managed MySQL"
fi
printf '  %-26s %s\n' "Spring server port:" "$server_port"
if [[ $use_docker == true ]]; then
    printf '  %-26s %s\n' "MySQL host port:" "$db_port"
    if [[ $reset_volume == true ]]; then
        printf '  %-26s %s\n' "Existing MySQL data:" "DELETE (reset)"
    elif docker volume inspect springboot_mysql-data >/dev/null 2>&1; then
        printf '  %-26s %s\n' "Existing MySQL data:" "keep"
    fi
else
    printf '  %-26s %s\n' "MySQL server:" "$db_host:$db_port"
    printf '  %-26s %s\n' "MySQL username:" "$db_username"
fi
printf '  %-26s %s\n' "SMTP host:" "$smtp_host"
printf '  %-26s %s\n' "SMTP port:" "$smtp_port"
printf '  %-26s %s\n' "SMTP username:" "$smtp_username"
printf '  %-26s %s\n' "Sender email:" "$smtp_sender"
printf '  %-26s %s\n' "Public base URL:" "$verification_base_url"
printf '  %-26s %s\n' "Secure cookie (HTTPS-only):" "$jwt_cookie_secure"
printf '  %-26s %s\n' "Turnstile captcha:" "$turnstile_enabled"
if [[ $turnstile_enabled == true ]]; then
    printf '  %-26s %s\n' "Turnstile site key:" "$turnstile_site_key"
fi
printf '  %-26s %s\n' "Admin portal email:" "$admin_email"
echo
echo "Note: All of the above (including passwords) is written in plain text to"
echo "      src/main/resources/application.properties and can be viewed or"
echo "      changed there later."
if [[ $use_docker == true ]]; then
    echo "      In Docker mode the database password and port are written to"
    echo "      compose.yaml instead, which is overwritten on every setup run."
fi
echo
read -rp "Are you sure you want to continue? (y/n): " confirm
case "$confirm" in
    [Yy]* ) echo "Proceeding...";;
    [Nn]* ) echo "Exiting..."; exit 1;;
    * ) echo "Invalid response"; exit 1;;
esac
echo

if [[ $reset_volume == true ]]; then
    # Runs before compose.yaml is regenerated, so 'down -v' uses the old file.
    echo "Deleting existing MySQL data..."
    if [[ -f ./compose.yaml ]]; then
        docker compose down -v
    fi
    # Fallback (no compose file, or one with a different project): containers
    # still reference the volume, so remove them first.
    if docker volume inspect springboot_mysql-data >/dev/null 2>&1; then
        old_ids=$(docker ps -aq --filter "label=com.docker.compose.project=springboot")
        [[ -n $old_ids ]] && docker rm -f $old_ids >/dev/null
        docker volume rm springboot_mysql-data >/dev/null \
            || { echo "Error: could not delete volume springboot_mysql-data." >&2; exit 1; }
    fi
    echo "MySQL data deleted."
    echo
fi

if [[ $use_docker == true ]]; then
    # Escape the password for compose.yaml: '$' -> '$$' (Compose interpolation),
    # then YAML single-quote it, doubling any embedded single quotes.
    yaml_password=${db_password//\$/\$\$}
    yaml_password="'${yaml_password//\'/\'\'}'"

    # compose.yaml and spring.docker.compose.enabled=true are always written
    # together: Compose support fails at startup without a compose file.
    cat > ./compose.yaml <<EOF
# compose.yaml - GENERATED by setup. Do not commit. Re-running setup overwrites it.
name: springboot   # fixed project name, so the volume is always springboot_mysql-data

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: $yaml_password
      MYSQL_DATABASE: taskdb
    ports:
      - "$db_port:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      # 127.0.0.1 matters: MySQL's first-boot init runs with networking off, so this can't pass early
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -uroot -p\"\$\$MYSQL_ROOT_PASSWORD\" --silent"]
      interval: 5s
      timeout: 5s
      retries: 24
      start_period: 30s
    labels:
      org.springframework.boot.jdbc.parameters: "serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false"

volumes:
  mysql-data:
EOF

    db_props="# Spring Boot Docker Compose support: Spring starts MySQL from compose.yaml
# and builds the datasource from it (user root, MYSQL_ROOT_PASSWORD,
# MYSQL_DATABASE and the mapped host port), so no spring.datasource.* here.
spring.docker.compose.enabled=true
# Also start MySQL for tests: the @SpringBootTest context needs a datasource.
spring.docker.compose.skip.in-tests=false"
else
    db_props="# Spring Boot Docker Compose support is off: MySQL is managed outside of this app.
spring.docker.compose.enabled=false
spring.datasource.url=jdbc:mysql://$db_host:$db_port/taskdb?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false&createDatabaseIfNotExist=true
spring.datasource.username=$db_username
spring.datasource.password=$db_password"
fi

mkdir -p ./src/main/resources
echo "# Spring Specific
spring.application.name=springboot
server.port=$server_port
logging.level.root=INFO
logging.level.org.springframework.security.config.annotation.authentication.configuration.InitializeUserDetailsBeanManagerConfigurer=ERROR

# Database Configuration
$db_props

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
$smtp_tls_prop

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
" > ./src/main/resources/application.properties

clear
echo
echo "--------- Setup Complete ---------"
if [[ $use_docker == true ]]; then
    echo "Database mode:      Docker (Spring Boot Docker Compose)"
    echo "Spring server port: $server_port"
    echo "MySQL host port:    $db_port"
else
    echo "Database mode:      Self-managed MySQL"
    echo "Spring server port: $server_port"
    echo "MySQL server:       $db_host:$db_port"
fi
echo "SMTP host:          $smtp_host:$smtp_port"
echo
if [[ $use_docker == false && -f compose.yaml ]]; then
    echo "Note: compose.yaml from an earlier Docker-mode setup was left in place."
    echo "      It is ignored while spring.docker.compose.enabled=false."
    echo
fi
if [[ $use_docker == true ]]; then
    echo "MySQL is defined in the generated compose.yaml. Spring Boot starts it"
    echo "on 'spring-boot:run' (Docker must be running) and stops it on shutdown."
    echo
fi
echo "To start the server, run the following command:"
echo
echo "./mvnw spring-boot:run"
echo
