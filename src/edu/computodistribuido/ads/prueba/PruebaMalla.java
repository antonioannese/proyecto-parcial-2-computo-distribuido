package edu.computodistribuido.ads.prueba;

import edu.computodistribuido.ads.comun.EnlaceCelula;
import edu.computodistribuido.ads.comun.Huella;
import edu.computodistribuido.ads.comun.Reloj;
import edu.computodistribuido.ads.nodo.CampoDatos;
import edu.computodistribuido.ads.protocolo.Mensaje;
import edu.computodistribuido.ads.protocolo.Servicio;
import edu.computodistribuido.ads.servidor.NucleoServidor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Ejercita la malla, el broadcast, una sola respuesta por solicitud
 * y el descarte de ciclos. No abre ventanas.
 */
public final class PruebaMalla {
    public static void main(String[] args) throws Exception {
        int base = args.length > 0 ? Integer.parseInt(args[0]) : 17621;
        List<CampoDatos> nodos = new ArrayList<CampoDatos>();
        List<Agente> sumas = new ArrayList<Agente>();
        List<Agente> otros = new ArrayList<Agente>();
        Cable gui = null;
        int fallos = 0;
        try {
            for (int i = 1; i <= 4; i++) {
                String id = "n" + i;
                CampoDatos campo = new CampoDatos(id, base + i - 1, pares(base, id), silencio());
                campo.iniciar();
                nodos.add(campo);
            }
            esperar(() -> paresDe(nodos) == 12, 8000, "la malla no se cerró: pares=" + paresDe(nodos));
            System.out.println("Malla completa: cada nodo ve a los otros tres.");

            for (int i = 0; i < 4; i++) {
                sumas.add(new Agente("suma-" + (i + 1), Servicio.SUMA, "127.0.0.1", base + i));
            }
            otros.add(new Agente("multi-1", Servicio.MULTIPLICACION, "127.0.0.1", base));
            otros.add(new Agente("div-1", Servicio.DIVISION, "127.0.0.1", base + 1));
            esperar(() -> celulasDe(nodos) >= 6, 4000, "no se conectaron las células: " + celulasDe(nodos));

            gui = new Cable("gui-1", "127.0.0.1", base);
            String sumaId = Huella.nuevoMensaje();
            Mensaje suma = solicitud(sumaId, Servicio.SUMA, "9", "4", "gui-1");
            gui.enviar(suma);
            final Cable cliente = gui;
            esperar(() -> contar(cliente, sumaId, "SUM_RES") >= 1, 4000, "no llegó SUM_RES");
            Thread.sleep(1500);
            verificar(contar(cliente, sumaId, "SUM_RES") == 1, "llegó más de una SUM_RES: " + contar(cliente, sumaId, "SUM_RES"));
            Mensaje respuesta = primera(cliente, sumaId, "SUM_RES");
            verificar("13".equals(respuesta.get("res")), "9+4 debía ser 13 y fue " + respuesta.get("res"));
            verificar("OK".equals(respuesta.get("estado")), "estado distinto de OK");
            verificar(sumaId.equals(respuesta.get("ref")), "la respuesta no cita la solicitud");
            int emitidas = emitidas(sumas);
            verificar(emitidas == 1, "debía responder una sola célula de suma, respondieron " + emitidas);
            verificar(cedidas(sumas) == 3, "las otras tres debían ceder, cedieron " + cedidas(sumas));
            for (Agente agente : sumas) {
                verificar(agente.nucleo.getSolicitudesVistas() == 1,
                        agente.id + " vio el REQ " + agente.nucleo.getSolicitudesVistas() + " veces");
            }
            int duplicados = duplicados(nodos);
            verificar(duplicados > 0, "la malla no reportó ciclos evitados");
            System.out.println("Suma 9+4=13, una respuesta, ciclos evitados=" + duplicados);

            int vistasAntes = sumas.get(0).nucleo.getSolicitudesVistas();
            int duplicadosAntes = duplicados(nodos);
            gui.enviar(suma);
            Thread.sleep(400);
            verificar(sumas.get(0).nucleo.getSolicitudesVistas() == vistasAntes, "el REQ repetido se volvió a entregar");
            verificar(duplicados(nodos) > duplicadosAntes, "repetir el mismo id no aumentó los ciclos evitados");
            System.out.println("El mismo id se descartó en el nodo de entrada.");

            String mulId = Huella.nuevoMensaje();
            gui.enviar(solicitud(mulId, Servicio.MULTIPLICACION, "8", "7", "gui-1"));
            esperar(() -> contar(cliente, mulId, "MUL_RES") == 1, 4000, "no llegó MUL_RES");
            Thread.sleep(1200);
            verificar("56".equals(primera(cliente, mulId, "MUL_RES").get("res")), "8×7 debía ser 56");
            verificar(emitidas(sumas) == 1, "una multiplicación no debía hacer responder a la suma");
            verificar(otros.get(0).nucleo.getRespuestasEmitidas() == 1, "la célula de multiplicación no respondió");
            System.out.println("Multiplicación 8×7=56 atendida solo por su código.");

            String restaId = Huella.nuevoMensaje();
            Agente resta = new Agente("resta-1", Servicio.RESTA, "127.0.0.1", base + 2);
            otros.add(resta);
            gui.enviar(solicitud(restaId, Servicio.RESTA, "9", "4", "gui-1"));
            esperar(() -> contar(cliente, restaId, "SUB_RES") == 1, 4000, "no llegó SUB_RES");
            verificar("5".equals(primera(cliente, restaId, "SUB_RES").get("res")), "9−4 debía ser 5");
            System.out.println("Resta 9-4=5.");

            String divId = Huella.nuevoMensaje();
            gui.enviar(solicitud(divId, Servicio.DIVISION, "9", "0", "gui-1"));
            esperar(() -> contar(cliente, divId, "DIV_RES") == 1, 4000, "no llegó DIV_RES");
            Mensaje division = primera(cliente, divId, "DIV_RES");
            verificar("ERROR".equals(division.get("estado")), "división entre cero debía ser ERROR");
            verificar(division.get("detalle").toLowerCase().contains("cero"), "faltó el detalle de división entre cero");
            System.out.println("División entre cero regresa error por el Campo de Datos.");

            for (int i = 1; i < sumas.size(); i++) {
                sumas.get(i).cerrar();
            }
            Thread.sleep(400);
            String respaldoId = Huella.nuevoMensaje();
            int previas = sumas.get(0).nucleo.getRespuestasEmitidas();
            gui.enviar(solicitud(respaldoId, Servicio.SUMA, "20", "3", "gui-1"));
            esperar(() -> contar(cliente, respaldoId, "SUM_RES") == 1, 4000, "la suma sobreviviente no respondió");
            Thread.sleep(1200);
            verificar("23".equals(primera(cliente, respaldoId, "SUM_RES").get("res")), "20+3 debía ser 23");
            verificar(contar(cliente, respaldoId, "SUM_RES") == 1, "el respaldo duplicó la respuesta");
            verificar(sumas.get(0).nucleo.getRespuestasEmitidas() == previas + 1, "no respondió la célula que seguía viva");
            System.out.println("Con una sola célula de suma, 20+3=23.");
            System.out.println("PRUEBA_OK");
        } catch (Throwable ex) {
            fallos++;
            System.out.println("PRUEBA_FALLO: " + ex.getMessage());
            ex.printStackTrace(System.out);
        } finally {
            if (gui != null) {
                gui.cerrar();
            }
            for (Agente agente : sumas) {
                agente.cerrar();
            }
            for (Agente agente : otros) {
                agente.cerrar();
            }
            for (CampoDatos nodo : nodos) {
                nodo.detener();
            }
        }
        System.exit(fallos == 0 ? 0 : 1);
    }

    private static List<CampoDatos.Par> pares(int base, String propio) {
        StringBuilder texto = new StringBuilder();
        for (int i = 1; i <= 4; i++) {
            String id = "n" + i;
            if (id.equals(propio)) {
                continue;
            }
            if (texto.length() > 0) {
                texto.append(',');
            }
            texto.append(id).append("=127.0.0.1:").append(base + i - 1);
        }
        return CampoDatos.parsearPares(texto.toString());
    }

    private static CampoDatos.Oyente silencio() {
        return new CampoDatos.Oyente() {
            @Override
            public void registro(String texto) {
            }

            @Override
            public void estado(int paresConectados, int paresEsperados, int celulas, int mensajes, int duplicados, String ultimo) {
            }
        };
    }

    private static Mensaje solicitud(String id, Servicio servicio, String a, String b, String origen) {
        return new Mensaje(Mensaje.DATO)
                .con("id", id)
                .con("ref", "")
                .con("cc", servicio.solicitud().name())
                .con("origen", origen)
                .con("huella", "111111111")
                .con("op1", a)
                .con("op2", b)
                .con("res", "")
                .con("estado", "")
                .con("detalle", "")
                .con("hops", "0")
                .con("ts", Reloj.ahora());
    }

    private static int paresDe(List<CampoDatos> nodos) {
        int total = 0;
        for (CampoDatos nodo : nodos) {
            total += nodo.getParesConectados();
        }
        return total;
    }

    private static int celulasDe(List<CampoDatos> nodos) {
        int total = 0;
        for (CampoDatos nodo : nodos) {
            total += nodo.getCelulas();
        }
        return total;
    }

    private static int duplicados(List<CampoDatos> nodos) {
        int total = 0;
        for (CampoDatos nodo : nodos) {
            total += nodo.getDuplicados();
        }
        return total;
    }

    private static int emitidas(List<Agente> agentes) {
        int total = 0;
        for (Agente agente : agentes) {
            total += agente.nucleo.getRespuestasEmitidas();
        }
        return total;
    }

    private static int cedidas(List<Agente> agentes) {
        int total = 0;
        for (Agente agente : agentes) {
            total += agente.nucleo.getCedidas();
        }
        return total;
    }

    private static int contar(Cable cable, String ref, String codigo) {
        int total = 0;
        synchronized (cable.datos) {
            for (Mensaje mensaje : cable.datos) {
                if (codigo.equals(mensaje.get("cc")) && ref.equals(mensaje.get("ref"))) {
                    total++;
                }
            }
        }
        return total;
    }

    private static Mensaje primera(Cable cable, String ref, String codigo) {
        synchronized (cable.datos) {
            for (Mensaje mensaje : cable.datos) {
                if (codigo.equals(mensaje.get("cc")) && ref.equals(mensaje.get("ref"))) {
                    return mensaje;
                }
            }
        }
        return null;
    }

    private static void verificar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }

    private static void esperar(BooleanSupplier condicion, long ms, String detalle) throws InterruptedException {
        long fin = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < fin) {
            if (condicion.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError(detalle);
    }

    private static final class Agente {
        final String id;
        final NucleoServidor nucleo;
        final EnlaceCelula enlace = new EnlaceCelula();

        Agente(String id, Servicio servicio, String host, int puerto) throws Exception {
            this.id = id;
            String huella = Huella.nueva();
            this.nucleo = new NucleoServidor(id, huella, servicio, new NucleoServidor.Salida() {
                @Override
                public void enviar(Mensaje mensaje) {
                    enlace.enviar(mensaje);
                }

                @Override
                public void nota(String texto) {
                }

                @Override
                public void estado(String texto) {
                }
            });
            Mensaje hello = new Mensaje(Mensaje.HELLO)
                    .con("entidad", "CELULA")
                    .con("rol", "SERVIDOR")
                    .con("id", id)
                    .con("huella", huella)
                    .con("servicio", servicio.name());
            enlace.conectar(host, puerto, hello, new EnlaceCelula.Oyente() {
                @Override
                public void alListo(String nodoId) {
                    nucleo.alConectar(nodoId);
                }

                @Override
                public void alDato(Mensaje mensaje) {
                    nucleo.alDato(mensaje);
                }

                @Override
                public void alCierre(String motivo) {
                }
            });
        }

        void cerrar() {
            enlace.close();
            nucleo.cerrar();
        }
    }

    private static final class Cable {
        final List<Mensaje> datos = Collections.synchronizedList(new ArrayList<Mensaje>());
        final EnlaceCelula enlace = new EnlaceCelula();

        Cable(String id, String host, int puerto) throws Exception {
            Mensaje hello = new Mensaje(Mensaje.HELLO)
                    .con("entidad", "CELULA")
                    .con("rol", "GUI")
                    .con("id", id)
                    .con("huella", "222222222")
                    .con("servicio", "");
            enlace.conectar(host, puerto, hello, new EnlaceCelula.Oyente() {
                @Override
                public void alListo(String nodoId) {
                }

                @Override
                public void alDato(Mensaje mensaje) {
                    datos.add(mensaje);
                }

                @Override
                public void alCierre(String motivo) {
                }
            });
        }

        void enviar(Mensaje mensaje) {
            enlace.enviar(mensaje);
        }

        void cerrar() {
            enlace.close();
        }
    }
}
