#!/bin/zsh
# Abre la configuración obligatoria: 4 nodos en malla y 20 células.
set -euo pipefail
source "$(dirname "$0")/entorno.sh"
cd "$ROOT"
setopt NO_HUP

"$ROOT/scripts/detener.sh" >/dev/null || true
for _ in {1..20}; do
  ocupado=0
  for puerto in 7401 7402 7403 7404; do
    if lsof -nP -iTCP:"$puerto" -sTCP:LISTEN >/dev/null 2>&1; then
      ocupado=1
    fi
  done
  if [[ "$ocupado" -eq 0 ]]; then
    break
  fi
  sleep 0.25
done

"$ROOT/scripts/compilar.sh"

echo "Calculando la posición de las ventanas..."
GEOMETRIA="$(python3 - <<'PY'
import subprocess
raw = subprocess.check_output(
    ["osascript", "-e", 'tell application "Finder" to get bounds of window of desktop'],
    text=True,
)
nums = [int(p.strip()) for p in raw.split(",")]
x0, y0, x1, y1 = nums
ancho = max(1100, x1 - x0)
alto = max(700, y1 - y0)
margen = 8
arriba = y0 + 28
abajo = y1 - 56
hueco = 6
columnas = 4
cw = (ancho - margen * 2 - hueco * (columnas - 1)) // columnas
nh = 176
gh = 400
sy = arriba + nh + hueco + gh + hueco
libre = max(150, abajo - sy)
cascade = 32 if libre >= 250 else 26
sh = min(172, max(140, libre - cascade * 3))
servicios = ("suma", "resta", "multi", "div")
for col in range(columnas):
    x = margen + col * (cw + hueco)
    print(f"nodo {x} {arriba} {cw} {nh}")
    print(f"gui {x} {arriba + nh + hueco} {cw} {gh}")
    for i, nombre in enumerate(servicios):
        print(f"{nombre} {x + i * 10} {sy + i * cascade} {max(240, cw - 24)} {sh}")
PY
)"

tipos=()
xs=()
ys=()
anchos=()
altos=()
while read -r tipo x y w h; do
  tipos+=("$tipo")
  xs+=("$x")
  ys+=("$y")
  anchos+=("$w")
  altos+=("$h")
done <<< "$GEOMETRIA"

lanzar() {
  "$JAVA" -Xms24m -Xmx96m -cp "$ROOT/out" "$@" &
}

pares_de() {
  local yo="$1"
  local salida=""
  local i puerto
  for i in 1 2 3 4; do
    if [[ "n$i" == "$yo" ]]; then
      continue
    fi
    puerto=$((7400 + i))
    if [[ -n "$salida" ]]; then
      salida="${salida},"
    fi
    salida="${salida}n${i}=127.0.0.1:${puerto}"
  done
  echo "$salida"
}

# La geometría viene en grupos de 6, índices de zsh desde 1:
# nodo, cliente, suma, resta, multiplicación, división.
echo "Abriendo 4 nodos..."
for i in 1 2 3 4; do
  n=$(( (i - 1) * 6 + 1 ))
  lanzar edu.computodistribuido.ads.nodo.NodoApp \
    --id "n$i" --puerto "$((7400 + i))" --pares "$(pares_de "n$i")" \
    --x "${xs[$n]}" --y "${ys[$n]}" --ancho "${anchos[$n]}" --alto "${altos[$n]}"
done

sleep 1.6
echo "Abriendo 4 clientes y 16 servidores..."

for i in 1 2 3 4; do
  base=$(( (i - 1) * 6 ))
  nodo="127.0.0.1:$((7400 + i))"

  g=$((base + 2))
  lanzar edu.computodistribuido.ads.cliente.ClienteApp \
    --id "gui-$i" --nodo "$nodo" \
    --x "${xs[$g]}" --y "${ys[$g]}" --ancho "${anchos[$g]}" --alto "${altos[$g]}"

  s=$((base + 3))
  lanzar edu.computodistribuido.ads.servidor.ServidorApp \
    --id "suma-$i" --servicio SUMA --nodo "$nodo" \
    --x "${xs[$s]}" --y "${ys[$s]}" --ancho "${anchos[$s]}" --alto "${altos[$s]}"

  r=$((base + 4))
  lanzar edu.computodistribuido.ads.servidor.ServidorApp \
    --id "resta-$i" --servicio RESTA --nodo "$nodo" \
    --x "${xs[$r]}" --y "${ys[$r]}" --ancho "${anchos[$r]}" --alto "${altos[$r]}"

  m=$((base + 5))
  lanzar edu.computodistribuido.ads.servidor.ServidorApp \
    --id "multi-$i" --servicio MULTIPLICACION --nodo "$nodo" \
    --x "${xs[$m]}" --y "${ys[$m]}" --ancho "${anchos[$m]}" --alto "${altos[$m]}"

  d=$((base + 6))
  lanzar edu.computodistribuido.ads.servidor.ServidorApp \
    --id "div-$i" --servicio DIVISION --nodo "$nodo" \
    --x "${xs[$d]}" --y "${ys[$d]}" --ancho "${anchos[$d]}" --alto "${altos[$d]}"
done

echo
echo "Listo. Cuatro columnas, una por nodo."
echo "Arriba el nodo (contador rojo y ciclos evitados), luego el cliente, y abajo las células:"
echo "  YO SÉ SUMAR, YO SÉ RESTAR, YO SÉ MULTIPLICAR, YO SÉ DIVIDIR."
echo "Cuando el nodo diga «Malla completa · 3/3 pares», ya se puede operar."
echo "Cierre esta terminal o ejecute scripts/detener.sh para apagar todo."
echo

wait
