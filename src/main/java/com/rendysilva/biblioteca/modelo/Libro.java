package com.rendysilva.biblioteca.modelo;

import java.util.LinkedHashMap;
import java.util.Map;

/** Libro del catalogo. ejemplaresDisponibles nunca supera ejemplaresTotales. */
public record Libro(long id, String isbn, String titulo, String autor, int anio,
                    int ejemplaresTotales, int ejemplaresDisponibles) {

    public Libro {
        if (titulo == null || titulo.isBlank()) throw new IllegalArgumentException("El titulo es obligatorio");
        if (autor == null || autor.isBlank()) throw new IllegalArgumentException("El autor es obligatorio");
        if (ejemplaresTotales < 1) throw new IllegalArgumentException("Debe haber al menos un ejemplar");
        if (ejemplaresDisponibles < 0 || ejemplaresDisponibles > ejemplaresTotales)
            throw new IllegalArgumentException("Ejemplares disponibles fuera de rango");
    }

    public Libro conDisponibles(int disponibles) {
        return new Libro(id, isbn, titulo, autor, anio, ejemplaresTotales, disponibles);
    }

    public boolean disponible() {
        return ejemplaresDisponibles > 0;
    }

    public Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("isbn", isbn);
        m.put("titulo", titulo);
        m.put("autor", autor);
        m.put("anio", anio);
        m.put("ejemplaresTotales", ejemplaresTotales);
        m.put("ejemplaresDisponibles", ejemplaresDisponibles);
        return m;
    }
}
