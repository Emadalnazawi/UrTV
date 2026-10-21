package sa.urtv.notify;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** A tiny HTTP/1.1 server: one request per connection, enough for Home Assistant's POSTs. */
final class Http {
    static final int MAX_BODY = 12 * 1024 * 1024;   // camera snapshots, with room to spare

    static final class Request {
        String method = "", path = "/";
        final Map<String, String> headers = new HashMap<>();
        byte[] body = new byte[0];
        final Map<String, String> fields = new HashMap<>();
        final Map<String, byte[]> files = new HashMap<>();
    }

    interface Handler { void handle(Request r) throws Exception; }

    private final ServerSocket server;

    Http(int port, Handler handler) throws IOException {
        server = new ServerSocket(port);
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try {
                    Socket s = server.accept();
                    new Thread(() -> serve(s, handler), "http-" + port).start();
                } catch (IOException e) {
                    if (server.isClosed()) return;
                }
            }
        }, "http-accept-" + port);
        t.setDaemon(true);
        t.start();
    }

    void close() { try { server.close(); } catch (IOException ignored) { } }

    private static void serve(Socket s, Handler handler) {
        int code = 200;
        try (Socket sock = s) {
            sock.setSoTimeout(15000);
            InputStream in = sock.getInputStream();
            Request r = read(in);
            try { handler.handle(r); } catch (Exception e) { code = 400; }
            OutputStream out = sock.getOutputStream();
            String text = code == 200 ? "OK" : "Bad request";
            out.write(("HTTP/1.1 " + code + (code == 200 ? " OK" : " Bad Request") + "\r\nContent-Type: text/plain\r\n"
                    + "Content-Length: " + text.length() + "\r\nConnection: close\r\n\r\n" + text)
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) { }
    }

    private static Request read(InputStream in) throws IOException {
        Request r = new Request();
        String head = readHead(in);
        String[] lines = head.split("\r\n");
        String[] first = lines[0].split(" ");
        if (first.length >= 2) { r.method = first[0]; r.path = first[1]; }
        for (int i = 1; i < lines.length; i++) {
            int c = lines[i].indexOf(':');
            if (c > 0) r.headers.put(lines[i].substring(0, c).trim().toLowerCase(Locale.ROOT), lines[i].substring(c + 1).trim());
        }
        int len = 0;
        try { len = Integer.parseInt(r.headers.getOrDefault("content-length", "0")); } catch (NumberFormatException ignored) { }
        if (len > MAX_BODY) throw new IOException("too large");
        if (len > 0) r.body = readN(in, len);
        String type = r.headers.getOrDefault("content-type", "");
        if (type.startsWith("multipart/form-data")) parseMultipart(r, type);
        else if (type.startsWith("application/x-www-form-urlencoded")) parseUrlEncoded(r, new String(r.body, StandardCharsets.UTF_8));
        int q = r.path.indexOf('?');
        if (q >= 0) { parseUrlEncoded(r, r.path.substring(q + 1)); r.path = r.path.substring(0, q); }
        return r;
    }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int s = 0, c;
        while ((c = in.read()) != -1) {
            b.write(c);
            s = (c == '\r' && (s == 0 || s == 2)) ? s + 1 : (c == '\n' && (s == 1 || s == 3)) ? s + 1 : 0;
            if (s == 4) break;
            if (b.size() > 64 * 1024) throw new IOException("headers too large");
        }
        return b.toString("ISO-8859-1");
    }

    private static byte[] readN(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int k = in.read(buf, off, n - off);
            if (k < 0) break;
            off += k;
        }
        return buf;
    }

    private static void parseUrlEncoded(Request r, String s) {
        for (String kv : s.split("&")) {
            if (kv.isEmpty()) continue;
            int e = kv.indexOf('=');
            try {
                String k = URLDecoder.decode(e < 0 ? kv : kv.substring(0, e), "UTF-8");
                String v = e < 0 ? "" : URLDecoder.decode(kv.substring(e + 1), "UTF-8");
                r.fields.put(k, v);
            } catch (Exception ignored) { }
        }
    }

    private static void parseMultipart(Request r, String type) {
        int bi = type.indexOf("boundary=");
        if (bi < 0) return;
        String boundary = type.substring(bi + 9).replace("\"", "").split(";")[0].trim();
        byte[] delim = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        byte[] b = r.body;
        int pos = indexOf(b, delim, 0);
        while (pos >= 0) {
            int start = pos + delim.length;
            if (start + 1 < b.length && b[start] == '-' && b[start + 1] == '-') break;   // closing boundary
            start += 2;   // CRLF after the boundary
            int next = indexOf(b, delim, start);
            if (next < 0) break;
            int headEnd = indexOf(b, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), start);
            if (headEnd < 0 || headEnd > next) { pos = next; continue; }
            String headers = new String(b, start, headEnd - start, StandardCharsets.UTF_8);
            int dataStart = headEnd + 4, dataEnd = next - 2;   // CRLF before the next boundary
            String name = attr(headers, "name");
            if (name != null && dataEnd >= dataStart) {
                byte[] data = java.util.Arrays.copyOfRange(b, dataStart, dataEnd);
                if (attr(headers, "filename") != null) r.files.put(name, data);
                else r.fields.put(name, new String(data, StandardCharsets.UTF_8));
            }
            pos = next;
        }
    }

    private static String attr(String headers, String key) {
        String low = headers.toLowerCase(Locale.ROOT);
        int i = low.indexOf("; " + key + "=");
        if (i < 0) i = low.indexOf(";" + key + "=");
        if (i < 0) return null;
        int s = headers.indexOf('=', i) + 1;
        if (s < headers.length() && headers.charAt(s) == '"') {
            int e = headers.indexOf('"', s + 1);
            return e < 0 ? null : headers.substring(s + 1, e);
        }
        int e = s;
        while (e < headers.length() && headers.charAt(e) != ';' && headers.charAt(e) != '\r') e++;
        return headers.substring(s, e).trim();
    }

    private static int indexOf(byte[] hay, byte[] needle, int from) {
        outer:
        for (int i = Math.max(0, from); i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }
}
