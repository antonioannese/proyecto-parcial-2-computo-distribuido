package edu.computodistribuido.ads.nodo;

import edu.computodistribuido.ads.comun.Args;
import edu.computodistribuido.ads.comun.EstiloUI;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.List;

/**
 * Ventana de un nodo del Campo de Datos. El número grande es el tráfico
 * difundido; al lado, los duplicados que se descartaron para cortar ciclos.
 */
public final class NodoApp extends JFrame implements CampoDatos.Oyente {
    private final Args args;
    private final JLabel cifraMensajes = EstiloUI.cifra("0", EstiloUI.NODO, 42);
    private final JLabel cifraCiclos = EstiloUI.cifra("0", new Color(168, 110, 8), 28);
    private final JLabel estado = EstiloUI.texto("Detenido", true);
    private final JButton botonIniciar = EstiloUI.boton("Iniciar", EstiloUI.NODO);
    private final JButton botonDetener = EstiloUI.boton("Detener", new Color(90, 98, 110));
    private final EstiloUI.Bitacora bitacora;
    private CampoDatos campo;

    private NodoApp(Args args) {
        super("Nodo");
        this.args = args;
        String id = args.obtener("id", "nodo");
        bitacora = new EstiloUI.Bitacora(id);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(EstiloUI.FONDO);
        setLayout(new BorderLayout(0, 6));
        JPanel raiz = (JPanel) getContentPane();
        raiz.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel norte = new JPanel(new BorderLayout());
        norte.setOpaque(false);
        norte.add(EstiloUI.banner("NODO " + id, "Campo de Datos · Antonio Annese 0266972", EstiloUI.NODO),
                BorderLayout.NORTH);

        JPanel cifras = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 0));
        cifras.setOpaque(false);
        JPanel bloqueMensajes = bloque("Mensajes difundidos", cifraMensajes);
        JPanel bloqueCiclos = bloque("Ciclos evitados", cifraCiclos);
        cifras.add(bloqueMensajes);
        cifras.add(bloqueCiclos);
        norte.add(cifras, BorderLayout.CENTER);
        norte.add(estado, BorderLayout.SOUTH);
        estado.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 10, 4, 10));
        add(norte, BorderLayout.NORTH);

        add(EstiloUI.scroll(bitacora.getArea()), BorderLayout.CENTER);

        JPanel sur = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        sur.setOpaque(false);
        botonDetener.setEnabled(false);
        sur.add(botonIniciar);
        sur.add(botonDetener);
        add(sur, BorderLayout.SOUTH);

        botonIniciar.addActionListener(e -> iniciar());
        botonDetener.addActionListener(e -> detener());
        EstiloUI.geometria(this, args, 360, 280);
        setTitle("NODO " + id + " · Campo de Datos");

        if (args.tiene("id") && args.tiene("puerto")) {
            SwingUtilities.invokeLater(this::iniciar);
        }
    }

    private static JPanel bloque(String titulo, JLabel cifra) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JLabel etiqueta = EstiloUI.texto(titulo, true);
        etiqueta.setHorizontalAlignment(JLabel.CENTER);
        panel.add(etiqueta, BorderLayout.NORTH);
        panel.add(cifra, BorderLayout.CENTER);
        return panel;
    }

    private void iniciar() {
        if (campo != null) {
            return;
        }
        String id = args.obtener("id", "");
        int puerto = args.entero("puerto", -1);
        if (id.isEmpty() || puerto < 1) {
            JOptionPane.showMessageDialog(this,
                    "Uso: --id n1 --puerto 7401 --pares n2=127.0.0.1:7402,n3=127.0.0.1:7403",
                    "Faltan datos", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<CampoDatos.Par> pares;
        try {
            pares = CampoDatos.parsearPares(args.obtener("pares", ""));
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Pares inválidos", JOptionPane.WARNING_MESSAGE);
            return;
        }
        campo = new CampoDatos(id, puerto, pares, this);
        try {
            campo.iniciar();
        } catch (Exception ex) {
            campo = null;
            JOptionPane.showMessageDialog(this, "No pude abrir el puerto " + puerto + ".\n" + ex.getMessage(),
                    "Nodo", JOptionPane.ERROR_MESSAGE);
            return;
        }
        botonIniciar.setEnabled(false);
        botonDetener.setEnabled(true);
        Runtime.getRuntime().addShutdownHook(new Thread(this::detenerSilencioso));
    }

    private void detener() {
        detenerSilencioso();
        botonIniciar.setEnabled(true);
        botonDetener.setEnabled(false);
        estado.setText("Detenido");
    }

    private void detenerSilencioso() {
        CampoDatos actual = campo;
        campo = null;
        if (actual != null) {
            actual.detener();
        }
    }

    @Override
    public void registro(String texto) {
        bitacora.agregar(texto);
    }

    @Override
    public void estado(int paresConectados, int paresEsperados, int celulas, int mensajes, int duplicados, String ultimo) {
        SwingUtilities.invokeLater(() -> {
            cifraMensajes.setText(Integer.toString(mensajes));
            cifraCiclos.setText(Integer.toString(duplicados));
            String malla = paresConectados + "/" + paresEsperados + " pares";
            if (paresEsperados > 0 && paresConectados >= paresEsperados) {
                malla = "Malla completa · " + malla;
            }
            String linea = malla + " · " + celulas + " células";
            if (ultimo != null && !ultimo.isEmpty()) {
                linea = linea + " · " + ultimo;
            }
            estado.setText(linea);
        });
    }

    public static void main(String[] args) {
        EstiloUI.aplicarLookAndFeel();
        Args argumentos = new Args(args);
        SwingUtilities.invokeLater(() -> new NodoApp(argumentos).setVisible(true));
    }
}
