# API REST de Biblioteca — Java

API REST para gestionar el catálogo, los usuarios y los préstamos de una biblioteca, con reglas de negocio reales: límite de préstamos por usuario, control de ejemplares, fechas de vencimiento y cálculo de multas.

Está construida **solo con el JDK** (sin Spring ni librerías externas), usando el servidor HTTP incluido en Java, para entender lo que los frameworks hacen por debajo: enrutamiento, serialización JSON, códigos de estado y concurrencia.

![Java](https://img.shields.io/badge/Java-17+-ED8B00?logo=openjdk&logoColor=white)
![REST](https://img.shields.io/badge/API-REST%20%2F%20JSON-blue)
![JUnit](https://img.shields.io/badge/JUnit-5-25A162?logo=junit5&logoColor=white)

## Endpoints

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `GET` | `/api/libros?q=texto&disponibles=true` | Lista y busca libros por título o autor | 200 |
| `GET` | `/api/libros/{id}` | Obtiene un libro | 200, 404 |
| `POST` | `/api/libros` | Registra un libro | 201, 400, 409 |
| `DELETE` | `/api/libros/{id}` | Elimina un libro sin préstamos activos | 204, 404, 409 |
| `GET` | `/api/usuarios` | Lista usuarios | 200 |
| `POST` | `/api/usuarios` | Registra un usuario (correo único) | 201, 400, 409 |
| `GET` | `/api/usuarios/{id}/prestamos` | Historial de préstamos de un usuario | 200, 404 |
| `GET` | `/api/prestamos?vencidos=true` | Lista préstamos (opcionalmente solo vencidos) | 200 |
| `POST` | `/api/prestamos` | Presta un libro a un usuario | 201, 404, 409 |
| `POST` | `/api/prestamos/{id}/devolucion` | Registra la devolución | 200, 404, 409 |

## Reglas de negocio

- Un préstamo dura **14 días**; después se marca como `VENCIDO`.
- Multa de **$0.25 por día** de atraso.
- Máximo **3 préstamos activos** por usuario.
- Un usuario con préstamos vencidos **no puede pedir otro libro**.
- No se puede prestar un libro sin ejemplares disponibles ni eliminar uno que está prestado.
- Los préstamos están sincronizados para que dos peticiones simultáneas no presten el último ejemplar dos veces.

## Ejemplos

```bash
# Prestar un libro
curl -X POST localhost:8080/api/prestamos -d '{"libroId": 3, "usuarioId": 1}'
```
```json
{"id":1,"libroId":3,"usuarioId":1,"fechaPrestamo":"2026-11-12","fechaLimite":"2026-11-26",
 "fechaDevolucion":null,"estado":"ACTIVO","multa":"0.00"}
```

```bash
# Intentar prestar el mismo libro (solo había un ejemplar)
curl -X POST localhost:8080/api/prestamos -d '{"libroId": 3, "usuarioId": 2}'
```
```json
{"error":"No hay ejemplares disponibles de \"Design Patterns\""}      ← HTTP 409
```

## Arquitectura

```
com.rendysilva.biblioteca
├── App.java              → Arranque y datos de ejemplo
├── http/                 → ApiServidor (rutas, códigos HTTP), Json (serializador propio)
├── servicio/             → BibliotecaServicio (reglas de negocio), ErrorNegocio
├── repositorio/          → Repositorio<T> genérico, en memoria y seguro para hilos
└── modelo/               → Libro, Usuario, Prestamo (records inmutables)
```

- La capa **HTTP** solo traduce peticiones y respuestas; toda la lógica vive en el **servicio**.
- Los errores de negocio llevan su código HTTP (`404`, `409`) y se manejan en un único punto.
- El **repositorio genérico** usa `ConcurrentHashMap` y `AtomicLong`; podría reemplazarse por uno con base de datos sin tocar el servicio.
- El servicio recibe un `Clock`, lo que permite **simular el paso del tiempo en las pruebas** (vencimientos y multas).

## Cómo ejecutarlo

Requiere Java 17 o superior.

```bash
mvn test                                  # 9 pruebas unitarias
mvn package
java -jar target/biblioteca-api-1.0.0.jar # escucha en el puerto 8080
```

Sin Maven:

```bash
javac -d out $(find src/main -name "*.java")
java -cp out com.rendysilva.biblioteca.App 8080
```

## Autor

**Rendy Silva** — Estudiante de Ingeniería en Software, Universidad de las Américas (UDLA), Quito.
