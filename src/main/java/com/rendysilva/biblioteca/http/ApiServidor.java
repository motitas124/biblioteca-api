package com.rendysilva.biblioteca.http;

import com.rendysilva.biblioteca.modelo.Libro;
import com.rendysilva.biblioteca.modelo.Prestamo;
import com.rendysilva.biblioteca.modelo.Usuario;
import com.rendysilva.biblioteca.servicio.BibliotecaServicio;
import com.rendysilva.biblioteca.servicio.ErrorNegocio;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP basado en com.sun.net.httpserver (incluido en el JDK).
 * Cada recurso tiene un manejador que traduce la peticion a una llamada
 * del servicio y la respuesta a JSON con el codigo HTTP adecuado.
 */
public class ApiServidor {

    private final BibliotecaServicio servicio;
    private final HttpServer servidor;

    public ApiServidor(BibliotecaServicio servicio, int puerto) throws IOException {
        this.servicio = servicio;
        this.servidor = HttpServer.create(new InetSocketAddress(puerto), 0);
        servidor.setExecutor(Executors.newFixedThreadPool(8));
        servidor.createContext("/api/libros", this::libros);
        servidor.createContext("/api/usuarios", this::usuarios);
        servidor.createContext("/api/prestamos", this::prestamos);
        servidor.createContext("/api/salud", ex -> responder(ex, 200, Map.of("estado", "ok")));
    }

    public void iniciar() {
        servidor.start();
    }

    public void detener() {
        servidor.stop(0);
    }

    public int puerto() {
        return servidor.getAddress().getPort();
    }

    // GET /api/libros?q=texto&disponibles=true | GET /api/libros/{id}
    // POST /api/libros | DELETE /api/libros/{id}
    private void libros(HttpExchange ex) throws IOException {
        manejar(ex, () -> {
            Long id = idDeRuta(ex, "/api/libros");
            String metodo = ex.getRequestMethod();
            if (id == null && metodo.equals("GET")) {
                Map<String, String> q = parametros(ex);
                List<Libro> lista = servicio.listarLibros(q.get("q"), "true".equals(q.get("disponibles")));
                return new Respuesta(200, lista.stream().map(Libro::aMapa).toList());
            }
            if (id == null && metodo.equals("POST")) {
                Map<String, Object> c = cuerpo(ex);
                Libro libro = servicio.crearLibro(texto(c, "isbn", false), texto(c, "titulo", true),
                        texto(c, "autor", true), entero(c, "anio", 0), entero(c, "ejemplares", 1));
                return new Respuesta(201, libro.aMapa());
            }
            if (id != null && metodo.equals("GET")) return new Respuesta(200, servicio.obtenerLibro(id).aMapa());
            if (id != null && metodo.equals("DELETE")) {
                servicio.eliminarLibro(id);
                return new Respuesta(204, null);
            }
            return metodoNoPermitido();
        });
    }

    // GET /api/usuarios | GET /api/usuarios/{id} | GET /api/usuarios/{id}/prestamos | POST /api/usuarios
    private void usuarios(HttpExchange ex) throws IOException {
        manejar(ex, () -> {
            String ruta = ex.getRequestURI().getPath();
            String metodo = ex.getRequestMethod();
            if (ruta.matches("/api/usuarios/\\d+/prestamos") && metodo.equals("GET")) {
                long id = Long.parseLong(ruta.split("/")[3]);
                servicio.obtenerUsuario(id);
                return new Respuesta(200, servicio.prestamosDeUsuario(id).stream()
                        .map(p -> p.aMapa(servicio.hoy())).toList());
            }
            Long id = idDeRuta(ex, "/api/usuarios");
            if (id == null && metodo.equals("GET"))
                return new Respuesta(200, servicio.listarUsuarios().stream().map(Usuario::aMapa).toList());
            if (id == null && metodo.equals("POST")) {
                Map<String, Object> c = cuerpo(ex);
                return new Respuesta(201, servicio.crearUsuario(texto(c, "nombre", true), texto(c, "correo", true)).aMapa());
            }
            if (id != null && metodo.equals("GET")) return new Respuesta(200, servicio.obtenerUsuario(id).aMapa());
            return metodoNoPermitido();
        });
    }

    // GET /api/prestamos?vencidos=true | POST /api/prestamos | POST /api/prestamos/{id}/devolucion
    private void prestamos(HttpExchange ex) throws IOException {
        manejar(ex, () -> {
            String ruta = ex.getRequestURI().getPath();
            String metodo = ex.getRequestMethod();
            if (ruta.matches("/api/prestamos/\\d+/devolucion") && metodo.equals("POST")) {
                Prestamo p = servicio.devolver(Long.parseLong(ruta.split("/")[3]));
                return new Respuesta(200, p.aMapa(servicio.hoy()));
            }
            if (ruta.equals("/api/prestamos") && metodo.equals("GET")) {
                boolean vencidos = "true".equals(parametros(ex).get("vencidos"));
                return new Respuesta(200, servicio.listarPrestamos(vencidos).stream()
                        .map(p -> p.aMapa(servicio.hoy())).toList());
            }
            if (ruta.equals("/api/prestamos") && metodo.equals("POST")) {
                Map<String, Object> c = cuerpo(ex);
                Prestamo p = servicio.prestar(entero(c, "libroId", -1), entero(c, "usuarioId", -1));
                return new Respuesta(201, p.aMapa(servicio.hoy()));
            }
            return metodoNoPermitido();
        });
    }

    // ---------- utilidades ----------

    private record Respuesta(int codigo, Object cuerpo) {}

    @FunctionalInterface
    private interface Accion {
        Respuesta ejecutar() throws IOException;
    }

    /** Centraliza el manejo de errores: cada excepcion se convierte en un codigo HTTP. */
    private void manejar(HttpExchange ex, Accion accion) throws IOException {
        try {
            Respuesta r = accion.ejecutar();
            responder(ex, r.codigo(), r.cuerpo());
        } catch (ErrorNegocio e) {
            responder(ex, e.codigoHttp(), Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            responder(ex, 400, Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            e.printStackTrace();
            responder(ex, 500, Map.of("error", "Error interno del servidor"));
        }
    }

    private static Respuesta metodoNoPermitido() {
        return new Respuesta(405, Map.of("error", "Metodo o ruta no soportados"));
    }

    private static void responder(HttpExchange ex, int codigo, Object cuerpo) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        if (cuerpo == null) {
            ex.sendResponseHeaders(codigo, -1);
        } else {
            byte[] bytes = Json.escribir(cuerpo).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(codigo, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
        ex.close();
    }

    private static Long idDeRuta(HttpExchange ex, String base) {
        String ruta = ex.getRequestURI().getPath();
        if (ruta.equals(base) || ruta.equals(base + "/")) return null;
        String resto = ruta.substring(base.length() + 1);
        if (!resto.matches("\\d+")) throw ErrorNegocio.noEncontrado("Ruta no encontrada: " + ruta);
        return Long.parseLong(resto);
    }

    private static Map<String, String> parametros(HttpExchange ex) {
        Map<String, String> mapa = new HashMap<>();
        String query = ex.getRequestURI().getRawQuery();
        if (query == null) return mapa;
        for (String par : query.split("&")) {
            String[] kv = par.split("=", 2);
            mapa.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return mapa;
    }

    private static Map<String, Object> cuerpo(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return Json.leerObjeto(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String texto(Map<String, Object> c, String clave, boolean obligatorio) {
        Object v = c.get(clave);
        if (v == null && obligatorio) throw new IllegalArgumentException("Falta el campo '" + clave + "'");
        return v == null ? null : v.toString();
    }

    private static int entero(Map<String, Object> c, String clave, int defecto) {
        Object v = c.get(clave);
        if (v == null) {
            if (defecto < 0) throw new IllegalArgumentException("Falta el campo '" + clave + "'");
            return defecto;
        }
        if (v instanceof Number n) return n.intValue();
        throw new IllegalArgumentException("El campo '" + clave + "' debe ser numerico");
    }
}
