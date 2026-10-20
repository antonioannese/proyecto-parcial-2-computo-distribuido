#!/bin/zsh
set -euo pipefail
source "$(dirname "$0")/entorno.sh"
cd "$ROOT"

ID="${1:-nodo-extra}"
PUERTO="${2:-7410}"
PARES="${3:-n1=127.0.0.1:7401,n2=127.0.0.1:7402,n3=127.0.0.1:7403,n4=127.0.0.1:7404}"

"$JAVA" -Xms32m -Xmx128m -cp "$ROOT/out" edu.computodistribuido.ads.nodo.NodoApp \
  --id "$ID" --puerto "$PUERTO" --pares "$PARES"
