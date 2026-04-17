#!/bin/sh
set -e
export VITE_AUTH_API_URL="${VITE_AUTH_API_URL:-http://localhost:8080}"
export VITE_MAINTENANCE_API_URL="${VITE_MAINTENANCE_API_URL:-http://localhost:8081}"
envsubst '${VITE_AUTH_API_URL} ${VITE_MAINTENANCE_API_URL}' \
  < /etc/nginx/templates/env-config.js.template \
  > /usr/share/nginx/html/env-config.js
exec nginx -g 'daemon off;'
