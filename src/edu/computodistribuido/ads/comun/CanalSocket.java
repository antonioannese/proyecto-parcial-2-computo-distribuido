package edu.computodistribuido.ads.comun;

import edu.computodistribuido.ads.protocolo.Mensaje;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Un mensaje ADS-1 por línea. La escritura se serializa; la lectura vive en un hilo propio. */
public final class CanalSocket implements AutoCloseable {
    public interface Escucha {
        void alMensaje(Mensaje mensaje);

        void alCierre(String motivo);
    }

    private final Socket socket;
    private final BufferedReader entrada;
    private final PrintWriter salida;
    private final AtomicBoolean abierto = new AtomicBoolean(true);
    private volatile Escucha escucha;

    public CanalSocket(Socket socket) throws IOException {
        this.socket = socket;
        this.socket.setTcpNoDelay(true);
        this.entrada = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.salida = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    public void iniciarLectura(Escucha escucha) {
        this.escucha = escucha;
        Thread hilo = new Thread(this::bucleLectura, "ads-lectura");
        hilo.setDaemon(true);
        hilo.start();
    }

    public void enviar(Mensaje mensaje) {
        if (!abierto.get()) {
            throw new IllegalStateException("Canal cerrado.");
        }
        synchronized (salida) {
            salida.println(mensaje.serializar());
            if (salida.checkError()) {
                abierto.set(false);
                cerrarSocket();
                throw new IllegalStateException("No se pudo escribir en el canal.");
            }
        }
    }

    private void bucleLectura() {
        try {
            String linea;
            while (abierto.get() && (linea = entrada.readLine()) != null) {
                if (linea.trim().isEmpty()) {
                    continue;
                }
                Mensaje mensaje;
                try {
                    mensaje = Mensaje.parsear(linea);
                } catch (RuntimeException ex) {
                    continue;
                }
                Escucha actual = escucha;
                if (actual != null) {
                    actual.alMensaje(mensaje);
                }
            }
            notificar("El extremo cerró la conexión.");
        } catch (Exception ex) {
            if (abierto.get()) {
                String motivo = ex.getMessage();
                notificar(motivo == null || motivo.isEmpty() ? ex.getClass().getSimpleName() : motivo);
            }
        } finally {
            cerrarSocket();
        }
    }

    private void notificar(String motivo) {
        if (abierto.compareAndSet(true, false)) {
            Escucha actual = escucha;
            if (actual != null) {
                actual.alCierre(motivo);
            }
        }
    }

    private void cerrarSocket() {
        try {
            socket.close();
        } catch (IOException ignorada) {
            // Cierre defensivo.
        }
    }

    @Override
    public void close() {
        notificar("Cierre local.");
        cerrarSocket();
    }

    public boolean estaAbierto() {
        return abierto.get() && !socket.isClosed();
    }
}
