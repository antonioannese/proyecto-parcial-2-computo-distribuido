package edu.computodistribuido.ads.protocolo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trama ADS-1, una línea UTF-8.
 *
 * ADS1|TIPO|clave=valor|clave=valor
 *
 * TIPO es HELLO, HELLO_OK o DATO. Los DATO se difunden; el resto solo
 * vive en el enlace entre una célula (o un nodo) y su vecino.
 */
public final class Mensaje {
    public static final String PREFIJO = "ADS1";
    public static final String HELLO = "HELLO";
    public static final String HELLO_OK = "HELLO_OK";
    public static final String DATO = "DATO";

    private final String tipo;
    private final Map<String, String> campos;

    public Mensaje(String tipo) {
        if (tipo == null || tipo.trim().isEmpty()) {
            throw new IllegalArgumentException("Tipo vacío.");
        }
        this.tipo = tipo.trim();
        this.campos = new LinkedHashMap<String, String>();
    }

    private Mensaje(String tipo, Map<String, String> campos) {
        this.tipo = tipo;
        this.campos = campos;
    }

    public String getTipo() {
        return tipo;
    }

    public Mensaje con(String clave, String valor) {
        campos.put(clave, valor == null ? "" : valor);
        return this;
    }

    public String get(String clave) {
        String valor = campos.get(clave);
        return valor == null ? "" : valor;
    }

    public int getEntero(String clave, int defecto) {
        try {
            String texto = get(clave);
            if (texto.isEmpty()) {
                return defecto;
            }
            return Integer.parseInt(texto);
        } catch (NumberFormatException ex) {
            return defecto;
        }
    }

    public Map<String, String> getCampos() {
        return Collections.unmodifiableMap(campos);
    }

    public Mensaje copia() {
        return new Mensaje(tipo, new LinkedHashMap<String, String>(campos));
    }

    public String serializar() {
        StringBuilder sb = new StringBuilder();
        sb.append(PREFIJO).append('|').append(tipo);
        for (Map.Entry<String, String> entrada : campos.entrySet()) {
            sb.append('|')
                    .append(escapar(entrada.getKey()))
                    .append('=')
                    .append(escapar(entrada.getValue()));
        }
        return sb.toString();
    }

    public static Mensaje parsear(String linea) {
        if (linea == null || linea.trim().isEmpty()) {
            throw new IllegalArgumentException("Línea vacía.");
        }
        String[] partes = linea.split("\\|", -1);
        if (partes.length < 2 || !PREFIJO.equals(partes[0])) {
            throw new IllegalArgumentException("Trama ajena al protocolo ADS-1.");
        }
        String tipo = partes[1];
        if (!HELLO.equals(tipo) && !HELLO_OK.equals(tipo) && !DATO.equals(tipo)) {
            throw new IllegalArgumentException("Tipo desconocido: " + tipo);
        }
        Map<String, String> campos = new LinkedHashMap<String, String>();
        for (int i = 2; i < partes.length; i++) {
            String parte = partes[i];
            if (parte.isEmpty()) {
                continue;
            }
            int igual = parte.indexOf('=');
            if (igual <= 0) {
                throw new IllegalArgumentException("Campo mal formado.");
            }
            campos.put(desescapar(parte.substring(0, igual)), desescapar(parte.substring(igual + 1)));
        }
        return new Mensaje(tipo, campos);
    }

    private static String escapar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        return texto
                .replace("\\", "\\\\")
                .replace("|", "\\p")
                .replace("=", "\\e")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String desescapar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '\\' && i + 1 < texto.length()) {
                char n = texto.charAt(++i);
                switch (n) {
                    case 'p':
                        out.append('|');
                        break;
                    case 'e':
                        out.append('=');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case '\\':
                        out.append('\\');
                        break;
                    default:
                        out.append(n);
                        break;
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return serializar();
    }
}
