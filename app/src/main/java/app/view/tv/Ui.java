package app.view.tv;

import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Ui {
    private Ui() {}

    static final int ACCENT = 0xFF7B61FF;
    static final int ACCENT2 = 0xFF00C2FF;

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Text mit Farbverlauf (für das VIEW-Logo). */
    static void gradientText(final TextView tv) {
        if (tv == null) return;
        tv.post(() -> {
            float w = tv.getPaint().measureText(tv.getText().toString());
            if (w <= 0) return;
            tv.getPaint().setShader(new LinearGradient(0, 0, w, tv.getTextSize(),
                    new int[]{0xFFFFFFFF, 0xFFB9A8FF, ACCENT2}, new float[]{0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP));
            tv.invalidate();
        });
    }

    // "DE | Deutschland", "|DE| SPORT", "AL: Albania", "UK - News"
    private static final Pattern CATEGORY = Pattern.compile(
            "^[^A-Za-z0-9]*([A-Za-z]{2,3})\\s*[|:\\-–—►»•]+\\s*(.+)$");
    private static final Pattern CHANNEL_PREFIX = Pattern.compile(
            "^[^A-Za-z0-9]*[A-Za-z]{2,3}\\s*[|:]+\\s*");
    private static final Pattern LEADING_JUNK = Pattern.compile("^[^\\p{L}\\p{N}]+");
    private static final Pattern TRAILING_JUNK = Pattern.compile("[|\\s]+$");

    /** Liefert {Kürzel, schöner Titel}. */
    static String[] splitCategory(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return new String[]{"TV", "Ohne Namen"};
        Matcher m = CATEGORY.matcher(s);
        if (m.matches()) {
            String rest = TRAILING_JUNK.matcher(LEADING_JUNK.matcher(m.group(2)).replaceFirst("")).replaceFirst("").trim();
            if (!rest.isEmpty()) {
                return new String[]{m.group(1).toUpperCase(Locale.ROOT), prettify(rest)};
            }
        }
        String clean = TRAILING_JUNK.matcher(LEADING_JUNK.matcher(s).replaceFirst("")).replaceFirst("").trim();
        if (clean.isEmpty()) clean = s;
        String code = clean.substring(0, 1).toUpperCase(Locale.ROOT);
        return new String[]{code, prettify(clean)};
    }

    /** "DEUTSCHLAND SPORT HD" -> "Deutschland Sport HD" */
    static String prettify(String s) {
        String[] words = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            boolean hasLetter = false;
            boolean allUpper = true;
            for (int i = 0; i < w.length(); i++) {
                char ch = w.charAt(i);
                if (Character.isLetter(ch)) {
                    hasLetter = true;
                    if (Character.isLowerCase(ch)) { allUpper = false; break; }
                }
            }
            if (hasLetter && allUpper && w.length() > 3) {
                sb.append(w.substring(0, 1)).append(w.substring(1).toLowerCase(Locale.GERMAN));
            } else {
                sb.append(w);
            }
        }
        return sb.length() == 0 ? s : sb.toString();
    }

    /** "DE: ZDF HD" -> "ZDF HD" */
    static String cleanChannelName(String raw) {
        String s = raw == null ? "" : raw.trim();
        String c = CHANNEL_PREFIX.matcher(s).replaceFirst("").trim();
        return c.isEmpty() ? (s.isEmpty() ? "Sender" : s) : c;
    }

    /** Kurzname, falls kein Logo da ist. */
    static String initials(String title) {
        String t = LEADING_JUNK.matcher(title == null ? "" : title).replaceFirst("").trim();
        if (t.isEmpty()) return "TV";
        String[] parts = t.split("\\s+");
        String first = parts[0];
        if (first.length() <= 5) return first.toUpperCase(Locale.GERMAN);
        if (parts.length > 1 && !parts[1].isEmpty()) {
            return (first.substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.GERMAN);
        }
        return first.substring(0, 2).toUpperCase(Locale.GERMAN);
    }

    static String expiryText(long seconds) {
        if (seconds <= 0) return "Abo: unbegrenzt";
        long ms = seconds * 1000L;
        long diff = ms - System.currentTimeMillis();
        String date = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN).format(new Date(ms));
        if (diff < 0) return "Abo abgelaufen am " + date;
        long days = (diff + 86_399_999L) / 86_400_000L;
        return "Abo bis " + date + "  ·  noch " + days + (days == 1 ? " Tag" : " Tage");
    }
}
