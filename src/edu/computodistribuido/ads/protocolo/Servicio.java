package edu.computodistribuido.ads.protocolo;

import java.awt.Color;

/** Servicio aritmético que una célula servidora anuncia y atiende. */
public enum Servicio {
    SUMA("SUM", "Suma", "YO SÉ SUMAR", "+", new Color(196, 92, 106)),
    RESTA("SUB", "Resta", "YO SÉ RESTAR", "−", new Color(47, 102, 186)),
    MULTIPLICACION("MUL", "Multiplicación", "YO SÉ MULTIPLICAR", "×", new Color(36, 140, 86)),
    DIVISION("DIV", "División", "YO SÉ DIVIDIR", "÷", new Color(184, 122, 20));

    private final String prefijo;
    private final String nombre;
    private final String lema;
    private final String simbolo;
    private final Color color;

    Servicio(String prefijo, String nombre, String lema, String simbolo, Color color) {
        this.prefijo = prefijo;
        this.nombre = nombre;
        this.lema = lema;
        this.simbolo = simbolo;
        this.color = color;
    }

    public String getPrefijo() {
        return prefijo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getLema() {
        return lema;
    }

    public String getSimbolo() {
        return simbolo;
    }

    public Color getColor() {
        return color;
    }

    public CodigoContenido solicitud() {
        return CodigoContenido.desde(prefijo + "_REQ");
    }

    public CodigoContenido respuesta() {
        return CodigoContenido.desde(prefijo + "_RES");
    }

    public CodigoContenido reclamo() {
        return CodigoContenido.desde(prefijo + "_CLAIM");
    }

    public static Servicio desde(String texto) {
        if (texto == null) {
            throw new IllegalArgumentException("Servicio vacío.");
        }
        return Servicio.valueOf(texto.trim().toUpperCase());
    }
}
