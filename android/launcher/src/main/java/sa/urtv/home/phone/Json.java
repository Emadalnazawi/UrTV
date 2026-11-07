package sa.urtv.home.phone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small strict JSON reader and writer for the phone page (org.json is Android's, and a test can't use it). It reads what the page may send
 * and nothing more: objects, arrays, strings, whole numbers up to nine digits, true, false, null; no fraction or exponent, no repeated key, no
 * control character inside a string, nothing after the value, at most four levels. No Android in it.
 */
public final class Json {
    public static final class Bad extends Exception {
        Bad(String why) { super(why); }
    }

    private static final int MAX_DEPTH = 4;
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    /** Map (LinkedHashMap), List, String, Long, Boolean or null. */
    public static Object parse(String text) throws Bad {
        Json p = new Json(text);
        p.ws();
        Object v = p.value(0);
        p.ws();
        if (p.i != text.length()) throw new Bad("trailing");
        return v;
    }

    /** An object, or Bad. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) throws Bad {
        Object v = parse(text);
        if (!(v instanceof Map)) throw new Bad("not an object");
        return (Map<String, Object>) v;
    }

    private void ws() {
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t' || s.charAt(i) == '\n' || s.charAt(i) == '\r')) i++;
    }

    private Object value(int depth) throws Bad {
        if (i >= s.length()) throw new Bad("end");
        char c = s.charAt(i);
        if (c == '{') return object(depth + 1);
        if (c == '[') return array(depth + 1);
        if (c == '"') return string();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        if (c == '-' || (c >= '0' && c <= '9')) return number();
        throw new Bad("value");
    }

    private Map<String, Object> object(int depth) throws Bad {
        if (depth > MAX_DEPTH) throw new Bad("deep");
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            if (i >= s.length() || s.charAt(i) != '"') throw new Bad("key");
            String k = string();
            if (m.containsKey(k)) throw new Bad("repeated key");
            ws();
            if (i >= s.length() || s.charAt(i) != ':') throw new Bad("colon");
            i++;
            ws();
            m.put(k, value(depth));
            ws();
            if (i >= s.length()) throw new Bad("end");
            if (s.charAt(i) == ',') { i++; continue; }
            if (s.charAt(i) == '}') { i++; return m; }
            throw new Bad("object");
        }
    }

    private List<Object> array(int depth) throws Bad {
        if (depth > MAX_DEPTH) throw new Bad("deep");
        List<Object> a = new ArrayList<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == ']') { i++; return a; }
        while (true) {
            ws();
            a.add(value(depth));
            if (a.size() > 64) throw new Bad("long");
            ws();
            if (i >= s.length()) throw new Bad("end");
            if (s.charAt(i) == ',') { i++; continue; }
            if (s.charAt(i) == ']') { i++; return a; }
            throw new Bad("array");
        }
    }

    private Long number() throws Bad {
        int start = i;
        if (s.charAt(i) == '-') i++;
        int digits = i;
        while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') i++;
        int n = i - digits;
        if (n == 0 || n > 9 || (n > 1 && s.charAt(digits) == '0')) throw new Bad("number");
        if (i < s.length() && (s.charAt(i) == '.' || s.charAt(i) == 'e' || s.charAt(i) == 'E')) throw new Bad("number");
        return Long.valueOf(s.substring(start, i));
    }

    private String string() throws Bad {
        StringBuilder b = new StringBuilder();
        i++;
        while (true) {
            if (i >= s.length()) throw new Bad("string");
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c < 0x20) throw new Bad("control");
            if (c != '\\') { b.append(c); continue; }
            if (i >= s.length()) throw new Bad("string");
            char e = s.charAt(i++);
            switch (e) {
                case '"': case '\\': case '/': b.append(e); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'n': b.append('\n'); break;
                case 'r': b.append('\r'); break;
                case 't': b.append('\t'); break;
                case 'u': {
                    if (i + 4 > s.length()) throw new Bad("escape");
                    int v = 0;
                    for (int k = 0; k < 4; k++) {
                        int d = Character.digit(s.charAt(i + k), 16);
                        if (d < 0) throw new Bad("escape");
                        v = v * 16 + d;
                    }
                    i += 4;
                    b.append((char) v);
                    break;
                }
                default: throw new Bad("escape");
            }
        }
    }

    // ---- writing

    /** A JSON string literal. < > & and the line separators are escaped too, so the text is safe wherever it ends up. */
    public static String quote(String v) {
        StringBuilder b = new StringBuilder("\"");
        for (int k = 0; k < v.length(); k++) {
            char c = v.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20 || c == '<' || c == '>' || c == '&' || c == ' ' || c == ' ' || c == 0x7f) b.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.append('"').toString();
    }

    /** Map, List, String, Number, Boolean, null (anything else is written as its text, quoted). */
    public static String write(Object v) {
        StringBuilder b = new StringBuilder();
        write(b, v);
        return b.toString();
    }

    private static void write(StringBuilder b, Object v) {
        if (v == null) b.append("null");
        else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) b.append(v);
        else if (v instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!first) b.append(',');
                first = false;
                b.append(quote(String.valueOf(e.getKey()))).append(':');
                write(b, e.getValue());
            }
            b.append('}');
        } else if (v instanceof Iterable) {
            b.append('[');
            boolean first = true;
            for (Object x : (Iterable<?>) v) {
                if (!first) b.append(',');
                first = false;
                write(b, x);
            }
            b.append(']');
        } else b.append(quote(String.valueOf(v)));
    }
}
