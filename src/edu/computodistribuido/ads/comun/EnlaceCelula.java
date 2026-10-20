package edu.computodistribuido.ads.comun;

import edu.computodistribuido.ads.protocolo.Mensaje;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Enlace de una célula con un nodo del Campo de Datos. */
public final class EnlaceCelula implements AutoCloseable {
    public interface Oyente {
        void alListo(String nodoId);

        void alDato(Mensaje mensaje);

        void alCierre(String motivo);
    }

    private final Object candado = new Object();
    private CanalSocket canal;
    private int generacion;
    private volatile boolean listo;
    private volatile CountDownLatch intento;

    public boolean estaListo() {
        return listo;
    }

    public void conectar(String host, int puerto, Mensaje hello, final Oyente oyente) throws IOException {
        final int gen = prepararNuevoIntento();
        CountDownLatch saludo = new CountDownLatch(1);
        intento = saludo;
        AtomicReference<String> fallo = new AtomicReference<String>(null);
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, puerto), 2500);
        } catch (IOException ex) {
            throw new IOException("No hay nodo en " + host + ":" + puerto + ".");
        }
        CanalSocket nuevo = new CanalSocket(socket);
        synchronized (candado) {
            if (gen != generacion) {
                nuevo.close();
                throw new IOException("Conexión cancelada.");
            }
            canal = nuevo;
        }
        nuevo.iniciarLectura(new CanalSocket.Escucha() {
            @Override
            public void alMensaje(Mensaje mensaje) {
                if (gen != generacion) {
                    return;
                }
                if (!listo && Mensaje.HELLO_OK.equals(mensaje.getTipo())) {
                    listo = true;
                    saludo.countDown();
                    oyente.alListo(mensaje.get("id"));
                    return;
                }
                if (listo && Mensaje.DATO.equals(mensaje.getTipo())) {
                    oyente.alDato(mensaje);
                }
            }

            @Override
            public void alCierre(String motivo) {
                if (gen != generacion) {
                    saludo.countDown();
                    return;
                }
                listo = false;
                fallo.compareAndSet(null, motivo);
                saludo.countDown();
                oyente.alCierre(motivo == null ? "Conexión cerrada." : motivo);
            }
        });
        try {
            nuevo.enviar(hello);
            if (!saludo.await(3, TimeUnit.SECONDS) || !listo || gen != generacion) {
                descartar(gen);
                String motivo = fallo.get();
                throw new IOException(motivo == null ? "El nodo no aceptó la célula." : motivo);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            descartar(gen);
            throw new IOException("Conexión interrumpida.");
        }
    }

    public void enviar(Mensaje mensaje) {
        CanalSocket actual;
        synchronized (candado) {
            actual = canal;
        }
        if (actual == null || !listo) {
            throw new IllegalStateException("La célula no está conectada.");
        }
        actual.enviar(mensaje);
    }

    private int prepararNuevoIntento() {
        CanalSocket anterior;
        synchronized (candado) {
            generacion++;
            anterior = canal;
            canal = null;
            listo = false;
        }
        CountDownLatch pendiente = intento;
        if (pendiente != null) {
            pendiente.countDown();
        }
        if (anterior != null) {
            anterior.close();
        }
        return generacion;
    }

    private void descartar(int gen) {
        CanalSocket actual;
        synchronized (candado) {
            if (gen != generacion) {
                return;
            }
            actual = canal;
            canal = null;
            listo = false;
        }
        if (actual != null) {
            actual.close();
        }
    }

    @Override
    public void close() {
        prepararNuevoIntento();
    }
}
