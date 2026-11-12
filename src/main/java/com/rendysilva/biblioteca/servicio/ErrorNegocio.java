package com.rendysilva.biblioteca.servicio;

/** Error de negocio que se traduce a un codigo HTTP concreto. */
public class ErrorNegocio extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int codigoHttp;

    public ErrorNegocio(int codigoHttp, String mensaje) {
        super(mensaje);
        this.codigoHttp = codigoHttp;
    }

    public int codigoHttp() {
        return codigoHttp;
    }

    public static ErrorNegocio noEncontrado(String mensaje) {
        return new ErrorNegocio(404, mensaje);
    }

    public static ErrorNegocio conflicto(String mensaje) {
        return new ErrorNegocio(409, mensaje);
    }
}
