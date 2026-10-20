#!/bin/zsh
# Cierra nodos y células de esta calculadora. No toca otros programas Java.
pkill -f "edu.computodistribuido.ads" 2>/dev/null || true
echo "Procesos ADS detenidos."
