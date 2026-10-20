package edu.computodistribuido.ads.servidor;

import edu.computodistribuido.ads.comun.Numeros;
import edu.computodistribuido.ads.protocolo.Servicio;

/** La aritmética vive solo en la célula servidora. El Campo de Datos no calcula. */
public final class MotorCalculo {
    public static final class Resultado {
        public final boolean exito;
        public final double valor;
        public final String detalle;

        private Resultado(boolean exito, double valor, String detalle) {
            this.exito = exito;
            this.valor = valor;
            this.detalle = detalle;
        }
    }

    private MotorCalculo() {
    }

    public static Resultado resolver(Servicio servicio, double a, double b) {
        switch (servicio) {
            case SUMA:
                return ok(a + b);
            case RESTA:
                return ok(a - b);
            case MULTIPLICACION:
                return ok(a * b);
            case DIVISION:
                if (b == 0.0d) {
                    return error("División entre cero.");
                }
                return ok(a / b);
            default:
                return error("Servicio no soportado.");
        }
    }

    public static String expresion(Servicio servicio, double a, double b) {
        return Numeros.formatear(a) + " " + servicio.getSimbolo() + " " + Numeros.formatear(b);
    }

    private static Resultado ok(double valor) {
        return new Resultado(true, valor, "ok");
    }

    private static Resultado error(String detalle) {
        return new Resultado(false, Double.NaN, detalle);
    }
}
