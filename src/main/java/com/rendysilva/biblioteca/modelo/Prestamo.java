package com.rendysilva.biblioteca.modelo;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

public record Prestamo(long id, long libroId, long usuarioId, LocalDate fechaPrestamo,
                       LocalDate fechaLimite, LocalDate fechaDevolucion) {

    /** Multa por dia de atraso, en centavos de dolar. */
    public static final int MULTA_DIARIA_CENTAVOS = 25;

    public boolean activo() {
        return fechaDevolucion == null;
    }

    public boolean vencido(LocalDate hoy) {
        return activo() && hoy.isAfter(fechaLimite);
    }

    public long diasDeAtraso(LocalDate hoy) {
        LocalDate referencia = activo() ? hoy : fechaDevolucion;
        return Math.max(0, ChronoUnit.DAYS.between(fechaLimite, referencia));
    }

    public Prestamo devolver(LocalDate fecha) {
        return new Prestamo(id, libroId, usuarioId, fechaPrestamo, fechaLimite, fecha);
    }

    public Map<String, Object> aMapa(LocalDate hoy) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("libroId", libroId);
        m.put("usuarioId", usuarioId);
        m.put("fechaPrestamo", fechaPrestamo.toString());
        m.put("fechaLimite", fechaLimite.toString());
        m.put("fechaDevolucion", fechaDevolucion == null ? null : fechaDevolucion.toString());
        m.put("estado", activo() ? (vencido(hoy) ? "VENCIDO" : "ACTIVO") : "DEVUELTO");
        m.put("multa", String.format(java.util.Locale.US, "%.2f", diasDeAtraso(hoy) * MULTA_DIARIA_CENTAVOS / 100.0));
        return m;
    }
}
