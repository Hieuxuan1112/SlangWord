#!/usr/bin/env sh
# Generates a self-signed certificate for local HTTPS.
#
# For development only. A self-signed certificate proves the TLS plumbing works;
# it proves nothing about identity, which is the whole job of a real certificate.
# A deployment would use one issued by a CA the client already trusts — from
# Let's Encrypt, or whatever the ingress in front of this terminates with.
set -eu

# Git Bash on Windows rewrites anything that looks like a Unix path, turning
# `-subj /CN=localhost` into `C:/Program Files/Git/CN=localhost`. Both variables
# are ignored on Linux and macOS, so this stays a single portable script.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

CERT_DIR="$(dirname "$0")/../certs"
DAYS=365

mkdir -p "$CERT_DIR"

if [ -f "$CERT_DIR/server.crt" ]; then
  echo "certs/server.crt already exists; delete it to regenerate."
  exit 0
fi

# subjectAltName, not just CN: browsers have ignored the common name for years
# and will reject a certificate that does not list the host in its SAN.
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout "$CERT_DIR/server.key" \
  -out "$CERT_DIR/server.crt" \
  -days "$DAYS" \
  -subj "/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

chmod 600 "$CERT_DIR/server.key"

echo "Wrote certs/server.crt and certs/server.key (valid $DAYS days)."
echo "Start with: docker compose -f docker-compose.yml -f docker-compose.tls.yml up --build"
