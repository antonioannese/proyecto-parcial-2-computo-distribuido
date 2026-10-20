package edu.computodistribuido.ads.comun;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/** Apariencia común. El look and feel cruzado evita que macOS tape el texto de los botones. */
public final class EstiloUI {
    public static final Color FONDO = new Color(244, 246, 248);
    public static final Color TEXTO = new Color(24, 28, 34);
    public static final Color TEXTO_SUAVE = new Color(70, 78, 90);
    public static final Color BORDE = new Color(190, 198, 208);
    public static final Color ACIERTO = new Color(20, 130, 70);
    public static final Color ERROR = new Color(176, 42, 42);
    public static final Color NODO = new Color(176, 42, 48);

    private EstiloUI() {
    }

    public static void aplicarLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignorada) {
            // Se sigue con el look and feel activo.
        }
        UIManager.put("Label.foreground", TEXTO);
        UIManager.put("Panel.background", FONDO);
    }

    public static JPanel banner(String titulo, String subtitulo, Color color) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(color);
        panel.setBorder(new EmptyBorder(8, 10, 8, 10));
        JLabel grande = new JLabel(titulo);
        grande.setForeground(Color.WHITE);
        grande.setFont(new Font("SansSerif", Font.BOLD, 16));
        panel.add(grande, BorderLayout.NORTH);
        if (subtitulo != null && !subtitulo.isEmpty()) {
            JLabel chico = new JLabel(subtitulo);
            chico.setForeground(new Color(255, 255, 255, 230));
            chico.setFont(new Font("SansSerif", Font.PLAIN, 12));
            panel.add(chico, BorderLayout.SOUTH);
        }
        return panel;
    }

    public static JLabel cifra(String texto, Color color, int puntos) {
        JLabel label = new JLabel(texto, JLabel.CENTER);
        label.setForeground(color);
        label.setFont(new Font("SansSerif", Font.BOLD, puntos));
        label.setOpaque(false);
        return label;
    }

    public static JLabel texto(String contenido, boolean suave) {
        JLabel label = new JLabel(contenido);
        label.setForeground(suave ? TEXTO_SUAVE : TEXTO);
        label.setFont(new Font("SansSerif", suave ? Font.PLAIN : Font.BOLD, 12));
        return label;
    }

    public static JTextField campo(int columnas) {
        JTextField campo = new JTextField(columnas);
        campo.setFont(new Font("SansSerif", Font.BOLD, 18));
        campo.setForeground(TEXTO);
        campo.setBackground(Color.WHITE);
        campo.setCaretColor(TEXTO);
        campo.setHorizontalAlignment(JTextField.CENTER);
        campo.setBorder(new CompoundBorder(new LineBorder(new Color(120, 130, 145), 1), new EmptyBorder(4, 6, 4, 6)));
        return campo;
    }

    public static JButton boton(String titulo, Color color) {
        JButton boton = new JButton(titulo);
        boton.setUI(new BasicButtonUI());
        boton.setBackground(color);
        boton.setForeground(Color.WHITE);
        boton.setOpaque(true);
        boton.setContentAreaFilled(true);
        boton.setBorderPainted(true);
        boton.setFocusPainted(false);
        boton.setFont(new Font("SansSerif", Font.BOLD, 12));
        boton.setBorder(new CompoundBorder(new LineBorder(color.darker(), 1), new EmptyBorder(6, 10, 6, 10)));
        return boton;
    }

    public static JTextArea area() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Monospaced", Font.PLAIN, 11));
        area.setForeground(TEXTO);
        area.setBackground(new Color(252, 252, 253));
        area.setMargin(new Insets(6, 6, 6, 6));
        return area;
    }

    public static JScrollPane scroll(JComponent componente) {
        JScrollPane scroll = new JScrollPane(componente);
        scroll.setBorder(new LineBorder(BORDE, 1));
        scroll.getViewport().setBackground(Color.WHITE);
        return scroll;
    }

    public static void geometria(JFrame frame, Args args, int ancho, int alto) {
        frame.setSize(args.entero("ancho", ancho), args.entero("alto", alto));
        if (args.tiene("x") && args.tiene("y")) {
            frame.setLocation(args.entero("x", 40), args.entero("y", 40));
        } else {
            frame.setLocationRelativeTo(null);
        }
    }

    /** Bitácora en pantalla y, si hay identificador, una copia en logs/. */
    public static final class Bitacora {
        private final JTextArea area = area();
        private final Path archivo;

        public Bitacora(String id) {
            Path ruta = null;
            if (id != null && !id.isEmpty()) {
                try {
                    Path dir = Paths.get("logs");
                    Files.createDirectories(dir);
                    ruta = dir.resolve(id.replaceAll("[^a-zA-Z0-9_-]", "_") + ".log");
                } catch (IOException ex) {
                    ruta = null;
                }
            }
            this.archivo = ruta;
        }

        public JTextArea getArea() {
            return area;
        }

        public void agregar(final String texto) {
            final String linea = Reloj.ahora() + "  " + texto;
            if (archivo != null) {
                try (BufferedWriter writer = Files.newBufferedWriter(
                        archivo, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    writer.write(linea);
                    writer.newLine();
                } catch (IOException ignorada) {
                    // La ventana sigue siendo la evidencia principal.
                }
            }
            Runnable pintar = new Runnable() {
                @Override
                public void run() {
                    area.append(linea + "\n");
                    if (area.getDocument().getLength() > 80_000) {
                        area.replaceRange("", 0, 20_000);
                    }
                    area.setCaretPosition(area.getDocument().getLength());
                }
            };
            if (SwingUtilities.isEventDispatchThread()) {
                pintar.run();
            } else {
                SwingUtilities.invokeLater(pintar);
            }
        }
    }
}
