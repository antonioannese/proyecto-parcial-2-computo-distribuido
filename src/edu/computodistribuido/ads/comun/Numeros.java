package edu.computodistribuido.ads.comun;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Operandos y resultados. Acepta coma o punto; el protocolo siempre viaja con punto. */
public final class Numeros {
    private static final DecimalFormat FORMATO;

    static {
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(Locale.US);
        FORMATO = new DecimalFormat("0.##########", simbolos);
        FORMATO.setGroupingUsed(false);
    }

    private Numeros() {
    }

    public static double parsear(String texto) {
        if (texto == null) {
            throw new NumberFormatException("Valor nulo.");
        }
        String limpio = texto.trim().replace(" ", "").replace(',', '.');
        if (limpio.isEmpty()) {
            throw new NumberFormatException("Valor vacío.");
        }
        return Double.parseDouble(limpio);
    }

    public static String formatear(double valor) {
        if (Double.isNaN(valor) || Double.isInfinite(valor)) {
            return String.valueOf(valor);
        }
        synchronized (FORMATO) {
            return FORMATO.format(valor);
        }
    }
}
