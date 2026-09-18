#!/usr/bin/env bash
#
# setup-server.sh — Provision an Ubuntu/Debian VPS for the PureEats platform.
#
# Sets up: Apache, UFW firewall, a restricted deploy user for GitHub Actions,
# Let's Encrypt SSL for every subdomain, PostgreSQL, per-app web roots with
# placeholder pages, the Spring Boot backend (cloned, built, run as a systemd
# service and reverse-proxied at api.pureeats.in), and baseline hardening.
#
# Root SSH login and password authentication are left ENABLED on purpose —
# this script does not touch /etc/ssh/sshd_config at all.
#
# USAGE
#   sudo DB_PASSWORD='change-me' DEPLOY_SSH_KEY='ssh-ed25519 AAAA... github-actions' \
#        ./setup-server.sh
#
# Required env vars:
#   DB_PASSWORD       Password for the PostgreSQL app user (never hardcode this in the script).
# Recommended env vars:
#   DEPLOY_SSH_KEY    Public key (contents of an id_ed25519.pub) for the GitHub Actions deploy user.
#                      You can also add it later with: ssh-copy-id / manual authorized_keys edit.
#   ADMIN_EMAIL        Email used for Let's Encrypt certificate registration/renewal notices.
#   BACKEND_REPO_URL  Git URL for the Spring Boot backend (default: pureeats-backend-2026, public repo).
#   BACKEND_BRANCH    Branch to clone/build on first provision (default: matches deploy.yml's trigger).
#   BACKEND_PORT      Local port the backend listens on / gets proxied to (default: 8081).
#   SPLUNK_HEC_URL    Splunk HEC endpoint - if set (with SPLUNK_HEC_TOKEN), app logs are
#                     also forwarded there in addition to console/file. Blank = disabled.
#   SPLUNK_HEC_TOKEN  Splunk HEC token - a credential, never commit it.
#   SPLUNK_INDEX      Optional Splunk index name; blank uses the token's default index.
#
# Idempotency: most steps are safe to re-run. Certbot skips domains that already have a
# valid cert. apt/useradd/psql steps check for existing state before acting. The backend's
# /etc/pureeats/pureeats-api.env (JWT secret, super-admin password) is generated once and
# never overwritten by a re-run.
#
set -euo pipefail

# ============================================================================
# 0. CONFIG — adjust as needed
# ============================================================================
MAIN_DOMAIN="pureeats.in"
SUBDOMAINS=("admin" "store-owner" "driver" "api")   # -> admin.pureeats.in, etc.
DEPLOY_USER="pureeats-deploy"
WEB_ROOT="/var/www"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@${MAIN_DOMAIN}}"

DB_NAME="${DB_NAME:-pureeats}"
DB_USER="${DB_USER:-pureeats_app}"
# TEMPORARY: hardcoded fallback for local testing. Replace this and/or pass
# DB_PASSWORD as an env var instead — do not leave a real password committed here.
DB_PASSWORD="${DB_PASSWORD:-ChangeMe_TempPass123}"

DEPLOY_SSH_KEY="${DEPLOY_SSH_KEY:-ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCCruvqnDBkk2vyNrObShw54A8Bivw+KDSYiZbG1mikUY78074IgMzBxIxdEERE9ctVguOGdcSqfcpOnNUfsOdmcjqNx2/Bemi9FW4a3MIt1QPgyRTnWbPoZffLoWAzblC+FJRz4TEvL404mymh2liR1q+hhrtThwUvdSdIpN07RRmqhGlpdqdKR+uRdCYuIaXUGK2XoieqXmdTUpuGV2bFqinVzaq2KhoCZlV2lusPL0Lsv9p1krPh0ZUxHv1NDlF5eYOHNvzywfjgG/8wFNKXGkW8JJhxYtCKtZr4zBwbYob+qHc/pwf1Rn4KPWwsfg5lWx5GfgLrUBtyeFrz2oqv rsa-key-20260830}"   # public key content for GitHub Actions deploy user

# --- Spring Boot backend (api.pureeats.in) -----------------------------------
# Public repo, so the server pulls over plain HTTPS - no deploy key needed.
BACKEND_REPO_URL="${BACKEND_REPO_URL:-https://github.com/arpangroup/pureeats-backend-2026.git}"
# Just needs *a* buildable branch for the initial clone/build below; the actual
# CI deploy step (see deploy.yml) always fetches+resets to its own BRANCH, so
# this only matters the very first time the service starts.
BACKEND_BRANCH="${BACKEND_BRANCH:-feature/26.08.30-initial-dev-3}"
BACKEND_APP_DIR="/home/${DEPLOY_USER}/apps/pureeats-backend-2026"
BACKEND_PORT="${BACKEND_PORT:-8081}"   # must match SERVER_PORT read by pureeats-app/src/main/resources/application.yml

# Optional: forward app logs to Splunk HEC in addition to console/file (see
# logback-spring.xml's SplunkHecAppender). Left blank by default, in which
# case the appender self-disables — no action needed if you don't use Splunk.
SPLUNK_HEC_URL="${SPLUNK_HEC_URL:-}"       # e.g. https://splunk.example.com:8088/services/collector/event
SPLUNK_HEC_TOKEN="${SPLUNK_HEC_TOKEN:-}"   # HEC token - treat like a password, never commit it
SPLUNK_INDEX="${SPLUNK_INDEX:-}"           # optional; blank uses the token's default index

# All app hostnames, main domain first.
ALL_DOMAINS=("$MAIN_DOMAIN")
for s in "${SUBDOMAINS[@]}"; do
  ALL_DOMAINS+=("${s}.${MAIN_DOMAIN}")
done

log() { echo -e "\n\033[1;32m==> $*\033[0m"; }
warn() { echo -e "\033[1;33m!! $*\033[0m"; }

if [[ $EUID -ne 0 ]]; then
  echo "Run this script as root (sudo)." >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive

# ============================================================================
# 1. System update
# ============================================================================
log "1/17 Updating system packages"
apt-get update -y
apt-get upgrade -y

# ============================================================================
# 2. Base utilities, time sync
# ============================================================================
log "2/17 Installing base utilities and syncing time"
apt-get install -y curl wget gnupg2 lsb-release ca-certificates software-properties-common ufw fail2ban unattended-upgrades chrony
systemctl enable --now chrony

# ============================================================================
# 3. Apache
# ============================================================================
log "3/17 Installing Apache and required modules"
apt-get install -y apache2
a2enmod rewrite headers ssl proxy proxy_http
# Hide Apache version/OS info in responses and error pages.
sed -i 's/^ServerTokens .*/ServerTokens Prod/; s/^ServerSignature .*/ServerSignature Off/' /etc/apache2/conf-available/security.conf || true
a2enconf security
systemctl enable apache2
systemctl restart apache2

# ============================================================================
# 4. Firewall (UFW)
# ============================================================================
log "4/17 Configuring UFW firewall"
ufw allow OpenSSH
ufw allow 'Apache Full'   # opens 80 + 443
ufw --force enable
ufw status verbose

# ----------------------------------------------------------------------------
# Root SSH password login: actually enforce it, don't just assume it.
# Cloud images (DigitalOcean in particular, when a droplet is created with an
# SSH key attached) commonly drop a cloud-init snippet into
# /etc/ssh/sshd_config.d/*.conf that sets PasswordAuthentication no - which is
# read BEFORE the main sshd_config (first directive occurrence wins in sshd),
# so editing only /etc/ssh/sshd_config has no effect if such a file exists.
# Fix any existing directive wherever it's set; if neither the main config nor
# a drop-in sets it at all, append to the main config (OpenSSH's own default
# for PermitRootLogin is "prohibit-password", i.e. password login BLOCKED, so
# an explicit "yes" is required, not just leaving it unset).
# ----------------------------------------------------------------------------
log "Ensuring root SSH password login is actually enabled (not just assumed)"
SSHD_CONFIG_FILES=(/etc/ssh/sshd_config)
if compgen -G "/etc/ssh/sshd_config.d/*.conf" > /dev/null; then
  SSHD_CONFIG_FILES+=(/etc/ssh/sshd_config.d/*.conf)
fi
for f in "${SSHD_CONFIG_FILES[@]}"; do
  [[ -f "$f" ]] || continue
  sed -i 's/^\s*#*\s*PermitRootLogin.*/PermitRootLogin yes/' "$f"
  sed -i 's/^\s*#*\s*PasswordAuthentication.*/PasswordAuthentication yes/' "$f"
done
grep -qr "^PermitRootLogin" /etc/ssh/sshd_config /etc/ssh/sshd_config.d/*.conf 2>/dev/null \
  || echo "PermitRootLogin yes" >> /etc/ssh/sshd_config
grep -qr "^PasswordAuthentication" /etc/ssh/sshd_config /etc/ssh/sshd_config.d/*.conf 2>/dev/null \
  || echo "PasswordAuthentication yes" >> /etc/ssh/sshd_config
sshd -t
systemctl restart ssh

# ============================================================================
# 5. fail2ban (protects SSH from brute force)
# ============================================================================
log "5/17 Enabling fail2ban"
systemctl enable --now fail2ban

# ============================================================================
# 6. Unattended security upgrades
# ============================================================================
log "6/17 Enabling unattended security upgrades"
dpkg-reconfigure -f noninteractive unattended-upgrades || true
systemctl enable --now unattended-upgrades || true

# ============================================================================
# 7. Swap file (safety net for small VPS instances)
# ============================================================================
log "7/17 Ensuring a swap file exists"
if ! swapon --show | grep -q '/swapfile'; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
  log "Created 2G swap file"
else
  log "Swap file already present, skipping"
fi

# ============================================================================
# 8. Deployment user (for GitHub Actions CI/CD)
# ============================================================================
log "8/17 Creating deployment user: ${DEPLOY_USER}"
if ! id "$DEPLOY_USER" &>/dev/null; then
  useradd -m -s /bin/bash "$DEPLOY_USER"
  usermod -aG www-data "$DEPLOY_USER"
else
  log "User ${DEPLOY_USER} already exists, skipping creation"
fi

install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "/home/${DEPLOY_USER}/.ssh"
touch "/home/${DEPLOY_USER}/.ssh/authorized_keys"
chmod 600 "/home/${DEPLOY_USER}/.ssh/authorized_keys"
if [[ -n "$DEPLOY_SSH_KEY" ]] && ! grep -qF "$DEPLOY_SSH_KEY" "/home/${DEPLOY_USER}/.ssh/authorized_keys"; then
  echo "$DEPLOY_SSH_KEY" >> "/home/${DEPLOY_USER}/.ssh/authorized_keys"
  log "Added deploy SSH public key to authorized_keys"
else
  warn "No DEPLOY_SSH_KEY provided (or already present). Add the GitHub Actions public key manually to /home/${DEPLOY_USER}/.ssh/authorized_keys"
fi
chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "/home/${DEPLOY_USER}/.ssh"

# Lock password login for this account — SSH key only.
passwd -l "$DEPLOY_USER" >/dev/null

# Give the deploy user just enough sudo rights to reload Apache and manage
# the app's systemd services — NOT full root. Extend the command list as needed.
cat > /etc/sudoers.d/90-${DEPLOY_USER} <<EOF
${DEPLOY_USER} ALL=(root) NOPASSWD: /usr/sbin/apache2ctl reload, /usr/sbin/apache2ctl restart, /bin/systemctl reload apache2, /bin/systemctl restart apache2, /bin/systemctl restart pureeats-api
EOF
chmod 440 /etc/sudoers.d/90-${DEPLOY_USER}
visudo -cf /etc/sudoers.d/90-${DEPLOY_USER}

# ============================================================================
# 9. Web root folders + placeholder pages
# ============================================================================
log "9/17 Creating web root folders"

declare -A PLACEHOLDER_TEXT=(
  ["$MAIN_DOMAIN"]="PureEats Customer"
  ["admin.${MAIN_DOMAIN}"]="PureEats Admin"
  ["store-owner.${MAIN_DOMAIN}"]="PureEats Store Owner"
  ["driver.${MAIN_DOMAIN}"]="PureEats Driver"
)

for domain in "${ALL_DOMAINS[@]}"; do
  # api.* is reverse-proxied to the Spring Boot service (see step 10 below),
  # not served as static files - it has no placeholder/web root.
  [[ "$domain" == "api.${MAIN_DOMAIN}" ]] && continue
  docroot="${WEB_ROOT}/${domain}/html"
  install -d -m 755 "$docroot"
  cat > "${docroot}/index.html" <<HTML
<!DOCTYPE html>
<html lang="en">
<head><meta charset="utf-8"><title>${PLACEHOLDER_TEXT[$domain]}</title></head>
<body>
  <h1>${PLACEHOLDER_TEXT[$domain]}</h1>
</body>
</html>
HTML
  # Owned by the deploy user (so CI can write) but group-readable by www-data
  # (so Apache can serve), world-readable so any browser can fetch it.
  chown -R "${DEPLOY_USER}:www-data" "${WEB_ROOT}/${domain}"
  find "${WEB_ROOT}/${domain}" -type d -exec chmod 755 {} \;
  find "${WEB_ROOT}/${domain}" -type f -exec chmod 644 {} \;
done

# ============================================================================
# 10. Apache vhosts (HTTP first — certbot will add the HTTPS half)
# ============================================================================
log "10/17 Writing Apache virtual hosts"
for domain in "${ALL_DOMAINS[@]}"; do
  vhost_file="/etc/apache2/sites-available/${domain}.conf"
  if [[ "$domain" == "api.${MAIN_DOMAIN}" ]]; then
    # Reverse proxy to the Spring Boot service instead of serving static files.
    cat > "$vhost_file" <<APACHE
<VirtualHost *:80>
    ServerName ${domain}

    # Let certbot's ACME challenge hit Apache directly, not the proxied app.
    ProxyPass /.well-known/acme-challenge/ !
    ProxyPreserveHost On
    ProxyPass / http://127.0.0.1:${BACKEND_PORT}/
    ProxyPassReverse / http://127.0.0.1:${BACKEND_PORT}/

    ErrorLog \${APACHE_LOG_DIR}/${domain}-error.log
    CustomLog \${APACHE_LOG_DIR}/${domain}-access.log combined
</VirtualHost>
APACHE
  else
    docroot="${WEB_ROOT}/${domain}/html"
    cat > "$vhost_file" <<APACHE
<VirtualHost *:80>
    ServerName ${domain}
    DocumentRoot ${docroot}

    <Directory ${docroot}>
        Options -Indexes +FollowSymLinks
        AllowOverride All
        Require all granted
    </Directory>

    ErrorLog \${APACHE_LOG_DIR}/${domain}-error.log
    CustomLog \${APACHE_LOG_DIR}/${domain}-access.log combined
</VirtualHost>
APACHE
  fi
  a2ensite "${domain}.conf" >/dev/null

  # Certbot's Apache plugin stores the :443 vhost in a SEPARATE "-le-ssl.conf"
  # file, derived from whatever the :80 vhost looked like at the time the cert
  # was installed. Since we just rewrote the :80 vhost above (e.g. switching
  # api.* to a reverse proxy), drop any existing -le-ssl.conf so certbot below
  # regenerates it from the current template instead of leaving it stale —
  # this does NOT request a new certificate, it only reinstalls the existing one.
  ssl_vhost="${domain}-le-ssl"
  a2dissite "$ssl_vhost" >/dev/null 2>&1 || true
  rm -f "/etc/apache2/sites-available/${ssl_vhost}.conf"
done

a2dissite 000-default >/dev/null 2>&1 || true
apache2ctl configtest
systemctl reload apache2

# ============================================================================
# 11. SSL certificates (Let's Encrypt) — HTTPS for every route
# ============================================================================
log "11/17 Installing certbot and requesting SSL certificates"
apt-get install -y certbot python3-certbot-apache

warn "Certbot requires each domain's DNS A/AAAA record to already point at this server's public IP."
warn "If DNS isn't propagated yet, re-run this script (or the certbot command below) later."

CERTBOT_DOMAIN_ARGS=()
for domain in "${ALL_DOMAINS[@]}"; do
  CERTBOT_DOMAIN_ARGS+=("-d" "$domain")
done

certbot --apache --non-interactive --agree-tos -m "$ADMIN_EMAIL" --redirect "${CERTBOT_DOMAIN_ARGS[@]}" \
  || warn "certbot failed for one or more domains — check DNS and re-run: certbot --apache -d ${ALL_DOMAINS[*]}"

# Certbot installs its own renewal systemd timer; confirm it's active.
systemctl enable --now certbot.timer 2>/dev/null || true

# ============================================================================
# 12. PostgreSQL
# ============================================================================
log "12/17 Installing PostgreSQL"
apt-get install -y postgresql postgresql-contrib
systemctl enable --now postgresql

log "Creating PostgreSQL role and database"
sudo -u postgres psql -v ON_ERROR_STOP=1 <<SQL
DO \$\$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${DB_USER}') THEN
      CREATE ROLE ${DB_USER} LOGIN PASSWORD '${DB_PASSWORD}';
   ELSE
      ALTER ROLE ${DB_USER} WITH PASSWORD '${DB_PASSWORD}';
   END IF;
END
\$\$;

SELECT 'CREATE DATABASE ${DB_NAME} OWNER ${DB_USER}'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${DB_NAME}')\gexec

GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};
SQL

warn "PostgreSQL is listening on localhost only (default). Not opened in UFW — the app should connect locally or over a private network / SSH tunnel."

# ============================================================================
# 13. Java + Maven (runtime/build tools for the Spring Boot backend)
# ============================================================================
log "13/17 Installing Java 21 and Maven"
apt-get install -y openjdk-21-jdk-headless maven

# ============================================================================
# 14. Backend app (pureeats-backend-2026) — clone, build, systemd service
# ============================================================================
log "14/17 Setting up the Spring Boot backend service"

install -d -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$(dirname "$BACKEND_APP_DIR")"

if [[ ! -d "${BACKEND_APP_DIR}/.git" ]]; then
  sudo -u "$DEPLOY_USER" git clone --branch "$BACKEND_BRANCH" "$BACKEND_REPO_URL" "$BACKEND_APP_DIR"
else
  log "Backend repo already cloned at ${BACKEND_APP_DIR}, skipping clone"
fi

# Persistent user data lives outside the git checkout - keeps uploads safe from
# a redeploy ever needing to re-clone/rebuild the app directory, and matches
# the log directory below in being a normal /var path, not app-relative.
install -d -m 750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" /var/lib/pureeats/uploads
install -d -m 750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" /var/log/pureeats

# Secrets/DB config the app reads as env vars (see pureeats-app/src/main/resources/application.yml).
# Generated once, on first provision only — reruns must not rotate the JWT secret
# (would invalidate every live session) or the super-admin password.
# Group-readable (not world) so the deploy user can traverse to its OWN profile
# file below — the secrets file itself stays root-only via its own 600 mode.
install -d -m 750 -o root -g "$DEPLOY_USER" /etc/pureeats
if [[ ! -f /etc/pureeats/pureeats-api.env ]]; then
  BACKEND_JWT_SECRET="$(openssl rand -base64 48)"
  BACKEND_SUPER_ADMIN_PASSWORD="$(openssl rand -base64 18)"
  cat > /etc/pureeats/pureeats-api.env <<ENV
SERVER_PORT=${BACKEND_PORT}
DB_HOST=localhost
DB_PORT=5432
DB_NAME=${DB_NAME}
DB_USERNAME=${DB_USER}
DB_PASSWORD=${DB_PASSWORD}
JWT_SECRET=${BACKEND_JWT_SECRET}
SUPER_ADMIN_EMAIL=${ADMIN_EMAIL}
SUPER_ADMIN_PASSWORD=${BACKEND_SUPER_ADMIN_PASSWORD}
SPLUNK_HEC_URL=${SPLUNK_HEC_URL}
SPLUNK_HEC_TOKEN=${SPLUNK_HEC_TOKEN}
SPLUNK_INDEX=${SPLUNK_INDEX}
MEDIA_PUBLIC_BASE_URL=https://api.${MAIN_DOMAIN}
ENV
  chmod 600 /etc/pureeats/pureeats-api.env
  log "Generated /etc/pureeats/pureeats-api.env (JWT secret + super-admin password — see summary below)"
else
  log "/etc/pureeats/pureeats-api.env already exists, leaving it untouched"
fi

# Non-secret runtime settings (profile, log path, uploads path). Owned by the
# deploy user (not root) so deploy.yml can overwrite this file on every deploy
# with no sudo grant needed — that's how these get passed from CI as VM
# arguments (see ExecStart below) without giving CI write access to secrets.
if [[ ! -f /etc/pureeats/pureeats-api-runtime.env ]]; then
  cat > /etc/pureeats/pureeats-api-runtime.env <<RUNTIME
SPRING_PROFILES_ACTIVE=prod
LOG_FILE=/var/log/pureeats/pureeats-api.log
MEDIA_LOCAL_BASE_DIR=/var/lib/pureeats/uploads
RUNTIME
fi
chown "${DEPLOY_USER}:${DEPLOY_USER}" /etc/pureeats/pureeats-api-runtime.env
chmod 644 /etc/pureeats/pureeats-api-runtime.env

log "Building the backend (first build — can take a few minutes)"
sudo -u "$DEPLOY_USER" -H bash -lc "cd '${BACKEND_APP_DIR}' && mvn -q -pl pureeats-app -am package -DskipTests"

cat > /etc/systemd/system/pureeats-api.service <<SERVICE
[Unit]
Description=PureEats Spring Boot API
After=network.target postgresql.service

[Service]
Type=simple
User=${DEPLOY_USER}
WorkingDirectory=${BACKEND_APP_DIR}/pureeats-app
EnvironmentFile=-/etc/pureeats/pureeats-api-runtime.env
EnvironmentFile=-/etc/pureeats/pureeats-api.env
ExecStart=/bin/bash -c 'exec java -jar target/pureeats-app-*.jar --spring.profiles.active=\${SPRING_PROFILES_ACTIVE:-prod} --logging.file.name=\${LOG_FILE:-/var/log/pureeats/pureeats-api.log} --pureeats.media.local.base-dir=\${MEDIA_LOCAL_BASE_DIR:-/var/lib/pureeats/uploads}'
SuccessExitStatus=143
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
SERVICE

systemctl daemon-reload
systemctl enable pureeats-api
systemctl restart pureeats-api

# ============================================================================
# 15. Log rotation sanity check (Apache/Postgres ship their own logrotate configs)
# ============================================================================
log "15/17 Verifying logrotate is installed"
apt-get install -y logrotate

# ============================================================================
# 16. Ownership recap + final permission pass
# ============================================================================
log "16/17 Final permissions pass"
for domain in "${ALL_DOMAINS[@]}"; do
  [[ "$domain" == "api.${MAIN_DOMAIN}" ]] && continue
  chown -R "${DEPLOY_USER}:www-data" "${WEB_ROOT}/${domain}"
done

# ============================================================================
# 17. Summary
# ============================================================================
log "17/17 Done"
cat <<SUMMARY

============================================================
 Setup complete
============================================================
 Apache vhosts created for:
$(for d in "${ALL_DOMAINS[@]}"; do echo "   - https://${d}"; done)

 Deploy user:      ${DEPLOY_USER} (password login disabled, SSH key only, scoped sudo)
 Deploy web roots: ${WEB_ROOT}/<domain>/html  (owner ${DEPLOY_USER}:www-data)
 Deploy sudo:      apache2 reload/restart + pureeats-api restart only (see /etc/sudoers.d/90-${DEPLOY_USER})

 Root SSH login and password authentication were left UNCHANGED (still enabled)
 per your request — this script does not modify /etc/ssh/sshd_config.

 PostgreSQL DB:    ${DB_NAME}
 PostgreSQL user:  ${DB_USER}  (password: value of \$DB_PASSWORD you supplied)

 Backend service:  pureeats-api.service, proxied at https://api.${MAIN_DOMAIN}
 Backend checkout: ${BACKEND_APP_DIR}  (owner ${DEPLOY_USER}, branch ${BACKEND_BRANCH})
 Backend env file: /etc/pureeats/pureeats-api.env (root-only, 600 — JWT secret
                    and super-admin password were freshly generated here if this
                    is the first run; re-running this script will NOT overwrite it)
 Backend logs:     journalctl -u pureeats-api -f

 NEXT STEPS (things this script cannot do for you):
   1. Point DNS A records for all domains above at this server's public IP,
      then re-run the certbot step if it failed due to DNS not being ready:
        certbot --apache -d ${ALL_DOMAINS[*]}
   2. Add ${DEPLOY_USER}'s private key as a GitHub Actions secret (e.g. DEPLOY_SSH_KEY)
      and use it in your workflow's ssh-agent/scp/rsync deploy step.
   3. Replace the placeholder index.html files with real app builds during deploy.
   4. Read /etc/pureeats/pureeats-api.env at least once (cat it as root) and note
      the generated SUPER_ADMIN_PASSWORD somewhere safe — it's only ever shown
      once, right here, the first time this script provisions the server.
   5. Consider a managed backup strategy for PostgreSQL (pg_dump cron + offsite copy).
   6. Root login + password SSH auth are still ON — if you ever want that locked
      down later, that's a deliberate follow-up, not something this script does.
============================================================
SUMMARY
