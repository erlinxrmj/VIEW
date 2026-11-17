package app.view.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collection;

/** Gespeicherte Anmeldung und Favoriten. */
final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("view", Context.MODE_PRIVATE);
    }

    static boolean loggedIn(Context c) {
        return !server(c).isEmpty() && !user(c).isEmpty();
    }

    static String server(Context c) { return sp(c).getString("server", ""); }
    static String user(Context c) { return sp(c).getString("user", ""); }
    static String pass(Context c) { return sp(c).getString("pass", ""); }
    static String lastServer(Context c) { return sp(c).getString("last_server", ""); }
    static String lastUser(Context c) { return sp(c).getString("last_user", ""); }
    static long expiry(Context c) { return sp(c).getLong("expiry", 0L); }

    static void setExpiry(Context c, long seconds) {
        sp(c).edit().putLong("expiry", seconds).apply();
    }

    static void saveLogin(Context c, String server, String user, String pass, long expiry) {
        sp(c).edit()
                .putString("server", server)
                .putString("user", user)
                .putString("pass", pass)
                .putLong("expiry", expiry)
                .putString("last_server", server)
                .putString("last_user", user)
                .apply();
    }

    static void logout(Context c) {
        sp(c).edit().remove("server").remove("user").remove("pass").remove("expiry").apply();
    }

    /** Favoriten in der Reihenfolge, in der sie hinzugefügt wurden. */
    static ArrayList<String> favorites(Context c) {
        ArrayList<String> out = new ArrayList<>();
        String raw = sp(c).getString("favorites", "");
        if (raw == null || raw.isEmpty()) return out;
        for (String s : raw.split(",")) {
            String t = s.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    static void setFavorites(Context c, Collection<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (id == null || id.isEmpty()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(id.replace(",", ""));
        }
        sp(c).edit().putString("favorites", sb.toString()).apply();
    }
}
