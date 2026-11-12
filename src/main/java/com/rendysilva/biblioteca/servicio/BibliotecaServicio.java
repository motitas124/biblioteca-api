package com.rendysilva.biblioteca.servicio;

import com.rendysilva.biblioteca.modelo.Libro;
import com.rendysilva.biblioteca.modelo.Prestamo;
import com.rendysilva.biblioteca.modelo.Usuario;
import com.rendysilva.biblioteca.repositorio.Repositorio;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Reglas de negocio de la biblioteca. */
public class BibliotecaServicio {

    public static final int DIAS_PRESTAMO = 14;
    public static final int MAX_PRESTAMOS_POR_USUARIO = 3;

    private final Repositorio<Libro> libros = new Repositorio<>(Libro::id);
    private final Repositorio<Usuario> usuarios = new Repositorio<>(Usuario::id);
    private final Repositorio<Prestamo> prestamos = new Repositorio<>(Prestamo::id);
    private final Clock reloj;

    public BibliotecaServicio(Clock reloj) {
        this.reloj = reloj;
    }

    public LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    // ---------- Libros ----------

    public Libro crearLibro(String isbn, String titulo, String autor, int anio, int ejemplares) {
        boolean repetido = isbn != null && !isbn.isBlank()
                && libros.todos().stream().anyMatch(l -> isbn.equals(l.isbn()));
        if (repetido) throw ErrorNegocio.conflicto("Ya existe un libro con ISBN " + isbn);
        return libros.crear(id -> new Libro(id, isbn, titulo, autor, anio, ejemplares, ejemplares));
    }

    public List<Libro> listarLibros(String busqueda, boolean soloDisponibles) {
        String q = busqueda == null ? "" : busqueda.toLowerCase(Locale.ROOT);
        return libros.todos().stream()
                .filter(l -> q.isEmpty()
                        || l.titulo().toLowerCase(Locale.ROOT).contains(q)
                        || l.autor().toLowerCase(Locale.ROOT).contains(q))
                .filter(l -> !soloDisponibles || l.disponible())
                .toList();
    }

    public Libro obtenerLibro(long id) {
        return libros.buscar(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Libro " + id + " no encontrado"));
    }

    public void eliminarLibro(long id) {
        Libro libro = obtenerLibro(id);
        if (libro.ejemplaresDisponibles() < libro.ejemplaresTotales())
            throw ErrorNegocio.conflicto("No se puede eliminar un libro con prestamos activos");
        libros.eliminar(id);
    }

    // ---------- Usuarios ----------

    public Usuario crearUsuario(String nombre, String correo) {
        boolean repetido = usuarios.todos().stream().anyMatch(u -> u.correo().equalsIgnoreCase(correo));
        if (repetido) throw ErrorNegocio.conflicto("El correo ya esta registrado");
        return usuarios.crear(id -> new Usuario(id, nombre, correo));
    }

    public List<Usuario> listarUsuarios() {
        return usuarios.todos();
    }

    public Usuario obtenerUsuario(long id) {
        return usuarios.buscar(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Usuario " + id + " no encontrado"));
    }

    // ---------- Prestamos ----------

    /** Sincronizado para que dos peticiones simultaneas no presten el ultimo ejemplar dos veces. */
    public synchronized Prestamo prestar(long libroId, long usuarioId) {
        Libro libro = obtenerLibro(libroId);
        obtenerUsuario(usuarioId);

        if (!libro.disponible())
            throw ErrorNegocio.conflicto("No hay ejemplares disponibles de \"" + libro.titulo() + "\"");

        List<Prestamo> delUsuario = prestamosDeUsuario(usuarioId);
        if (delUsuario.stream().anyMatch(p -> p.vencido(hoy())))
            throw ErrorNegocio.conflicto("El usuario tiene prestamos vencidos sin devolver");
        if (delUsuario.stream().filter(Prestamo::activo).count() >= MAX_PRESTAMOS_POR_USUARIO)
            throw ErrorNegocio.conflicto("El usuario alcanzo el maximo de " + MAX_PRESTAMOS_POR_USUARIO + " prestamos");

        libros.actualizar(libro.conDisponibles(libro.ejemplaresDisponibles() - 1));
        LocalDate hoy = hoy();
        return prestamos.crear(id -> new Prestamo(id, libroId, usuarioId, hoy, hoy.plusDays(DIAS_PRESTAMO), null));
    }

    public synchronized Prestamo devolver(long prestamoId) {
        Prestamo prestamo = prestamos.buscar(prestamoId)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("Prestamo " + prestamoId + " no encontrado"));
        if (!prestamo.activo()) throw ErrorNegocio.conflicto("El prestamo ya fue devuelto");

        Libro libro = obtenerLibro(prestamo.libroId());
        libros.actualizar(libro.conDisponibles(libro.ejemplaresDisponibles() + 1));
        return prestamos.actualizar(prestamo.devolver(hoy()));
    }

    public List<Prestamo> listarPrestamos(boolean soloVencidos) {
        return prestamos.todos().stream()
                .filter(p -> !soloVencidos || p.vencido(hoy()))
                .toList();
    }

    public List<Prestamo> prestamosDeUsuario(long usuarioId) {
        return prestamos.todos().stream().filter(p -> p.usuarioId() == usuarioId).toList();
    }
}
