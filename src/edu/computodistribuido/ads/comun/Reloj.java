package edu.computodistribuido.ads.comun;

import java.text.SimpleDateFormat;
import java.util.Date;

/** Hora local para bitácoras. Un formato nuevo por llamada: SimpleDateFormat no es seguro entre hilos. */
public final class Reloj {
    private Reloj() {
    }

    public static String ahora() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }
}
