package edu.computodistribuido.ads.servidor;

import edu.computodistribuido.ads.comun.Args;
import edu.computodistribuido.ads.comun.EnlaceCelula;
import edu.computodistribuido.ads.comun.EstiloUI;
import edu.computodistribuido.ads.comun.Huella;
import edu.computodistribuido.ads.protocolo.Mensaje;
import edu.computodistribuido.ads.protocolo.Servicio;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.concurrent.atomic.AtomicBoolean;

/** Célula servidora. El título dice qué sabe hacer: sumar, restar, multiplicar o dividir. */
public final class ServidorApp extends JFrame {
    private final Args args;
    private final String id;
    private final String huella = Huella.nueva();
    private final Servicio servicio;
    private final EnlaceCelula enlace = new EnlaceCelula();
    private final AtomicBoolean conectando = new AtomicBoolean(false);
    private final JLabel estado = EstiloUI.texto("Detenida", true);
    private final JButton botonIniciar;
    private final JButton botonDetener;
    private final EstiloUI.Bitacora bitacora;
    private volatile NucleoServidor nucleo;
    private volatile boolean querer;

    private ServidorApp(Args args, Servicio servicio) {
        super(servicio.getLema());
        this.args = args;
        this.servicio = servicio;
        this.id = args.obtener("id", servicio.name().toLowerCase() + "-1");
        this.bitacora = new EstiloUI.Bitacora(id);
        this.botonIniciar = EstiloUI.boton("Iniciar", servicio.getColor());
        this.botonDetener = EstiloUI.boton("Detener", new Color(90, 98, 110));

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle(servicio.getLema() + " · " + id);
        getContentPane().setBackground(EstiloUI.FONDO);
        setLayout(new BorderLayout());
        ((JPanel) getContentPane()).setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel norte = new JPanel(new BorderLayout(0, 4));
        norte.setOpaque(false);
        norte.add(EstiloUI.banner(servicio.getLema(),
                id + "  ·  huella " + huella + "  ·  " + servicio.solicitud().name() + " → " + servicio.respuesta().name(),
                servicio.getColor()), BorderLayout.NORTH);
        estado.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 10, 2, 10));
        norte.add(estado, BorderLayout.SOUTH);
        add(norte, BorderLayout.NORTH);
        add(EstiloUI.scroll(bitacora.getArea()), BorderLayout.CENTER);

        JPanel sur = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        sur.setOpaque(false);
        botonDetener.setEnabled(false);
        sur.add(botonIniciar);
        sur.add(botonDetener);
        JButton salir = EstiloUI.boton("Salir", new Color(70, 76, 86));
        sur.add(salir);
        add(sur, BorderLayout.SOUTH);

        botonIniciar.addActionListener(e -> iniciar());
        botonDetener.addActionListener(e -> detener());
        salir.addActionListener(e -> {
            detener();
            dispose();
            System.exit(0);
        });
        EstiloUI.geometria(this, args, 340, 230);

        if (args.tiene("nodo")) {
            SwingUtilities.invokeLater(this::iniciar);
        }
    }

    private void iniciar() {
        if (!conectando.compareAndSet(false, true)) {
            return;
        }
        querer = true;
        botonIniciar.setEnabled(false);
        botonDetener.setEnabled(true);
        String nodo = args.obtener("nodo", "");
        int separador = nodo.lastIndexOf(':');
        if (separador <= 0) {
            conectando.set(false);
            botonIniciar.setEnabled(true);
            botonDetener.setEnabled(false);
            JOptionPane.showMessageDialog(this, "Indique --nodo host:puerto", "Servidor", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String host = nodo.substring(0, separador);
        int puerto;
        try {
            puerto = Integer.parseInt(nodo.substring(separador + 1));
        } catch (NumberFormatException ex) {
            conectando.set(false);
            JOptionPane.showMessageDialog(this, "Puerto inválido.", "Servidor", JOptionPane.WARNING_MESSAGE);
            return;
        }
        NucleoServidor nuevo = new NucleoServidor(id, huella, servicio, new NucleoServidor.Salida() {
            @Override
            public void enviar(Mensaje mensaje) {
                try {
                    enlace.enviar(mensaje);
                } catch (RuntimeException ex) {
                    bitacora.agregar("No pude publicar: " + ex.getMessage());
                }
            }

            @Override
            public void nota(String texto) {
                bitacora.agregar(texto);
            }

            @Override
            public void estado(String texto) {
                SwingUtilities.invokeLater(() -> ServidorApp.this.estado.setText(texto));
            }
        });
        NucleoServidor anterior = nucleo;
        nucleo = nuevo;
        if (anterior != null) {
            anterior.cerrar();
        }
        Thread hilo = new Thread(() -> {
            while (querer && !enlace.estaListo()) {
                try {
                    enlace.conectar(host, puerto, hello(), new EnlaceCelula.Oyente() {
                        @Override
                        public void alListo(String nodoId) {
                            NucleoServidor actual = nucleo;
                            if (actual != null) {
                                actual.alConectar(nodoId);
                            }
                        }

                        @Override
                        public void alDato(Mensaje mensaje) {
                            NucleoServidor actual = nucleo;
                            if (actual != null) {
                                actual.alDato(mensaje);
                            }
                        }

                        @Override
                        public void alCierre(String motivo) {
                            bitacora.agregar("Enlace cerrado: " + motivo);
                            SwingUtilities.invokeLater(() -> {
                                if (!enlace.estaListo()) {
                                    estado.setText("Desconectada");
                                }
                            });
                        }
                    });
                } catch (Exception ex) {
                    if (querer) {
                        bitacora.agregar(ex.getMessage() + " Reintento.");
                        dormir();
                    }
                }
            }
            conectando.set(false);
        }, "conexion-" + id);
        hilo.setDaemon(true);
        hilo.start();
    }

    private Mensaje hello() {
        return new Mensaje(Mensaje.HELLO)
                .con("entidad", "CELULA")
                .con("rol", "SERVIDOR")
                .con("id", id)
                .con("huella", huella)
                .con("servicio", servicio.name());
    }

    private void detener() {
        querer = false;
        enlace.close();
        NucleoServidor actual = nucleo;
        nucleo = null;
        if (actual != null) {
            actual.cerrar();
        }
        conectando.set(false);
        botonIniciar.setEnabled(true);
        botonDetener.setEnabled(false);
        estado.setText("Detenida");
    }

    private static void dormir() {
        try {
            Thread.sleep(600);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        EstiloUI.aplicarLookAndFeel();
        Args argumentos = new Args(args);
        Servicio servicio;
        try {
            servicio = Servicio.desde(argumentos.obtener("servicio", ""));
        } catch (RuntimeException ex) {
            System.err.println("Uso: --id suma-1 --servicio SUMA|RESTA|MULTIPLICACION|DIVISION --nodo 127.0.0.1:7401");
            return;
        }
        SwingUtilities.invokeLater(() -> new ServidorApp(argumentos, servicio).setVisible(true));
    }
}
