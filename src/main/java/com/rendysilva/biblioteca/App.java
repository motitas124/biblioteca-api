package com.rendysilva.biblioteca;

import com.rendysilva.biblioteca.http.ApiServidor;
import com.rendysilva.biblioteca.servicio.BibliotecaServicio;

import java.time.Clock;

public class App {

    public static void main(String[] args) throws Exception {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        BibliotecaServicio servicio = new BibliotecaServicio(Clock.systemDefaultZone());
        cargarDatosDeEjemplo(servicio);

        ApiServidor api = new ApiServidor(servicio, puerto);
        api.iniciar();
        System.out.println("API de Biblioteca escuchando en http://localhost:" + api.puerto() + "/api/libros");
        Runtime.getRuntime().addShutdownHook(new Thread(api::detener));
    }

    static void cargarDatosDeEjemplo(BibliotecaServicio s) {
        s.crearLibro("978-0307474728", "Cien anos de soledad", "Gabriel Garcia Marquez", 1967, 3);
        s.crearLibro("978-0132350884", "Clean Code", "Robert C. Martin", 2008, 2);
        s.crearLibro("978-0201633610", "Design Patterns", "Gamma, Helm, Johnson, Vlissides", 1994, 1);
        s.crearLibro("978-9978671015", "Huasipungo", "Jorge Icaza", 1934, 2);
        s.crearUsuario("Ana Torres", "ana.torres@correo.com");
        s.crearUsuario("Luis Paredes", "luis.paredes@correo.com");
    }
}
