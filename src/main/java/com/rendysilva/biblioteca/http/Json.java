package com.rendysilva.biblioteca.http;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializador y parser JSON minimo, escrito a mano para no depender de
 * librerias externas. Soporta objetos, listas, textos, numeros, booleanos y null.
 * El parser acepta objetos planos (clave -> valor simple), que es todo lo que
 * reciben los endpoints de esta API.
 */
public final class Json {

    private Json() {}

    public static String escribir(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(valor, sb);
        return sb.toString();
    }

    private static void escribir(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            texto(s, sb);
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (!primero) sb.append(',');
                texto(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escribir(e.getValue(), sb);
                primero = false;
            }
            sb.append('}');
        } else if (v instanceof Collection<?> lista) {
            sb.append('[');
            boolean primero = true;
            for (Object o : lista) {
                if (!primero) sb.append(',');
                escribir(o, sb);
                primero = false;
            }
            sb.append(']');
        } else {
            texto(v.toString(), sb);
        }
    }

    private static void texto(String s, StringBuilder sb) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    /** Parsea un objeto JSON plano: {"clave": "texto", "n": 3, "b": true}. */
    public static Map<String, Object> leerObjeto(String json) {
        Lector l = new Lector(json == null ? "" : json);
        Map<String, Object> resultado = new LinkedHashMap<>();
        l.espacios();
        l.esperar('{');
        l.espacios();
        if (l.ver() == '}') {
            l.pos++;
            return resultado;
        }
        while (true) {
            l.espacios();
            String clave = l.texto();
            l.espacios();
            l.esperar(':');
            l.espacios();
            resultado.put(clave, l.valor());
            l.espacios();
            char c = l.siguiente();
            if (c == '}') break;
            if (c != ',') throw new IllegalArgumentException("JSON invalido: se esperaba ',' o '}'");
        }
        return resultado;
    }

    private static final class Lector {
        private final String s;
        private int pos;

        Lector(String s) { this.s = s; }

        char ver() {
            if (pos >= s.length()) throw new IllegalArgumentException("JSON incompleto");
            return s.charAt(pos);
        }

        char siguiente() { char c = ver(); pos++; return c; }

        void espacios() { while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }

        void esperar(char c) {
            if (siguiente() != c) throw new IllegalArgumentException("JSON invalido: se esperaba '" + c + "'");
        }

        Object valor() {
            char c = ver();
            if (c == '"') return texto();
            if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            if (s.startsWith("null", pos)) { pos += 4; return null; }
            int inicio = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
            if (inicio == pos) throw new IllegalArgumentException("JSON invalido en posicion " + pos);
            String num = s.substring(inicio, pos);
            return num.matches("-?\\d+") ? (Object) Long.parseLong(num) : (Object) Double.parseDouble(num);
        }

        String texto() {
            esperar('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = siguiente();
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = siguiente();
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }
    }
}
