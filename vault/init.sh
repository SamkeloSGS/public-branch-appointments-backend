#!/bin/sh
# Local only. Seeds the secrets into the dev-mode Vault, then renders them as files for the apps that cannot read Vault:
#   .secrets/keycloak/keycloak.env    sourced by the Keycloak container before it starts
#   .secrets/monitoring/              read by postgres-exporter (DATA_SOURCE_USER_FILE, DATA_SOURCE_PASS_FILE)
# The backend reads appointments/backend and appointments/shared from Vault itself (Spring Cloud Vault).
# Runs on every `docker compose up`: a dev-mode Vault keeps nothing across restarts.
# Staging and production use a real Vault (server mode, AppRole) and their own values; nothing here applies there.
set -eu

out=/out

# --- Seed (local development values only) ---------------------------------------------------------------------------
# Each value can be replaced by the environment variable named beside it (the Fullstack compose passes them from
# its .env); the defaults are the local ones.

# Shared by Keycloak (the client's secret) and the backend (its login to Keycloak's admin API): one source of truth
vault kv put -mount=secret appointments/shared \
  backend-client-secret="${BACKEND_CLIENT_SECRET:-local-dev-backend-secret}"

vault kv put -mount=secret appointments/keycloak \
  admin-username="${KEYCLOAK_ADMIN_USERNAME:-admin}" \
  admin-password="${KEYCLOAK_ADMIN_PASSWORD:-admin}" \
  smtp-user= \
  smtp-password=

# Every secret of the backend; it reads them from here at start-up and has no defaults. The database login is
# kept together: its username and password change together.
vault kv put -mount=secret appointments/backend \
  database-username="${DATABASE_USERNAME:-appointments}" \
  database-password="${DATABASE_PASSWORD:-appointments}" \
  redis-password= \
  smtp-username= \
  smtp-password= \
  webhook-secret-local="${WEBHOOK_SECRET_LOCAL:-local-dev-webhook-secret}"

# --- Render ----------------------------------------------------------------------------------------------------------

get() { vault kv get -mount=secret -field="$2" "appointments/$1"; }

# Single-quoted shell values; a single quote inside a value is written as '\''
quote() { printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"; }

mkdir -p "$out/keycloak" "$out/monitoring"

cat > "$out/keycloak/keycloak.env" <<EOF
export KC_BOOTSTRAP_ADMIN_USERNAME=$(quote "$(get keycloak admin-username)")
export KC_BOOTSTRAP_ADMIN_PASSWORD=$(quote "$(get keycloak admin-password)")
export KC_SMTP_USER=$(quote "$(get keycloak smtp-user)")
export KC_SMTP_PASSWORD=$(quote "$(get keycloak smtp-password)")
export KC_BACKEND_CLIENT_SECRET=$(quote "$(get shared backend-client-secret)")
EOF

# postgres-exporter signs in as the application's database user (local only; production uses a read-only monitor role)
printf '%s' "$(get backend database-username)" > "$out/monitoring/postgres-username"
printf '%s' "$(get backend database-password)" > "$out/monitoring/postgres-password"

echo "Vault seeded; secrets rendered for keycloak and monitoring"
