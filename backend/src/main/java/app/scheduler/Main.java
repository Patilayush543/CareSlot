package app.scheduler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Main {
    private static final Scheduler scheduler = new Scheduler();
    private static final Pattern FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"?([^\\\",}]+)\\\"?");

    public static void main(String[] args) throws Exception {
        int port = serverPort();
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/api/login", Main::login);
        server.createContext("/api/register", Main::register);
        server.createContext("/api/logout", Main::logout);
        server.createContext("/api/providers", Main::providers);
        server.createContext("/api/slots", Main::slots);
        server.createContext("/api/appointments", Main::appointments);
        server.createContext("/api/health", exchange -> send(exchange, 200, "{\"status\":\"ok\"}"));
        server.start();
        System.out.println("Appointment Slot Finder API running on port " + port);
    }

    private static int serverPort() {
        String configured = System.getenv("PORT");
        if (configured == null || configured.trim().isEmpty()) return 8080;
        try {
            int port = Integer.parseInt(configured.trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("PORT must be a number between 1 and 65535.");
        }
    }

    private static void login(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if (!"POST".equals(exchange.getRequestMethod())) { send(exchange, 405, error("Use POST.")); return; }
        try {
            java.util.Map<String, String> body = fields(readBody(exchange));
            DemoSession session = scheduler.login(required(body, "username"), required(body, "password"));
            send(exchange, 200, sessionJson(session));
        } catch (IllegalArgumentException ex) { send(exchange, 401, error(ex.getMessage())); }
    }

    private static void register(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if (!"POST".equals(exchange.getRequestMethod())) { send(exchange, 405, error("Use POST.")); return; }
        try {
            java.util.Map<String, String> body = fields(readBody(exchange));
            DemoSession session = scheduler.registerPatient(required(body, "username"), required(body, "password"),
                    required(body, "displayName"));
            send(exchange, 201, sessionJson(session));
        } catch (IllegalArgumentException ex) { send(exchange, 400, error(ex.getMessage())); }
    }

    private static void logout(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        scheduler.logout(bearerToken(exchange));
        send(exchange, 200, "{\"status\":\"logged out\"}");
    }

    private static void providers(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if (!"GET".equals(exchange.getRequestMethod())) { send(exchange, 405, error("Use GET.")); return; }
        StringBuilder json = new StringBuilder("[");
        List<Provider> list = scheduler.getProviders();
        for (int i = 0; i < list.size(); i++) {
            Provider p = list.get(i);
            if (i > 0) json.append(',');
            json.append("{\"id\":").append(quote(p.id)).append(",\"name\":").append(quote(p.name))
                .append(",\"service\":").append(quote(p.service)).append('}');
        }
        json.append(']');
        send(exchange, 200, json.toString());
    }

    private static void slots(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if (!"GET".equals(exchange.getRequestMethod())) { send(exchange, 405, error("Use GET.")); return; }
        try {
            scheduler.authenticate(bearerToken(exchange));
            java.util.Map<String, String> q = query(exchange.getRequestURI().getRawQuery());
            List<Appointment> result = scheduler.findSlots(required(q, "providerId"), required(q, "date"),
                    integer(q, "duration"), integer(q, "from"), integer(q, "to"));
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < result.size(); i++) {
                if (i > 0) json.append(',');
                json.append(appointmentJson(result.get(i)));
            }
            send(exchange, 200, json.append(']').toString());
        } catch (IllegalArgumentException ex) { send(exchange, 400, error(ex.getMessage())); }
    }

    private static void appointments(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if ("GET".equals(exchange.getRequestMethod())) {
            List<Appointment> list;
            try { list = scheduler.getAppointments(bearerToken(exchange)); }
            catch (IllegalArgumentException ex) { send(exchange, 401, error(ex.getMessage())); return; }
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) json.append(',');
                json.append(appointmentJson(list.get(i)));
            }
            send(exchange, 200, json.append(']').toString());
            return;
        }
        if ("DELETE".equals(exchange.getRequestMethod())) {
            try {
                Appointment appointment = scheduler.cancel(bearerToken(exchange), appointmentId(exchange));
                send(exchange, 200, appointmentJson(appointment));
            } catch (IllegalArgumentException ex) { send(exchange, 400, error(ex.getMessage())); }
            catch (IllegalStateException ex) { send(exchange, 500, error(ex.getMessage())); }
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) { send(exchange, 405, error("Use GET or POST.")); return; }
        try {
            java.util.Map<String, String> body = fields(readBody(exchange));
            Appointment a = scheduler.bookForPatient(bearerToken(exchange), required(body, "providerId"), required(body, "date"),
                    parseTime(required(body, "startTime")), parseTime(required(body, "endTime")));
            send(exchange, 201, appointmentJson(a));
        } catch (IllegalArgumentException ex) { send(exchange, 400, error(ex.getMessage())); }
    }

    private static String appointmentJson(Appointment a) {
        return "{\"id\":" + quote(a.id) + ",\"providerId\":" + quote(a.providerId) +
                ",\"providerName\":" + quote(a.providerName) + ",\"service\":" + quote(a.service) +
                ",\"patientName\":" + quote(a.patientName) + ",\"date\":" + quote(a.date) + ",\"startTime\":" + quote(a.getStartTime()) +
                ",\"endTime\":" + quote(a.getEndTime()) + "}";
    }

    private static String sessionJson(DemoSession session) {
        return "{\"token\":" + quote(session.token) + ",\"username\":" + quote(session.username) +
                ",\"displayName\":" + quote(session.displayName) + ",\"role\":" + quote(session.role) +
                ",\"providerId\":" + (session.providerId == null ? "null" : quote(session.providerId)) + "}";
    }

    private static String bearerToken(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) throw new IllegalArgumentException("Please log in.");
        return header.substring(7).trim();
    }

    private static String appointmentId(HttpExchange exchange) {
        String prefix = "/api/appointments/";
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith(prefix)) throw new IllegalArgumentException("Appointment ID is required.");
        String id = path.substring(prefix.length());
        if (id.isEmpty() || id.indexOf('/') >= 0) throw new IllegalArgumentException("Appointment ID is invalid.");
        return decode(id);
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        InputStream in = exchange.getRequestBody();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024]; int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static java.util.Map<String, String> fields(String json) {
        java.util.Map<String, String> map = new java.util.HashMap<String, String>();
        Matcher matcher = FIELD.matcher(json);
        while (matcher.find()) map.put(matcher.group(1), matcher.group(2).trim());
        return map;
    }

    private static java.util.Map<String, String> query(String raw) {
        java.util.Map<String, String> map = new java.util.HashMap<String, String>();
        if (raw == null) return map;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) map.put(decode(parts[0]), decode(parts[1]));
        }
        return map;
    }

    private static String decode(String value) {
        try { return java.net.URLDecoder.decode(value, "UTF-8"); }
        catch (Exception ex) { return value; }
    }

    private static String required(java.util.Map<String, String> map, String key) {
        String value = map.get(key);
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Missing field: " + key);
        return value.trim();
    }

    private static int integer(java.util.Map<String, String> map, String key) {
        try { return Integer.parseInt(required(map, key)); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Field " + key + " must be a number."); }
    }

    private static int parseTime(String text) {
        try {
            String[] pieces = text.split(":");
            int hours = Integer.parseInt(pieces[0]); int minutes = Integer.parseInt(pieces[1]);
            if (pieces.length != 2 || hours < 0 || hours > 23 || minutes < 0 || minutes > 59) throw new Exception();
            return hours * 60 + minutes;
        } catch (Exception ex) { throw new IllegalArgumentException("Time must use HH:mm format."); }
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String error(String message) { return "{\"error\":" + quote(message) + "}"; }

    private static boolean handleOptions(HttpExchange exchange) throws IOException {
        if (!"OPTIONS".equals(exchange.getRequestMethod())) return false;
        send(exchange, 204, "");
        return true;
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
