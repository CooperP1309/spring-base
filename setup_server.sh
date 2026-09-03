#!/usr/bin/env bash

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
echo
echo "--------- Configuration Summary ---------"
printf '  %-26s %s\n' "Database container name:" "$db_container_name"
printf '  %-26s %s\n' "Spring server port:" "$server_port"
printf '  %-26s %s\n' "MySQL host port:" "$db_port"
echo
read -rp "Are you sure you want to continue? (y/n): " confirm
case "$confirm" in
    [Yy]* ) echo "Proceeding...";;
    [Nn]* ) echo "Exiting..."; exit 1;;
    * ) echo "Invalid response";;
esac
echo
mkdir -p ./src/main/resources
echo "spring.application.name=springboot

server.port=$server_port

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
echo "Validating MySQL deployment..."

sleep 5

if docker ps --format '{{.Names}}' | grep -Fxq -- "$db_container_name"; then
    echo "MySQL container is running."
    echo
else
    echo "Error: MySQL container is not running."
    exit 1
fi

clear
echo
echo "--------- Setup Complete ---------"
echo "Spring server port: $server_port"
echo "MySQL host port:    $db_port"
echo
echo "To start the server, run the following command:"
echo
echo "mvn spring-boot:run"
