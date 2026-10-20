package edu.computodistribuido.ads.nodo;

import edu.computodistribuido.ads.comun.CanalSocket;
import edu.computodistribuido.ads.comun.Huella;
import edu.computodistribuido.ads.comun.Reloj;
import edu.computodistribuido.ads.protocolo.Mensaje;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Un nodo del Campo de Datos. No calcula ni elige servidor: acepta células
 * y otros nodos, y difunde cada DATO una sola vez.
 */
public final class CampoDatos {
    public static final int SALTOS_MAXIMOS = 16;

    public interface Oyente {
        void registro(String texto);

        void estado(int paresConectados, int paresEsperados, int celulas, int mensajes, int duplicados, String ultimo);
    }

    public static final class Par {
        public final String id;
        public final String host;
        public final int puerto;

        public Par(String id, String host, int puerto) {
            this.id = id;
            this.host = host;
            this.puerto = puerto;
        }
    }

    private static final class Conexion {
        final CanalSocket canal;
        final boolean saliente;
        String id = "";
        String rol = "";
        String servicio = "";
        String huella = "";
        boolean lista;

        Conexion(CanalSocket canal, boolean saliente) {
            this.canal = canal;
            this.saliente = saliente;
        }
    }

    private final String id;
    private final int puerto;
    private final List<Par> pares;
    private final Oyente oyente;
    private final Object candado = new Object();
    private final Map<String, Conexion> nodos = new LinkedHashMap<String, Conexion>();
    private final List<Conexion> celulas = new ArrayList<Conexion>();
    private final Map<String, Long> vistos = new LinkedHashMap<String, Long>();
    private final ConcurrentHashMap<String, Boolean> intentando = new ConcurrentHashMap<String, Boolean>();
    private final ConcurrentHashMap<String, Long> intentoDesde = new ConcurrentHashMap<String, Long>();

    private ServerSocket servidor;
    private ScheduledExecutorService tareas;
    private volatile boolean activo;
    private int mensajes;
    private int duplicados;

    public CampoDatos(String id, int puerto, List<Par> pares, Oyente oyente) {
        this.id = id;
        this.puerto = puerto;
        this.pares = new ArrayList<Par>(pares);
        this.oyente = oyente;
    }

    public String getId() {
        return id;
    }

    public int getMensajes() {
        synchronized (candado) {
            return mensajes;
        }
    }

    public int getDuplicados() {
        synchronized (candado) {
            return duplicados;
        }
    }

    public int getParesConectados() {
        synchronized (candado) {
            return nodos.size();
        }
    }

    public int getCelulas() {
        synchronized (candado) {
            return celulas.size();
        }
    }

    public void iniciar() throws IOException {
        synchronized (candado) {
            if (activo) {
                return;
            }
            servidor = new ServerSocket();
            servidor.setReuseAddress(true);
            servidor.bind(new InetSocketAddress(puerto));
            activo = true;
            tareas = Executors.newScheduledThreadPool(2, r -> {
                Thread hilo = new Thread(r, "ads-nodo-" + id);
                hilo.setDaemon(true);
                return hilo;
            });
        }
        tareas.execute(this::aceptar);
        tareas.scheduleWithFixedDelay(this::intentarPares, 150, 700, TimeUnit.MILLISECONDS);
        publicar("Nodo " + id + " escucha en el puerto " + puerto + ".");
        notificarEstado("");
    }

    public void detener() {
        List<Conexion> cerrar = new ArrayList<Conexion>();
        ServerSocket socket;
        ScheduledExecutorService pool;
        synchronized (candado) {
            if (!activo && servidor == null) {
                return;
            }
            activo = false;
            socket = servidor;
            servidor = null;
            pool = tareas;
            tareas = null;
            cerrar.addAll(nodos.values());
            cerrar.addAll(celulas);
            nodos.clear();
            celulas.clear();
            intentando.clear();
            intentoDesde.clear();
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignorada) {
                // El accept saldrá por la excepción.
            }
        }
        for (Conexion conexion : cerrar) {
            conexion.canal.close();
        }
        if (pool != null) {
            pool.shutdownNow();
        }
        publicar("Nodo " + id + " detenido.");
        notificarEstado("");
    }

    /** Publica un DATO originado en este nodo (por ejemplo un WITHDRAW). */
    public void publicarDato(Mensaje mensaje) {
        difundir(null, mensaje);
    }

    private void aceptar() {
        while (activo) {
            try {
                Socket socket = servidor.accept();
                if (!activo) {
                    socket.close();
                    return;
                }
                Conexion conexion = new Conexion(new CanalSocket(socket), false);
                conexion.canal.iniciarLectura(escucha(conexion));
            } catch (IOException ex) {
                if (activo) {
                    publicar("Error al aceptar una conexión: " + ex.getMessage());
                }
                return;
            }
        }
    }

    private void intentarPares() {
        if (!activo) {
            return;
        }
        for (Par par : pares) {
            if (par.id.equals(id) || estaConectado(par.id)) {
                intentando.remove(par.id);
                intentoDesde.remove(par.id);
                continue;
            }
            Long desde = intentando.get(par.id) == null ? null : intentoDesde.get(par.id);
            long ahoraIntento = System.currentTimeMillis();
            if (desde != null && ahoraIntento - desde < 1500L) {
                continue;
            }
            intentando.put(par.id, Boolean.TRUE);
            intentoDesde.put(par.id, ahoraIntento);
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(par.host, par.puerto), 600);
                Conexion conexion = new Conexion(new CanalSocket(socket), true);
                conexion.id = par.id;
                conexion.canal.iniciarLectura(escucha(conexion));
                conexion.canal.enviar(helloNodo());
            } catch (Exception ex) {
                intentando.remove(par.id);
                intentoDesde.remove(par.id);
            }
        }
    }

    private boolean estaConectado(String remoto) {
        synchronized (candado) {
            return nodos.containsKey(remoto);
        }
    }

    private CanalSocket.Escucha escucha(final Conexion conexion) {
        return new CanalSocket.Escucha() {
            @Override
            public void alMensaje(Mensaje mensaje) {
                recibir(conexion, mensaje);
            }

            @Override
            public void alCierre(String motivo) {
                olvidar(conexion, motivo);
            }
        };
    }

    private void recibir(Conexion conexion, Mensaje mensaje) {
        if (Mensaje.HELLO.equals(mensaje.getTipo())) {
            if ("NODO".equals(mensaje.get("entidad"))) {
                registrarNodo(conexion, mensaje.get("id"), false);
                try {
                    conexion.canal.enviar(helloOk());
                } catch (RuntimeException ex) {
                    olvidar(conexion, "No aceptó el saludo.");
                }
            } else if ("CELULA".equals(mensaje.get("entidad"))) {
                registrarCelula(conexion, mensaje);
            }
            return;
        }
        if (Mensaje.HELLO_OK.equals(mensaje.getTipo())) {
            if (conexion.saliente) {
                String remoto = mensaje.get("id");
                if (remoto.isEmpty()) {
                    remoto = conexion.id;
                }
                registrarNodo(conexion, remoto, true);
            }
            return;
        }
        if (Mensaje.DATO.equals(mensaje.getTipo())) {
            if (!conexion.lista) {
                return;
            }
            difundir(conexion, mensaje);
        }
    }

    private void registrarNodo(Conexion nueva, String remoto, boolean yoInicie) {
        if (remoto == null || remoto.isEmpty() || remoto.equals(id)) {
            nueva.canal.close();
            return;
        }
        Conexion sobra = null;
        boolean alta = false;
        synchronized (candado) {
            intentando.remove(remoto);
            intentoDesde.remove(remoto);
            nueva.id = remoto;
            nueva.rol = "NODO";
            nueva.lista = true;
            Conexion previa = nodos.get(remoto);
            if (previa == null || previa == nueva) {
                nodos.put(remoto, nueva);
                alta = previa == null;
            } else {
                boolean menorInicia = id.compareTo(remoto) < 0;
                boolean nuevaEsLaBuena = yoInicie == menorInicia;
                if (nuevaEsLaBuena) {
                    nodos.put(remoto, nueva);
                    sobra = previa;
                } else {
                    sobra = nueva;
                }
            }
        }
        if (sobra != null && sobra != nueva) {
            sobra.lista = false;
            sobra.canal.close();
        } else if (sobra == nueva) {
            nueva.lista = false;
            nueva.canal.close();
        }
        if (alta) {
            publicar("Enlace de malla con " + remoto + ".");
            notificarEstado("");
        }
    }

    private void registrarCelula(Conexion conexion, Mensaje hello) {
        String celulaId = hello.get("id");
        if (celulaId.isEmpty()) {
            conexion.canal.close();
            return;
        }
        synchronized (candado) {
            conexion.id = celulaId;
            conexion.rol = hello.get("rol");
            conexion.servicio = hello.get("servicio");
            conexion.huella = hello.get("huella");
            conexion.lista = true;
            celulas.add(conexion);
        }
        try {
            conexion.canal.enviar(helloOk());
        } catch (RuntimeException ex) {
            olvidar(conexion, "La célula no recibió el saludo.");
            return;
        }
        String servicio = conexion.servicio.isEmpty() ? conexion.rol : conexion.servicio;
        publicar("Célula " + celulaId + " (" + servicio + ", huella " + conexion.huella + ").");
        notificarEstado("");
    }

    private void olvidar(Conexion conexion, String motivo) {
        Mensaje retirada = null;
        boolean cambio = false;
        synchronized (candado) {
            if (!conexion.id.isEmpty()) {
                intentando.remove(conexion.id);
                intentoDesde.remove(conexion.id);
            }
            if ("NODO".equals(conexion.rol)) {
                if (nodos.remove(conexion.id, conexion)) {
                    cambio = true;
                }
            } else if (conexion.lista && celulas.remove(conexion)) {
                cambio = true;
                retirada = retiradaDe(conexion);
            }
            conexion.lista = false;
        }
        if (cambio && "NODO".equals(conexion.rol)) {
            publicar("Se perdió el enlace con " + conexion.id + " (" + motivo + ").");
        } else if (retirada != null) {
            publicar("Célula " + conexion.id + " desconectada.");
        }
        if (retirada != null && activo) {
            difundir(null, retirada);
        }
        if (cambio) {
            notificarEstado("");
        }
    }

    private Mensaje retiradaDe(Conexion conexion) {
        return new Mensaje(Mensaje.DATO)
                .con("id", Huella.nuevoMensaje())
                .con("ref", "")
                .con("cc", "WITHDRAW")
                .con("origen", conexion.id)
                .con("huella", conexion.huella)
                .con("servicio", conexion.servicio)
                .con("rol", conexion.rol)
                .con("nodo", id)
                .con("op1", "")
                .con("op2", "")
                .con("res", "")
                .con("estado", "")
                .con("detalle", "desconexion")
                .con("hops", "0")
                .con("ts", Reloj.ahora());
    }

    private void difundir(Conexion origen, Mensaje entrada) {
        String idMensaje = entrada.get("id");
        String codigo = entrada.get("cc");
        if (idMensaje.isEmpty() || codigo.isEmpty()) {
            publicar("DATO rechazado: falta id o código de contenido.");
            return;
        }
        List<Conexion> destinos = new ArrayList<Conexion>();
        Mensaje saliente = null;
        boolean duplicado = false;
        synchronized (candado) {
            long ahora = System.currentTimeMillis();
            if (vistos.containsKey(idMensaje)) {
                duplicados++;
                duplicado = true;
            } else {
                int hops = entrada.getEntero("hops", 0) + 1;
                if (hops > SALTOS_MAXIMOS) {
                    duplicados++;
                    duplicado = true;
                } else {
                    vistos.put(idMensaje, ahora);
                    depurarVistos(ahora);
                    mensajes++;
                    saliente = entrada.copia().con("hops", Integer.toString(hops));
                    for (Conexion nodo : nodos.values()) {
                        if (nodo != origen && nodo.lista) {
                            destinos.add(nodo);
                        }
                    }
                    for (Conexion celula : celulas) {
                        if (celula != origen && celula.lista) {
                            destinos.add(celula);
                        }
                    }
                }
            }
        }
        String resumen = describir(entrada);
        if (duplicado || saliente == null) {
            publicar("CICLO EVITADO " + resumen + " · ya visto, no se retransmite.");
            notificarEstado(resumen);
            return;
        }
        int vecinos = contar(destinos, "NODO");
        int locales = contar(destinos, null);
        publicar("BROADCAST " + resumen + " → " + vecinos + (vecinos == 1 ? " vecino" : " vecinos")
                + ", " + locales + (locales == 1 ? " célula" : " células") + ".");
        notificarEstado(resumen);
        enviarTodos(destinos, saliente);
    }

    private void enviarTodos(List<Conexion> destinos, Mensaje saliente) {
        for (Conexion destino : destinos) {
            try {
                destino.canal.enviar(saliente);
            } catch (RuntimeException ex) {
                olvidar(destino, "Fallo al retransmitir.");
            }
        }
    }

    private static int contar(List<Conexion> destinos, String rolNodo) {
        int total = 0;
        for (Conexion conexion : destinos) {
            boolean esNodo = "NODO".equals(conexion.rol);
            if (rolNodo != null && esNodo) {
                total++;
            } else if (rolNodo == null && !esNodo) {
                total++;
            }
        }
        return total;
    }

    private void depurarVistos(long ahora) {
        if (vistos.size() < 3000) {
            return;
        }
        Iterator<Map.Entry<String, Long>> it = vistos.entrySet().iterator();
        while (it.hasNext() && vistos.size() > 2000) {
            Map.Entry<String, Long> entrada = it.next();
            if (ahora - entrada.getValue() > 120_000 || vistos.size() > 2500) {
                it.remove();
            }
        }
    }

    private static String describir(Mensaje mensaje) {
        StringBuilder sb = new StringBuilder();
        sb.append(mensaje.get("cc")).append(' ').append(Huella.corto(mensaje.get("id")));
        if (!mensaje.get("ref").isEmpty()) {
            sb.append(" ref=").append(Huella.corto(mensaje.get("ref")));
        }
        if (!mensaje.get("origen").isEmpty()) {
            sb.append(" origen=").append(mensaje.get("origen"));
        }
        if (!mensaje.get("op1").isEmpty() && mensaje.get("cc").endsWith("_REQ")) {
            sb.append(" ops=").append(mensaje.get("op1")).append(',').append(mensaje.get("op2"));
        }
        if (!mensaje.get("res").isEmpty()) {
            sb.append(" res=").append(mensaje.get("res"));
        }
        return sb.toString();
    }

    private Mensaje helloNodo() {
        return new Mensaje(Mensaje.HELLO)
                .con("entidad", "NODO")
                .con("id", id)
                .con("puerto", Integer.toString(puerto));
    }

    private Mensaje helloOk() {
        return new Mensaje(Mensaje.HELLO_OK)
                .con("id", id)
                .con("detalle", "aceptado");
    }

    private void publicar(String texto) {
        oyente.registro(texto);
    }

    private void notificarEstado(String ultimo) {
        int paresConectados;
        int celulasLocales;
        int totalMensajes;
        int totalDuplicados;
        synchronized (candado) {
            paresConectados = nodos.size();
            celulasLocales = celulas.size();
            totalMensajes = mensajes;
            totalDuplicados = duplicados;
        }
        oyente.estado(paresConectados, paresEsperados(), celulasLocales, totalMensajes, totalDuplicados, ultimo);
    }

    private int paresEsperados() {
        int esperados = 0;
        for (Par par : pares) {
            if (!par.id.equals(id)) {
                esperados++;
            }
        }
        return esperados;
    }

    public static List<Par> parsearPares(String texto) {
        List<Par> lista = new ArrayList<Par>();
        if (texto == null || texto.trim().isEmpty()) {
            return lista;
        }
        String[] trozos = texto.split(",");
        for (String trozo : trozos) {
            String limpio = trozo.trim();
            if (limpio.isEmpty()) {
                continue;
            }
            int igual = limpio.indexOf('=');
            int dosPuntos = limpio.lastIndexOf(':');
            if (igual <= 0 || dosPuntos <= igual) {
                throw new IllegalArgumentException("Par mal formado: " + limpio + ". Use id=host:puerto");
            }
            String idPar = limpio.substring(0, igual).trim();
            String host = limpio.substring(igual + 1, dosPuntos).trim();
            int puertoPar = Integer.parseInt(limpio.substring(dosPuntos + 1).trim());
            lista.add(new Par(idPar, host, puertoPar));
        }
        return lista;
    }
}
