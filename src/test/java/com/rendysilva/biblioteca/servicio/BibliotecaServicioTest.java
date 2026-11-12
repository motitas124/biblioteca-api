package com.rendysilva.biblioteca.servicio;

import com.rendysilva.biblioteca.http.Json;
import com.rendysilva.biblioteca.modelo.Libro;
import com.rendysilva.biblioteca.modelo.Prestamo;
import com.rendysilva.biblioteca.modelo.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BibliotecaServicioTest {

    /** Reloj que se puede adelantar para simular el paso de los dias. */
    private static final class RelojMovil extends Clock {
        private Instant ahora = Instant.parse("2026-03-01T10:00:00Z");
        void avanzarDias(int dias) { ahora = ahora.plusSeconds(dias * 86_400L); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zona) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    private RelojMovil reloj;
    private BibliotecaServicio servicio;
    private Libro libro;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        reloj = new RelojMovil();
        servicio = new BibliotecaServicio(reloj);
        libro = servicio.crearLibro("111", "Rayuela", "Julio Cortazar", 1963, 1);
        usuario = servicio.crearUsuario("Ana", "ana@correo.com");
    }

    @Test
    void prestarReduceEjemplaresYDevolverLosRestaura() {
        Prestamo p = servicio.prestar(libro.id(), usuario.id());
        assertEquals(0, servicio.obtenerLibro(libro.id()).ejemplaresDisponibles());

        servicio.devolver(p.id());
        assertEquals(1, servicio.obtenerLibro(libro.id()).ejemplaresDisponibles());
    }

    @Test
    void noPrestaSiNoHayEjemplares() {
        Usuario otro = servicio.crearUsuario("Luis", "luis@correo.com");
        servicio.prestar(libro.id(), usuario.id());
        ErrorNegocio e = assertThrows(ErrorNegocio.class, () -> servicio.prestar(libro.id(), otro.id()));
        assertEquals(409, e.codigoHttp());
    }

    @Test
    void limitaPrestamosPorUsuario() {
        Libro muchos = servicio.crearLibro("222", "Libro popular", "Autor", 2020, 10);
        for (int i = 0; i < BibliotecaServicio.MAX_PRESTAMOS_POR_USUARIO; i++)
            servicio.prestar(muchos.id(), usuario.id());
        assertThrows(ErrorNegocio.class, () -> servicio.prestar(muchos.id(), usuario.id()));
    }

    @Test
    void calculaMultaPorAtraso() {
        Prestamo p = servicio.prestar(libro.id(), usuario.id());
        reloj.avanzarDias(BibliotecaServicio.DIAS_PRESTAMO + 4);

        assertTrue(p.vencido(servicio.hoy()));
        assertEquals(4, p.diasDeAtraso(servicio.hoy()));
        assertEquals("1.00", p.aMapa(servicio.hoy()).get("multa"));
        assertEquals(1, servicio.listarPrestamos(true).size());
    }

    @Test
    void usuarioConPrestamoVencidoNoPuedePedirOtro() {
        Libro otroLibro = servicio.crearLibro("333", "Ficciones", "Borges", 1944, 2);
        servicio.prestar(libro.id(), usuario.id());
        reloj.avanzarDias(30);
        assertThrows(ErrorNegocio.class, () -> servicio.prestar(otroLibro.id(), usuario.id()));
    }

    @Test
    void noSePuedeDevolverDosVeces() {
        Prestamo p = servicio.prestar(libro.id(), usuario.id());
        servicio.devolver(p.id());
        assertThrows(ErrorNegocio.class, () -> servicio.devolver(p.id()));
    }

    @Test
    void validaDatosDeEntrada() {
        assertThrows(IllegalArgumentException.class, () -> servicio.crearUsuario("X", "correo-invalido"));
        assertThrows(IllegalArgumentException.class, () -> servicio.crearLibro("9", " ", "Autor", 2000, 1));
        assertThrows(ErrorNegocio.class, () -> servicio.crearLibro("111", "Repetido", "Autor", 2000, 1));
        assertThrows(ErrorNegocio.class, () -> servicio.crearUsuario("Ana 2", "ANA@correo.com"));
    }

    @Test
    void buscaPorTituloOAutor() {
        servicio.crearLibro("444", "El Aleph", "Jorge Luis Borges", 1949, 1);
        assertEquals(1, servicio.listarLibros("borges", false).size());
        assertEquals(1, servicio.listarLibros("rayuela", false).size());
        servicio.prestar(libro.id(), usuario.id());
        List<Libro> disponibles = servicio.listarLibros("", true);
        assertFalse(disponibles.stream().anyMatch(l -> l.id() == libro.id()));
    }

    @Test
    void jsonIdaYVuelta() {
        String json = Json.escribir(Map.of("titulo", "Comillas \"dobles\""));
        assertEquals("{\"titulo\":\"Comillas \\\"dobles\\\"\"}", json);
        Map<String, Object> leido = Json.leerObjeto("{ \"titulo\": \"Hola\\nMundo\", \"anio\": 1999, \"activo\": true }");
        assertEquals("Hola\nMundo", leido.get("titulo"));
        assertEquals(1999L, leido.get("anio"));
        assertEquals(Boolean.TRUE, leido.get("activo"));
    }
}
