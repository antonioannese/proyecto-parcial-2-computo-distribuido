#!/bin/zsh
set -euo pipefail
source "$(dirname "$0")/entorno.sh"
cd "$ROOT"

mkdir -p "$ROOT/out"
find src -name "*.java" | sort > "$ROOT/out/fuentes.txt"
"$JAVAC" -encoding UTF-8 --release 11 -d "$ROOT/out" @"$ROOT/out/fuentes.txt"

echo "Compilación correcta."
echo "Clases en: $ROOT/out"
echo
echo "Demostración (4 nodos y 20 células):  scripts/demo.sh"
echo "Prueba de la malla, sin ventanas:     scripts/probar.sh"
