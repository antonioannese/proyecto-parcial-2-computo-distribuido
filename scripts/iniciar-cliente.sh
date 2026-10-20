#!/bin/zsh
set -euo pipefail
source "$(dirname "$0")/entorno.sh"
cd "$ROOT"

ID="${1:-gui-extra}"
NODO="${2:-127.0.0.1:7401}"

"$JAVA" -Xms32m -Xmx128m -cp "$ROOT/out" edu.computodistribuido.ads.cliente.ClienteApp \
  --id "$ID" --nodo "$NODO"
