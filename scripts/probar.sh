#!/bin/zsh
set -euo pipefail
source "$(dirname "$0")/entorno.sh"
cd "$ROOT"
"$ROOT/scripts/compilar.sh" >/dev/null
"$JAVA" -cp "$ROOT/out" edu.computodistribuido.ads.prueba.PruebaMalla
