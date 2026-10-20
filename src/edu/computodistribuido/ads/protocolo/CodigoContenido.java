package edu.computodistribuido.ads.protocolo;

/**
 * Códigos de Contenido que circulan por el Campo de Datos.
 * El nodo no los interpreta: solo los transporta. Cada célula decide.
 */
public enum CodigoContenido {
    SUM_REQ,
    SUM_RES,
    SUM_CLAIM,
    SUB_REQ,
    SUB_RES,
    SUB_CLAIM,
    MUL_REQ,
    MUL_RES,
    MUL_CLAIM,
    DIV_REQ,
    DIV_RES,
    DIV_CLAIM,
    PRESENCE,
    WITHDRAW;

    public boolean esSolicitud() {
        return name().endsWith("_REQ");
    }

    public boolean esRespuesta() {
        return name().endsWith("_RES");
    }

    public boolean esReclamo() {
        return name().endsWith("_CLAIM");
    }

    public static CodigoContenido desde(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        try {
            return CodigoContenido.valueOf(texto.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
