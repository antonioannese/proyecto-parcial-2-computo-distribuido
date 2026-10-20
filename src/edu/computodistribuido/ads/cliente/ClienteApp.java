package edu.computodistribuido.ads.cliente;

import edu.computodistribuido.ads.comun.Args;
import edu.computodistribuido.ads.comun.EnlaceCelula;
import edu.computodistribuido.ads.comun.EstiloUI;
import edu.computodistribuido.ads.comun.Huella;
import edu.computodistribuido.ads.comun.Numeros;
import edu.computodistribuido.ads.comun.Reloj;
import edu.computodistribuido.ads.protocolo.CodigoContenido;
import edu.computodistribuido.ads.protocolo.Mensaje;
import edu.computodistribuido.ads.protocolo.Servicio;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Célula GUI. Publica la solicitud y muestra la respuesta cuyo ref corresponde a la suya. */
public final class ClienteApp extends JFrame {
    private final Args args;
    private final String id;
    private final String huella = Huella.nueva();
    private final EnlaceCelula enlace = new EnlaceCelula();
    private final AtomicBoolean conectando = new AtomicBoolean(false);
    private final AtomicInteger vistos = new AtomicInteger();
    private final Set<String> mias = ConcurrentHashMap.newKeySet();
    private final Set<String> mostradas = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService reloj = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread hilo = new Thread(r, "ads-gui-tiempo");
        hilo.setDaemon(true);
        return hilo;
    });

    private final JTextField op1 = EstiloUI.campo(6);
    private final JTextField op2 = EstiloUI.campo(6);
    private final JLabel resultado = EstiloUI.cifra("—", EstiloUI.ACIERTO, 40);
    private final JLabel expresion = EstiloUI.texto("Esperando una operación", true);
    private final JLabel vistosLabel = EstiloUI.cifra("0", EstiloUI.NODO, 22);
    private final JLabel estado = EstiloUI.texto("Desconectada", true);
    private final EstiloUI.Bitacora bitacora;
    private final JButton botonIniciar;
    private final JButton botonDetener;
    private final JButton[] operaciones;
    private volatile boolean querer;
    private volatile String ultimaSolicitud = "";
    private volatile ScheduledFuture<?> espera;

    private ClienteApp(Args args) {
        super("Cliente");
        this.args = args;
        this.id = args.obtener("id", "gui-1");
        this.bitacora = new EstiloUI.Bitacora(id);
        Color azul = new Color(31, 78, 121);
        this.botonIniciar = EstiloUI.boton("Iniciar", azul);
        this.botonDetener = EstiloUI.boton("Detener", new Color(90, 98, 110));
        this.operaciones = new JButton[]{
                EstiloUI.boton("+", azul),
                EstiloUI.boton("−", azul),
                EstiloUI.boton("×", azul),
                EstiloUI.boton("÷", azul)
        };

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("CLIENTE · " + id);
        getContentPane().setBackground(EstiloUI.FONDO);
        setLayout(new BorderLayout());
        ((JPanel) getContentPane()).setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel norte = new JPanel(new BorderLayout());
        norte.setOpaque(false);
        norte.add(EstiloUI.banner("CLIENTE",
                id + "  ·  huella " + huella,
                azul), BorderLayout.NORTH);
        add(norte, BorderLayout.NORTH);

        JPanel centro = new JPanel(new BorderLayout(0, 4));
        centro.setOpaque(false);
        centro.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 10, 4, 10));

        JPanel filaVistos = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        filaVistos.setOpaque(false);
        filaVistos.add(EstiloUI.texto("Mensajes vistos", true));
        filaVistos.add(vistosLabel);
        centro.add(filaVistos, BorderLayout.NORTH);

        JPanel cuerpo = new JPanel(new BorderLayout());
        cuerpo.setOpaque(false);
        expresion.setHorizontalAlignment(JLabel.CENTER);
        resultado.setHorizontalAlignment(JLabel.CENTER);
        cuerpo.add(expresion, BorderLayout.NORTH);
        cuerpo.add(resultado, BorderLayout.CENTER);

        JPanel operandos = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        operandos.setOpaque(false);
        op1.setText("9");
        op2.setText("4");
        operandos.add(op1);
        operandos.add(op2);
        cuerpo.add(operandos, BorderLayout.SOUTH);
        centro.add(cuerpo, BorderLayout.CENTER);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));
        botones.setOpaque(false);
        botones.add(operaciones[0]);
        botones.add(operaciones[1]);
        botones.add(operaciones[2]);
        botones.add(operaciones[3]);
        centro.add(botones, BorderLayout.SOUTH);
        add(centro, BorderLayout.CENTER);

        bitacora.getArea().setRows(5);
        JPanel sur = new JPanel(new BorderLayout(0, 4));
        sur.setOpaque(false);
        sur.add(EstiloUI.scroll(bitacora.getArea()), BorderLayout.CENTER);
        estado.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 10, 0, 10));
        sur.add(estado, BorderLayout.NORTH);
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        acciones.setOpaque(false);
        botonDetener.setEnabled(false);
        JButton salir = EstiloUI.boton("Salir", new Color(70, 76, 86));
        acciones.add(botonIniciar);
        acciones.add(botonDetener);
        acciones.add(salir);
        sur.add(acciones, BorderLayout.SOUTH);
        add(sur, BorderLayout.SOUTH);

        operaciones[0].addActionListener(e -> enviar(Servicio.SUMA));
        operaciones[1].addActionListener(e -> enviar(Servicio.RESTA));
        operaciones[2].addActionListener(e -> enviar(Servicio.MULTIPLICACION));
        operaciones[3].addActionListener(e -> enviar(Servicio.DIVISION));
        botonIniciar.addActionListener(e -> iniciar());
        botonDetener.addActionListener(e -> detener());
        salir.addActionListener(e -> {
            detener();
            dispose();
            System.exit(0);
        });
        habilitarOperaciones(false);
        EstiloUI.geometria(this, args, 360, 420);
        expresion.setFont(new Font("SansSerif", Font.PLAIN, 13));

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
            JOptionPane.showMessageDialog(this, "Indique --nodo host:puerto", "Cliente", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String host = nodo.substring(0, separador);
        int puerto;
        try {
            puerto = Integer.parseInt(nodo.substring(separador + 1));
        } catch (NumberFormatException ex) {
            conectando.set(false);
            JOptionPane.showMessageDialog(this, "Puerto inválido.", "Cliente", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Thread hilo = new Thread(() -> {
            while (querer && !enlace.estaListo()) {
                try {
                    enlace.conectar(host, puerto, hello(), new EnlaceCelula.Oyente() {
                        @Override
                        public void alListo(String nodoId) {
                            SwingUtilities.invokeLater(() -> {
                                estado.setText("Conectada al nodo " + nodoId);
                                habilitarOperaciones(true);
                            });
                            bitacora.agregar("En el Campo de Datos del nodo " + nodoId + ". Huella " + huella + ".");
                        }

                        @Override
                        public void alDato(Mensaje mensaje) {
                            SwingUtilities.invokeLater(() -> recibir(mensaje));
                        }

                        @Override
                        public void alCierre(String motivo) {
                            bitacora.agregar("Enlace cerrado: " + motivo);
                            SwingUtilities.invokeLater(() -> {
                                if (!enlace.estaListo()) {
                                    estado.setText("Desconectada");
                                    habilitarOperaciones(false);
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
                .con("rol", "GUI")
                .con("id", id)
                .con("huella", huella)
                .con("servicio", "");
    }

    private void enviar(Servicio servicio) {
        if (!enlace.estaListo()) {
            JOptionPane.showMessageDialog(this, "Inicie la conexión con el nodo.", "Cliente", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final double a;
        final double b;
        try {
            a = Numeros.parsear(op1.getText());
            b = Numeros.parsear(op2.getText());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Los operandos deben ser numéricos.", "Cliente", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String idSolicitud = Huella.nuevoMensaje();
        Mensaje solicitud = new Mensaje(Mensaje.DATO)
                .con("id", idSolicitud)
                .con("ref", "")
                .con("cc", servicio.solicitud().name())
                .con("origen", id)
                .con("huella", huella)
                .con("op1", Numeros.formatear(a))
                .con("op2", Numeros.formatear(b))
                .con("res", "")
                .con("estado", "")
                .con("detalle", "")
                .con("hops", "0")
                .con("ts", Reloj.ahora());
        try {
            enlace.enviar(solicitud);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cliente", JOptionPane.ERROR_MESSAGE);
            return;
        }
        mias.add(idSolicitud);
        ultimaSolicitud = idSolicitud;
        String texto = Numeros.formatear(a) + " " + servicio.getSimbolo() + " " + Numeros.formatear(b);
        expresion.setText(texto);
        resultado.setForeground(new Color(168, 110, 8));
        resultado.setText("…");
        bitacora.agregar("PUBLICO " + servicio.solicitud().name() + " " + texto
                + " · id " + Huella.corto(idSolicitud));
        if (espera != null) {
            espera.cancel(false);
        }
        espera = reloj.schedule(() -> SwingUtilities.invokeLater(() -> {
            if (mostradas.add(idSolicitud)) {
                resultado.setForeground(EstiloUI.ERROR);
                resultado.setText("sin resp.");
                bitacora.agregar("Sin respuesta para " + Huella.corto(idSolicitud)
                        + ". ¿Hay una célula de " + servicio.getNombre() + "?");
            }
        }), 4, TimeUnit.SECONDS);
    }

    private void recibir(Mensaje mensaje) {
        int total = vistos.incrementAndGet();
        vistosLabel.setText(Integer.toString(total));
        CodigoContenido codigo = CodigoContenido.desde(mensaje.get("cc"));
        if (codigo == null) {
            return;
        }
        String ref = mensaje.get("ref");
        if (codigo.esRespuesta() && mias.contains(ref)) {
            if (!mostradas.add(ref)) {
                bitacora.agregar("Descarto " + codigo.name() + " duplicada de " + mensaje.get("origen")
                        + " · ref " + Huella.corto(ref));
                return;
            }
            if (espera != null && ref.equals(ultimaSolicitud)) {
                espera.cancel(false);
            }
            if (ref.equals(ultimaSolicitud)) {
                if ("OK".equals(mensaje.get("estado"))) {
                    resultado.setForeground(EstiloUI.ACIERTO);
                    resultado.setText(mensaje.get("res"));
                    expresion.setText(mensaje.get("op1") + " " + simboloDe(codigo) + " " + mensaje.get("op2")
                            + " = " + mensaje.get("res"));
                } else {
                    resultado.setForeground(EstiloUI.ERROR);
                    resultado.setText("error");
                    expresion.setText(mensaje.get("detalle"));
                }
            }
            bitacora.agregar("RECIBÍ " + codigo.name() + " de " + mensaje.get("origen")
                    + " (huella " + mensaje.get("huella") + ") · ref " + Huella.corto(ref)
                    + ("OK".equals(mensaje.get("estado")) ? " = " + mensaje.get("res") : " · " + mensaje.get("detalle")));
            return;
        }
        if (codigo.esSolicitud() || codigo.esRespuesta() || codigo.esReclamo()) {
            String extra = mensaje.get("res").isEmpty() ? "" : " = " + mensaje.get("res");
            bitacora.agregar("Broadcast " + codigo.name() + " de " + mensaje.get("origen") + extra);
        }
    }

    private static String simboloDe(CodigoContenido codigo) {
        if (codigo == null) {
            return "?";
        }
        switch (codigo) {
            case SUM_RES:
                return "+";
            case SUB_RES:
                return "−";
            case MUL_RES:
                return "×";
            case DIV_RES:
                return "÷";
            default:
                return "?";
        }
    }

    private void habilitarOperaciones(boolean valor) {
        for (JButton boton : operaciones) {
            boton.setEnabled(valor);
        }
        op1.setEnabled(valor);
        op2.setEnabled(valor);
    }

    private void detener() {
        querer = false;
        enlace.close();
        conectando.set(false);
        botonIniciar.setEnabled(true);
        botonDetener.setEnabled(false);
        habilitarOperaciones(false);
        estado.setText("Desconectada");
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
        SwingUtilities.invokeLater(() -> new ClienteApp(new Args(args)).setVisible(true));
    }
}
