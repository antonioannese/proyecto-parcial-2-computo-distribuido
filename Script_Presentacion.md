# Guion para el video

Antonio Annese Caballero, ID 0266972. Cómputo Distribuido, segundo parcial.

Duración orientativa: 6 a 8 minutos. Habla mirando las ventanas que se mencionan. No hace falta leerlo palabra por palabra; sí conviene cubrir cada punto.

## 1. Qué es el sistema

"Este es el proyecto del segundo parcial. La calculadora del primer parcial era de tres capas: cliente, middleware y servidores. Aquí esa arquitectura se sustituye por ADS. Hay un Campo de Datos de cuatro nodos en malla completa, y las células se comunican por broadcast usando Códigos de Contenido. No hay un nodo maestro."

Señala la fila de arriba: NODO n1, n2, n3 y n4. En cada uno debe decir **Malla completa · 3/3 pares**.

## 2. Quién está conectado

"Cada nodo tiene cinco células: un cliente y un servidor de suma, resta, multiplicación y división. Son veinte células. El título de cada servidor dice lo que sabe hacer: yo sé sumar, yo sé restar, yo sé multiplicar, yo sé dividir. La huella es el identificador de esa instancia."

Abre, si hace falta, una ventana verde de multiplicar y una rosa de sumar para que se vea el código `MUL_REQ` o `SUM_REQ`.

## 3. Una suma, y por qué no responden las cuatro

"En el cliente del nodo 1 pongo 9 y 4 y oprimo suma. El cliente publica `SUM_REQ`. El nodo lo difunde a la malla y a sus células. Las cuatro células de suma reciben el mismo mensaje, porque el nodo no elige destinatario: solo mira que el mensaje sea nuevo."

"Cada una se postula con su huella. Después de una ventana corta, solo la huella menor envía `SUM_RES`. Las otras tres escriben que cedieron la solicitud. El cliente muestra 13 y el nombre de la célula que contestó. Si llegara otra respuesta con el mismo identificador, el cliente la descarta."

Deja visible la bitácora del nodo: líneas `BROADCAST` y `CICLO EVITADO`. El número ámbar de **Ciclos evitados** tiene que ser mayor que cero. Ese es el corte de los duplicados que la malla genera al estar todos conectados con todos.

## 4. Las otras operaciones, desde otro cliente

"Ahora, desde el cliente del nodo 3: resta, multiplicación y división. Una multiplicación trae `MUL_REQ`. Las células de suma lo ven y lo ignoran, porque no es su código. Solo responde una célula de multiplicar."

Haz estas cuatro, despacio, y deja que se lea el resultado antes de la siguiente:

- 9 − 4 = 5. En la bitácora del cliente debe verse `SUB_RES` asociado a esa solicitud.
- 8 × 7 = 56. Señala `MUL_REQ` al enviar y `MUL_RES` al llegar el 56.
- 8 ÷ 2 = 4. Esta es la división correcta: el cliente muestra 4 y la bitácora dice `DIV_RES`.
- 9 ÷ 0. No debe cerrarse el programa: la respuesta llega con error de división entre cero.

## 5. No hay una célula indispensable

"Detengo tres servidores de suma y dejo uno. Vuelvo a sumar. La que sigue conectada responde, porque es la única huella que se postuló. El servicio no depende de una instancia concreta."

Cierra tres ventanas **YO SÉ SUMAR** y suma 20 + 3 en cualquier cliente. Debe dar 23.

## 6. No hay un nodo indispensable

"Detengo el nodo 4. Su cliente y sus servidores pierden el enlace. Los otros tres nodos siguen en malla y sus clientes siguen calculando. Si más adelante vuelvo a levantar un nodo con el mismo programa y otros puertos, entra a la malla sin cambiar el código."

Oprime **Detener** en NODO n4. Suma desde el cliente del nodo 1 o 2.

## 7. Cierre

"El protocolo es ADS-1. Toda solicitud y toda respuesta llevan Código de Contenido, identificador único y, en la respuesta, la referencia a la solicitud original. El reporte documenta el formato, el broadcast y las dos barreras contra respuestas repetidas: la huella menor, y el filtro del cliente por si aun así llegara otra."

Menciona al final la liga del repositorio.
