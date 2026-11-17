package app.view.tv;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Xtream-Codes-API: Login, Kategorien, Sender, Stream-Adressen. */
final class Xtream {
    private Xtream() {}

    static final String UA = "VLC/3.0.20 LibVLC/3.0.20";
    static final int TYPE_NORMAL = 0;
    static final int TYPE_FAV = 1;
    static final int TYPE_ALL = 2;
    private static final long CACHE_MS = 12L * 60L * 60L * 1000L;
    private static final String FILE_CATS = "live_categories.json";
    private static final String FILE_STREAMS = "live_streams.json";

    static final class Channel {
        final String id;
        final String name;
        final String title;
        final String logo;
        final String categoryId;
        final String initials;
        final long stableId;

        Channel(String id, String name, String logo, String categoryId) {
            this.id = id;
            this.name = name;
            this.title = Ui.cleanChannelName(name);
            this.logo = logo;
            this.categoryId = categoryId;
            this.initials = Ui.initials(this.title);
            long sid;
            try {
                sid = Long.parseLong(id);
            } catch (NumberFormatException e) {
                sid = ((long) id.hashCode()) | 0x4000000000000000L;
            }
            this.stableId = sid;
        }
    }

    static final class Category {
        final String id;
        final String title;
        final String code;
        final int type;
        final ArrayList<Channel> channels = new ArrayList<>();

        Category(String id, String title, String code, int type) {
            this.id = id;
            this.title = title;
            this.code = code;
            this.type = type;
        }
    }

    static final class Data {
        final ArrayList<Category> categories = new ArrayList<>();
        final ArrayList<Channel> all = new ArrayList<>();
        final HashMap<String, Channel> byId = new HashMap<>();
    }

    static final class LoginResult {
        boolean ok;
        String error = "";
        long expiry;
    }

    // ---------------------------------------------------------------- Adressen

    static String normalizeServer(String in) {
        String s = in == null ? "" : in.trim();
        if (s.isEmpty()) return s;
        if (!s.toLowerCase(Locale.ROOT).startsWith("http://") && !s.toLowerCase(Locale.ROOT).startsWith("https://")) {
            s = "http://" + s;
        }
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        String[] ends = {"/player_api.php", "/get.php", "/xmltv.php", "/panel_api.php"};
        boolean changed = true;
        while (changed) {
            changed = false;
            while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
            String lower = s.toLowerCase(Locale.ROOT);
            for (String e : ends) {
                if (lower.endsWith(e) && s.length() > e.length() + 8) {
                    s = s.substring(0, s.length() - e.length());
                    changed = true;
                    break;
                }
            }
        }
        return s;
    }

    static String streamUrl(String server, String user, String pass, Channel c, boolean hls) {
        return server + "/live/" + user + "/" + pass + "/" + c.id + (hls ? ".m3u8" : ".ts");
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static String api(String server, String user, String pass, String action) {
        String url = server + "/player_api.php?username=" + enc(user) + "&password=" + enc(pass);
        if (action != null) url += "&action=" + action;
        return url;
    }

    // ---------------------------------------------------------------- Netzwerk

    static String httpGet(String url) throws IOException {
        String current = url;
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(90000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "*/*");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null || loc.isEmpty()) throw new IOException("Weiterleitung ohne Ziel");
                current = new URL(new URL(current), loc).toString();
                continue;
            }
            if (code >= 400) {
                c.disconnect();
                throw new IOException("Server-Fehler " + code);
            }
            try (InputStream in = new BufferedInputStream(c.getInputStream());
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                c.disconnect();
            }
        }
        throw new IOException("Zu viele Weiterleitungen");
    }

    static String describe(Throwable e) {
        if (e instanceof UnknownHostException) return "Server nicht gefunden. Bitte die Server-URL prüfen.";
        if (e instanceof SocketTimeoutException) return "Der Server antwortet nicht. Bitte später nochmals versuchen.";
        if (e instanceof JSONException) return "Der Server hat keine gültige Antwort geschickt. Server-URL und Zugangsdaten prüfen.";
        if (e instanceof java.net.ConnectException) return "Keine Verbindung zum Server möglich.";
        if (e instanceof OutOfMemoryError) return "Die Senderliste ist zu gross für dieses Gerät.";
        String m = e.getMessage();
        return m == null || m.isEmpty() ? "Unbekannter Fehler (" + e.getClass().getSimpleName() + ")" : m;
    }

    private static String str(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return "";
        Object v = o.opt(key);
        if (v == null) return "";
        String s = String.valueOf(v).trim();
        return "null".equalsIgnoreCase(s) ? "" : s;
    }

    // ---------------------------------------------------------------- Login

    static LoginResult login(String server, String user, String pass) {
        LoginResult r = new LoginResult();
        try {
            String body = httpGet(api(server, user, pass, null));
            JSONObject o = new JSONObject(body);
            JSONObject ui = o.optJSONObject("user_info");
            if (ui == null) {
                r.error = "Keine gültige Xtream-Antwort. Stimmt die Server-URL?";
                return r;
            }
            int auth = ui.optInt("auth", 0);
            if (auth != 1) {
                r.error = "Benutzername oder Passwort ist falsch.";
                return r;
            }
            String status = str(ui, "status");
            if (status.equalsIgnoreCase("Banned") || status.equalsIgnoreCase("Disabled")) {
                r.error = "Dieses Konto ist gesperrt (" + status + ").";
                return r;
            }
            long exp = 0;
            try {
                String e = str(ui, "exp_date");
                if (!e.isEmpty()) exp = (long) Double.parseDouble(e);
            } catch (Exception ignored) {
            }
            if (status.equalsIgnoreCase("Expired")) {
                r.error = "Dein Abo ist abgelaufen.";
                return r;
            }
            r.ok = true;
            r.expiry = exp;
        } catch (Throwable e) {
            r.error = describe(e);
        }
        return r;
    }

    // ---------------------------------------------------------------- Sender

    static void clearCache(Context ctx) {
        File dir = ctx.getApplicationContext().getFilesDir();
        //noinspection ResultOfMethodCallIgnored
        new File(dir, FILE_CATS).delete();
        //noinspection ResultOfMethodCallIgnored
        new File(dir, FILE_STREAMS).delete();
    }

    static Data load(Context ctx, String server, String user, String pass, boolean force) throws Exception {
        File dir = ctx.getApplicationContext().getFilesDir();
        File fCat = new File(dir, FILE_CATS);
        File fStr = new File(dir, FILE_STREAMS);
        boolean haveCache = fCat.exists() && fStr.exists() && fStr.length() > 2;
        boolean fresh = haveCache && (System.currentTimeMillis() - fStr.lastModified()) < CACHE_MS;

        JSONArray cats = null;
        JSONArray streams = null;
        if (force || !fresh) {
            try {
                String c = httpGet(api(server, user, pass, "get_live_categories"));
                String s = httpGet(api(server, user, pass, "get_live_streams"));
                JSONArray ca = new JSONArray(c.trim());
                JSONArray sa = new JSONArray(s.trim());
                cats = ca;
                streams = sa;
                write(fCat, c);
                write(fStr, s);
            } catch (Exception e) {
                if (!haveCache) throw e;
            }
        }
        if (cats == null || streams == null) {
            cats = new JSONArray(read(fCat).trim());
            streams = new JSONArray(read(fStr).trim());
        }
        return build(cats, streams);
    }

    private static Data build(JSONArray ca, JSONArray sa) {
        Data d = new Data();
        LinkedHashMap<String, Category> map = new LinkedHashMap<>();
        for (int i = 0; i < ca.length(); i++) {
            JSONObject o = ca.optJSONObject(i);
            if (o == null) continue;
            String id = str(o, "category_id");
            if (id.isEmpty() || map.containsKey(id)) continue;
            String[] p = Ui.splitCategory(str(o, "category_name"));
            map.put(id, new Category(id, p[1], p[0], TYPE_NORMAL));
        }
        for (int i = 0; i < sa.length(); i++) {
            JSONObject o = sa.optJSONObject(i);
            if (o == null) continue;
            String id = str(o, "stream_id");
            if (id.isEmpty() || d.byId.containsKey(id)) continue;
            String name = str(o, "name");
            Channel ch = new Channel(id, name, str(o, "stream_icon"), str(o, "category_id"));
            d.all.add(ch);
            d.byId.put(id, ch);
            Category c = map.get(ch.categoryId);
            if (c != null) c.channels.add(ch);
        }
        for (Category c : map.values()) {
            if (!c.channels.isEmpty()) d.categories.add(c);
        }
        return d;
    }

    private static void write(File f, String s) throws IOException {
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(f)) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            if (!tmp.renameTo(f)) throw new IOException("Cache konnte nicht gespeichert werden");
        }
    }

    private static String read(File f) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(f));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
