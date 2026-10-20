package edu.computodistribuido.ads.comun;

import java.util.LinkedHashMap;
import java.util.Map;

/** Argumentos --clave valor. Nada del tamaño del sistema va fijo en el código. */
public final class Args {
    private final Map<String, String> valores = new LinkedHashMap<String, String>();

    public Args(String[] args) {
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            String token = args[i];
            if (token == null || !token.startsWith("--")) {
                continue;
            }
            String clave = token.substring(2);
            String valor = "true";
            if (i + 1 < args.length && args[i + 1] != null && !args[i + 1].startsWith("--")) {
                valor = args[++i];
            }
            valores.put(clave, valor);
        }
    }

    public boolean tiene(String clave) {
        return valores.containsKey(clave);
    }

    public String obtener(String clave, String defecto) {
        String valor = valores.get(clave);
        if (valor == null || valor.trim().isEmpty()) {
            return defecto;
        }
        return valor.trim();
    }

    public int entero(String clave, int defecto) {
        try {
            return Integer.parseInt(obtener(clave, Integer.toString(defecto)));
        } catch (NumberFormatException ex) {
            return defecto;
        }
    }
}
