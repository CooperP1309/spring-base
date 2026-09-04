#!/usr/bin/env bash

# Boots the local development stack:
#   1. ensures the Docker daemon is running (starting it if possible)
#   2. ensures the MySQL container from application.properties is running
#   3. waits until MySQL actually accepts connections
#   4. hands off to ./mvnw spring-boot:run
#
# All database details are read from src/main/resources/application.properties,
# which is written by ./setup_server.sh.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

PROPS="$SCRIPT_DIR/src/main/resources/application.properties"

clear
echo
echo "--------- Starting Spring Server ---------"
echo

# --- prerequisites ---------------------------------------------------------

if ! command -v docker &> /dev/null; then
    echo "Error: Docker is not installed or not in PATH." >&2
    exit 1
fi

if [[ ! -x "$SCRIPT_DIR/mvnw" ]]; then
    echo "Error: ./mvnw not found or not executable in $SCRIPT_DIR." >&2
    exit 1
fi

if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed or not in PATH (needed by ./mvnw)." >&2
    exit 1
fi

if [[ ! -f "$PROPS" ]]; then
    echo "Error: $PROPS not found. Run ./setup_server.sh first." >&2
    exit 1
fi

# --- read a single key=value from application.properties ------------------
# Exact key match, ignores surrounding whitespace, tolerates CRLF endings,
# returns the first match only.
prop() {
    local key=$1 line
    while IFS= read -r line || [[ -n $line ]]; do
        line=${line%$'\r'}
        line=${line#"${line%%[![:space:]]*}"}
        case $line in
            "$key"=*) printf '%s\n' "${line#*=}"; return 0 ;;
        esac
    done < "$PROPS"
    return 1
}

db_container=$(prop 'db.container.name') || {
    echo "Error: 'db.container.name' is not set in application.properties." >&2
    echo "       Re-run ./setup_server.sh (or add the line manually)." >&2
    exit 1
}
db_password=$(prop 'spring.datasource.password') || {
    echo "Error: 'spring.datasource.password' is not set in application.properties." >&2
    exit 1
}
db_url=$(prop 'spring.datasource.url')
db_hostport=${db_url#*://}
db_hostport=${db_hostport%%/*}
db_port=${db_hostport##*:}

# --- ensure the Docker daemon is running ---------------------------------

wait_for_docker() {
    local i
    for ((i = 0; i < 30; i++)); do
        docker info >/dev/null 2>&1 && return 0
        printf '.'
        sleep 1
    done
    return 1
}

if docker info >/dev/null 2>&1; then
    echo "Docker daemon is running."
else
    echo "Docker daemon is not running - attempting to start it..."

    # Docker Desktop (Linux) uses a per-user context and its own CLI.
    if docker context inspect 2>/dev/null | grep -q '"desktop' \
        && docker desktop status >/dev/null 2>&1; then
        docker desktop start >/dev/null 2>&1 || true
    fi

    # systemd-managed daemon.
    if ! docker info >/dev/null 2>&1 && command -v systemctl >/dev/null 2>&1 \
        && systemctl list-unit-files 2>/dev/null | grep -q '^docker\.service'; then
        sudo systemctl start docker || true
    fi

    # SysV init fallback.
    if ! docker info >/dev/null 2>&1 && command -v service >/dev/null 2>&1; then
        sudo service docker start || true
    fi

    printf 'Waiting for the Docker daemon'
    if wait_for_docker; then
        echo
        echo "Docker daemon is running."
    else
        echo
        echo "Error: could not start the Docker daemon. Start Docker and retry." >&2
        exit 1
    fi
fi

# --- ensure the MySQL container is running ------------------------------

if docker ps --format '{{.Names}}' | grep -Fxq -- "$db_container"; then
    echo "MySQL container '$db_container' is already running."
elif docker ps -a --format '{{.Names}}' | grep -Fxq -- "$db_container"; then
    echo "Starting existing MySQL container '$db_container'..."
    if ! docker start "$db_container" >/dev/null; then
        echo "Error: failed to start container '$db_container'." >&2
        exit 1
    fi
else
    echo "Error: MySQL container '$db_container' does not exist." >&2
    echo "       Run ./setup_server.sh to create it." >&2
    exit 1
fi

# --- wait for MySQL to accept connections -------------------------------

echo
echo "Waiting for MySQL to accept connections (first boot can take 15-30s)..."
for ((i = 1; i <= 60; i++)); do
    if ! docker ps --format '{{.Names}}' | grep -Fxq -- "$db_container"; then
        echo "Error: MySQL container '$db_container' exited during startup." >&2
        docker logs --tail 20 "$db_container" >&2
        exit 1
    fi
    if docker exec "$db_container" \
        mysqladmin ping -uroot -p"$db_password" --silent >/dev/null 2>&1; then
        echo "MySQL is ready."
        break
    fi
    if [[ $i -eq 60 ]]; then
        echo "Error: MySQL did not become ready within 120s." >&2
        docker logs --tail 20 "$db_container" >&2
        exit 1
    fi
    sleep 2
done

# --- start the Spring server -------------------------------------------

echo
echo "--------- Launching Spring Boot ---------"
echo "Container:  $db_container"
[[ -n $db_port ]] && echo "MySQL port: $db_port"
echo
exec ./mvnw spring-boot:run
