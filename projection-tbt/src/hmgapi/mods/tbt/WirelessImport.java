package hmgapi.mods.tbt;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hmgapi.hook.ModContext;

/**
 * The owner's turn-by-turn settings from the Wireless mod (2.x had them: tbt, tbt_standard,
 * tbt_side_streets), taken over once. HMG-API has no settings migration, so without this an
 * owner who moves to this mod starts at its defaults: turn-by-turn back on for one who had it
 * off, the car's own turn format off for one who had it on.
 *
 * Once: only while this mod has no settings file of its own (the owner never saved any, and
 * nothing was taken over yet). Then the values found are written to this mod's file, so the
 * owner sees them in the HMG-API app and HMG-API reads them from the next start, and they
 * hold in memory for this start (its settings were read before zygoteInit). Every setting
 * here takes effect at a restart, so that's the whole effect. The Wireless mod's file is
 * only read: 3.0 leaves the old keys there (until the owner saves its settings), and a
 * downgrade to 2.x finds them again.
 *
 * Runs in zygoteInit, as root in the zygote (/data/hmgapi/settings is root-only), before any
 * app is forked: the forked apps inherit the values. No Android here (host-tested).
 */
public final class WirelessImport {
    private WirelessImport() {}

    static final String WIRELESS = "wireless-carplay-aa";
    /** the settings that moved, under the same keys */
    static final String[] KEYS = { "tbt", "tbt_standard", "tbt_side_streets" };

    /** what was taken over this start (empty: nothing) */
    static volatile Map<String, Boolean> values = new LinkedHashMap<String, Boolean>();

    /** a setting: the value taken over this start, else HMG-API's (the owner's, else the default) */
    public static boolean bool(ModContext mod, String key, boolean fallback) {
        Boolean v = values.get(key);
        return v != null ? v : mod.settingBoolean(key, fallback);
    }

    /** settings: HMG-API's settings folder; own: this mod's id. Returns what it logs (null: nothing to say). */
    static String run(File settings, String own) {
        File mine = new File(settings, own + ".json"), theirs = new File(settings, WIRELESS + ".json");
        if (mine.exists() || !theirs.isFile()) return null;
        Map<String, Boolean> found;
        try {
            found = booleans(read(theirs));
        } catch (Exception e) {
            return "turn-by-turn settings from the Wireless mod: can't read them (" + e + ")";
        }
        if (found.isEmpty()) return null;
        values = found;
        String saved;
        try {
            write(settings, mine, json(found));
            saved = "saved as this mod's";
        } catch (Exception e) {
            saved = "for this start only, can't save them (" + e + "): taken over again at the next";
        }
        return "turn-by-turn settings taken over from the Wireless mod, " + saved + ": " + json(found);
    }

    private static final Pattern PAIR = Pattern.compile("\"(tbt|tbt_standard|tbt_side_streets)\"\\s*:\\s*(true|false)\\b");

    /** the moved keys with a true/false value in a settings file (written by hmgapid: one flat JSON object) */
    static Map<String, Boolean> booleans(String json) {
        Map<String, Boolean> out = new LinkedHashMap<String, Boolean>();
        Matcher m = PAIR.matcher(json);
        while (m.find()) out.put(m.group(1), Boolean.valueOf(m.group(2)));
        Map<String, Boolean> ordered = new LinkedHashMap<String, Boolean>();
        for (String k : KEYS) if (out.containsKey(k)) ordered.put(k, out.get(k));
        return ordered;
    }

    static String json(Map<String, Boolean> m) {
        StringBuilder b = new StringBuilder("{");
        for (Map.Entry<String, Boolean> e : m.entrySet()) {
            if (b.length() > 1) b.append(", ");
            b.append('"').append(e.getKey()).append("\": ").append(e.getValue());
        }
        return b.append('}').toString();
    }

    private static String read(File f) throws Exception {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    /** as hmgapid's setting-set: a temporary file, synced, renamed over (owner-only, as the folder) */
    private static void write(File dir, File to, String json) throws Exception {
        File tmp = new File(dir, "." + to.getName() + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            out.write(json.getBytes("UTF-8"));
            out.getFD().sync();
        } finally {
            out.close();
        }
        tmp.setReadable(false, false);
        tmp.setReadable(true, true);
        tmp.setWritable(false, false);
        tmp.setWritable(true, true);
        if (!tmp.renameTo(to)) {
            tmp.delete();
            throw new java.io.IOException("can't rename " + tmp + " to " + to);
        }
    }
}
