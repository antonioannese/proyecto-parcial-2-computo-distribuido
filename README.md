# Calculadora distribuida con arquitectura ADS

Antonio Annese Caballero · ID 0266972  
Cómputo Distribuido · Proyecto del segundo parcial

La calculadora del primer parcial (cliente, middleware y servidores) queda sustituida por un Campo de Datos de nodos en malla. Las células no se llaman entre sí: publican un mensaje con Código de Contenido y cada célula decide si le corresponde.

## Qué hay que tener abierto en la demostración

4 nodos, 4 clientes y 16 servidores (suma, resta, multiplicación y división en cada nodo). En total, 24 ventanas.

```bash
chmod +x scripts/*.sh
./scripts/demo.sh
```

Cuando un nodo diga **Malla completa · 3/3 pares**, ya se puede calcular. Cada columna de la pantalla es un nodo: arriba el nodo, en medio el cliente y abajo las cuatro células servidor.

Para apagar todo:

```bash
./scripts/detener.sh
```

## Comprobar la malla sin abrir ventanas

```bash
./scripts/probar.sh
```

Esa prueba levanta cuatro nodos, hace suma, resta, multiplicación y división entre cero, comprueba que solo una célula responde y que un mensaje repetido no vuelve a circular.

## Arrancar una instancia suelta

El mismo programa sirve para cualquier cantidad de nodos o células. Los identificadores y los puertos entran por argumentos; no están fijos en el código.

```bash
./scripts/compilar.sh
./scripts/iniciar-nodo.sh n5 7410 n1=127.0.0.1:7401,n2=127.0.0.1:7402
./scripts/iniciar-cliente.sh gui-5 127.0.0.1:7410
./scripts/iniciar-servidor.sh suma-5 SUMA 127.0.0.1:7410
```

Servicios válidos: `SUMA`, `RESTA`, `MULTIPLICACION`, `DIVISION`.

## Programas

| Programa | Clase |
|---|---|
| Nodo del Campo de Datos | `edu.computodistribuido.ads.nodo.NodoApp` |
| Célula cliente (GUI) | `edu.computodistribuido.ads.cliente.ClienteApp` |
| Célula servidor | `edu.computodistribuido.ads.servidor.ServidorApp` |

El diseño, los códigos de contenido y la forma de evitar ciclos y respuestas duplicadas están en `Reporte_Tecnico.pdf`. El guion para grabar está en `Script_Presentacion.md`.

Repositorio: https://github.com/antonioannese/proyecto-parcial-2-computo-distribuido

Video de la demostración: https://drive.google.com/file/d/1B20m8tzX4MjSiu5_uAyjQYxr72L59dam/view?usp=sharing
