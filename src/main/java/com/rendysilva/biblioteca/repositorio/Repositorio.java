package com.rendysilva.biblioteca.repositorio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

/**
 * Repositorio generico en memoria y seguro para hilos (el servidor HTTP
 * atiende varias peticiones a la vez). Se puede reemplazar por uno que use
 * una base de datos sin tocar la capa de servicio.
 */
public class Repositorio<T> {

    private final Map<Long, T> datos = new ConcurrentHashMap<>();
    private final AtomicLong secuencia = new AtomicLong(1);
    private final ToLongFunction<T> obtenerId;

    public Repositorio(ToLongFunction<T> obtenerId) {
        this.obtenerId = obtenerId;
    }

    /** Crea una entidad nueva asignandole el siguiente id disponible. */
    public T crear(LongFunction<T> fabrica) {
        T entidad = fabrica.apply(secuencia.getAndIncrement());
        datos.put(obtenerId.applyAsLong(entidad), entidad);
        return entidad;
    }

    public T actualizar(T entidad) {
        datos.put(obtenerId.applyAsLong(entidad), entidad);
        return entidad;
    }

    public Optional<T> buscar(long id) {
        return Optional.ofNullable(datos.get(id));
    }

    public List<T> todos() {
        List<T> lista = new ArrayList<>(datos.values());
        lista.sort((a, b) -> Long.compare(obtenerId.applyAsLong(a), obtenerId.applyAsLong(b)));
        return lista;
    }

    public boolean eliminar(long id) {
        return datos.remove(id) != null;
    }
}
