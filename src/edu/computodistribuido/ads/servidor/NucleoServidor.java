package edu.computodistribuido.ads.servidor;

import edu.computodistribuido.ads.comun.Huella;
import edu.computodistribuido.ads.comun.Numeros;
import edu.computodistribuido.ads.comun.Reloj;
import edu.computodistribuido.ads.protocolo.CodigoContenido;
import edu.computodistribuido.ads.protocolo.Mensaje;
import edu.computodistribuido.ads.protocolo.Servicio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Varias células del mismo servicio reciben el mismo REQ.
 * Cada una publica un reclamo con su huella. Pasada una ventana breve,
 * solo la huella menor envía la respuesta. Si esa respuesta no llega,
 * la siguiente huella la respalda. No hay coordinador.
 */
public final class NucleoServidor {
    static final long ESPERA_RECLAMO_MS = 60L;
    static final long ESPERA_DECISION_MS = 320L;
    static final long ESPERA_RESPALDO_MS = 900L;

    public interface Salida {
        void enviar(Mensaje mensaje);

        void nota(String texto);

        void estado(String texto);
    }

    private static final class Caso {
        final Mensaje solicitud;
        final TreeSet<String> huellas = new TreeSet<String>();
        boolean cerrado;
        ScheduledFuture<?> reclamo;
        ScheduledFuture<?> decision;
        ScheduledFuture<?> respaldo;

        Caso(Mensaje solicitud) {
            this.solicitud = solicitud;
        }
    }

    private final String id;
    private final String huella;
    private final Servicio servicio;
    private final Salida salida;
    private final Object candado = new Object();
    private final Map<String, Caso> casos = new ConcurrentHashMap<String, Caso>();
    private final AtomicInteger solicitudesVistas = new AtomicInteger();
    private final AtomicInteger respuestasEmitidas = new AtomicInteger();
    private final AtomicInteger cedidas = new AtomicInteger();
    private final ScheduledExecutorService reloj;
    private volatile String nodoId = "";

    public NucleoServidor(String id, String huella, Servicio servicio, Salida salida) {
        this.id = id;
        this.huella = huella;
        this.servicio = servicio;
        this.salida = salida;
        this.reloj = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread hilo = new Thread(r, "ads-servicio-" + id);
            hilo.setDaemon(true);
            return hilo;
        });
    }

    public int getSolicitudesVistas() {
        return solicitudesVistas.get();
    }

    public int getRespuestasEmitidas() {
        return respuestasEmitidas.get();
    }

    public int getCedidas() {
        return cedidas.get();
    }

    public void alConectar(String nodo) {
        this.nodoId = nodo == null ? "" : nodo;
        salida.estado("Conectada a " + this.nodoId + ". " + servicio.getLema() + ".");
        salida.nota("Anuncio " + servicio.solicitud().name() + " · huella " + huella + " · nodo " + this.nodoId);
        salida.enviar(dato(CodigoContenido.PRESENCE, Huella.nuevoMensaje(), "")
                .con("servicio", servicio.name())
                .con("rol", "SERVIDOR")
                .con("nodo", this.nodoId));
    }

    public void alDato(Mensaje mensaje) {
        CodigoContenido codigo = CodigoContenido.desde(mensaje.get("cc"));
        if (codigo == null) {
            return;
        }
        if (codigo == CodigoContenido.PRESENCE) {
            if (servicio.name().equals(mensaje.get("servicio")) && !id.equals(mensaje.get("origen"))) {
                salida.nota("También está " + mensaje.get("origen") + " para " + servicio.getNombre() + ".");
            }
            return;
        }
        if (codigo == CodigoContenido.WITHDRAW) {
            if (servicio.name().equals(mensaje.get("servicio")) && !id.equals(mensaje.get("origen"))) {
                salida.nota(mensaje.get("origen") + " salió del Campo de Datos.");
            }
            return;
        }
        if (codigo == servicio.solicitud()) {
            solicitudesVistas.incrementAndGet();
            abrirCaso(mensaje);
            return;
        }
        if (codigo == servicio.reclamo() && !huella.equals(mensaje.get("huella"))) {
            anotarReclamo(mensaje.get("ref"), mensaje.get("huella"), mensaje.get("origen"));
            return;
        }
        if (codigo == servicio.respuesta() && !huella.equals(mensaje.get("huella"))) {
            cerrarPorRespuesta(mensaje.get("ref"), mensaje.get("origen"));
            return;
        }
        if (codigo.esSolicitud()) {
            salida.nota("Ignoro " + codigo.name() + "; mi código es " + servicio.solicitud().name() + ".");
        }
    }

    public void cerrar() {
        reloj.shutdownNow();
    }

    private void abrirCaso(Mensaje solicitud) {
        String idSolicitud = solicitud.get("id");
        if (idSolicitud.isEmpty()) {
            return;
        }
        synchronized (candado) {
            Caso previo = casos.get(idSolicitud);
            if (previo != null) {
                return;
            }
            Caso caso = new Caso(solicitud);
            casos.put(idSolicitud, caso);
            salida.nota("RECIBÍ " + servicio.solicitud().name() + " " + expresionDe(solicitud)
                    + " de " + solicitud.get("origen") + " · id " + Huella.corto(idSolicitud));
            salida.estado("Vi " + expresionDe(solicitud) + ". Me postulo.");
            caso.reclamo = reloj.schedule(() -> postularse(idSolicitud), ESPERA_RECLAMO_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void postularse(String idSolicitud) {
        synchronized (candado) {
            Caso caso = casos.get(idSolicitud);
            if (caso == null || caso.cerrado) {
                return;
            }
            caso.huellas.add(huella);
            caso.decision = reloj.schedule(() -> decidir(idSolicitud), ESPERA_DECISION_MS, TimeUnit.MILLISECONDS);
        }
        salida.nota("Reclamo " + Huella.corto(idSolicitud) + " con huella " + huella + ".");
        Caso caso = casos.get(idSolicitud);
        if (caso != null) {
            salida.enviar(base(servicio.reclamo(), idSolicitud, caso.solicitud));
        }
    }

    private void anotarReclamo(String ref, String huellaAjena, String origen) {
        if (ref == null || ref.isEmpty() || huellaAjena == null || huellaAjena.isEmpty()) {
            return;
        }
        synchronized (candado) {
            Caso caso = casos.get(ref);
            if (caso == null || caso.cerrado) {
                return;
            }
            caso.huellas.add(huellaAjena);
        }
        salida.nota(origen + " también se postula a " + Huella.corto(ref) + " (huella " + huellaAjena + ").");
    }

    private void decidir(String idSolicitud) {
        Mensaje respuesta = null;
        String texto = null;
        String ganadora = null;
        synchronized (candado) {
            Caso caso = casos.get(idSolicitud);
            if (caso == null || caso.cerrado) {
                return;
            }
            if (caso.huellas.isEmpty()) {
                caso.huellas.add(huella);
            }
            ganadora = caso.huellas.first();
            if (!huella.equals(ganadora)) {
                cedidas.incrementAndGet();
                salida.nota("No respondo " + Huella.corto(idSolicitud) + ": atiende la huella " + ganadora + ".");
                salida.estado("Cedí " + expresionDe(caso.solicitud) + " a otra célula.");
                programarRespaldo(caso, idSolicitud);
                return;
            }
            caso.cerrado = true;
            respuesta = construirRespuesta(caso.solicitud, idSolicitud);
            texto = describirRespuesta(caso.solicitud, respuesta);
        }
        salida.enviar(respuesta);
        respuestasEmitidas.incrementAndGet();
        salida.nota("RESPONDO " + servicio.respuesta().name() + " " + texto
                + " · ref " + Huella.corto(idSolicitud));
        salida.estado(texto);
    }

    private void programarRespaldo(Caso caso, String idSolicitud) {
        List<String> orden = new ArrayList<String>(caso.huellas);
        int indice = orden.indexOf(huella);
        if (indice <= 0) {
            return;
        }
        long espera = ESPERA_RESPALDO_MS + (indice * 250L);
        caso.respaldo = reloj.schedule(() -> respaldar(idSolicitud), espera, TimeUnit.MILLISECONDS);
    }

    private void respaldar(String idSolicitud) {
        Mensaje respuesta;
        Mensaje reclamo;
        String texto;
        synchronized (candado) {
            Caso caso = casos.get(idSolicitud);
            if (caso == null || caso.cerrado) {
                return;
            }
            caso.cerrado = true;
            reclamo = base(servicio.reclamo(), idSolicitud, caso.solicitud);
            respuesta = construirRespuesta(caso.solicitud, idSolicitud);
            texto = describirRespuesta(caso.solicitud, respuesta);
        }
        salida.enviar(reclamo);
        salida.enviar(respuesta);
        respuestasEmitidas.incrementAndGet();
        salida.nota("RESPALDO " + servicio.respuesta().name() + " " + texto
                + " · la huella menor no contestó.");
        salida.estado("Respaldo: " + texto);
    }

    private void cerrarPorRespuesta(String ref, String origen) {
        if (ref == null || ref.isEmpty()) {
            return;
        }
        synchronized (candado) {
            Caso caso = casos.get(ref);
            if (caso == null) {
                return;
            }
            caso.cerrado = true;
            cancelar(caso.reclamo);
            cancelar(caso.decision);
            cancelar(caso.respaldo);
        }
        salida.nota("Vi " + servicio.respuesta().name() + " de " + origen + " para " + Huella.corto(ref) + ".");
    }

    private Mensaje construirRespuesta(Mensaje solicitud, String idSolicitud) {
        Mensaje respuesta = base(servicio.respuesta(), idSolicitud, solicitud);
        try {
            double a = Numeros.parsear(solicitud.get("op1"));
            double b = Numeros.parsear(solicitud.get("op2"));
            MotorCalculo.Resultado resultado = MotorCalculo.resolver(servicio, a, b);
            if (resultado.exito) {
                respuesta.con("res", Numeros.formatear(resultado.valor)).con("estado", "OK").con("detalle", "ok");
            } else {
                respuesta.con("res", "").con("estado", "ERROR").con("detalle", resultado.detalle);
            }
        } catch (RuntimeException ex) {
            respuesta.con("res", "").con("estado", "ERROR").con("detalle", "Operandos inválidos.");
        }
        return respuesta;
    }

    private String describirRespuesta(Mensaje solicitud, Mensaje respuesta) {
        try {
            double a = Numeros.parsear(solicitud.get("op1"));
            double b = Numeros.parsear(solicitud.get("op2"));
            String base = MotorCalculo.expresion(servicio, a, b);
            if ("OK".equals(respuesta.get("estado"))) {
                return base + " = " + respuesta.get("res");
            }
            return base + " → " + respuesta.get("detalle");
        } catch (RuntimeException ex) {
            return respuesta.get("detalle");
        }
    }

    private static String expresionDe(Mensaje solicitud) {
        String a = solicitud.get("op1");
        String b = solicitud.get("op2");
        if (a.isEmpty() && b.isEmpty()) {
            return "";
        }
        return a + " , " + b;
    }

    private Mensaje base(CodigoContenido codigo, String ref, Mensaje solicitud) {
        return dato(codigo, Huella.nuevoMensaje(), ref)
                .con("op1", solicitud.get("op1"))
                .con("op2", solicitud.get("op2"))
                .con("servicio", servicio.name());
    }

    private Mensaje dato(CodigoContenido codigo, String idMensaje, String ref) {
        return new Mensaje(Mensaje.DATO)
                .con("id", idMensaje)
                .con("ref", ref)
                .con("cc", codigo.name())
                .con("origen", id)
                .con("huella", huella)
                .con("nodo", nodoId)
                .con("op1", "")
                .con("op2", "")
                .con("res", "")
                .con("estado", "")
                .con("detalle", "")
                .con("hops", "0")
                .con("ts", Reloj.ahora());
    }

    private static void cancelar(ScheduledFuture<?> futuro) {
        if (futuro != null) {
            futuro.cancel(false);
        }
    }
}
