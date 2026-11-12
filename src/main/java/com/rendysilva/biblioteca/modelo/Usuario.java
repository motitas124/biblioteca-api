package com.rendysilva.biblioteca.modelo;

import java.util.LinkedHashMap;
import java.util.Map;

public record Usuario(long id, String nombre, String correo) {

    public Usuario {
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("El nombre es obligatorio");
        if (correo == null || !correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new IllegalArgumentException("Correo invalido");
    }

    public Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("nombre", nombre);
        m.put("correo", correo);
        return m;
    }
}
