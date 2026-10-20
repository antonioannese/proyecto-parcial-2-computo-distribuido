package edu.computodistribuido.ads.comun;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Identificadores que viajan en el Campo de Datos. */
public final class Huella {
    private Huella() {
    }

    /** Nueve dígitos, estables durante la vida del proceso. */
    public static String nueva() {
        int valor = ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000);
        return Integer.toString(valor);
    }

    public static String nuevoMensaje() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String corto(String id) {
        if (id == null || id.isEmpty()) {
            return "-";
        }
        return id.length() <= 8 ? id : id.substring(0, 8);
    }
}
