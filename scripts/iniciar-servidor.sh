#!/bin/zsh
set -euo pipefail
source "$(dirname "$0")/entorno.sh"
cd "$ROOT"

ID="${1:-suma-extra}"
SERVICIO="${2:-SUMA}"
NODO="${3:-127.0.0.1:7401}"

"$JAVA" -Xms32m -Xmx128m -cp "$ROOT/out" edu.computodistribuido.ads.servidor.ServidorApp \
  --id "$ID" --servicio "$SERVICIO" --nodo "$NODO"
