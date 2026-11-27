package hmgapi.mods.tbt;

import hmgapi.hook.ModContext;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;

public final class WirelessImportTest {
    static int fail, oks;

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (ok) oks++;
        else fail = 1;
    }

    static void eq(String what, Object want, Object got) {
        boolean ok = want == null ? got == null : want.equals(got);
        check(what + (ok ? "" : " (wanted " + want + ", got " + got + ")"), ok);
    }

    static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), "UTF-8");
    }

    static void write(File f, String s) throws Exception {
        Files.write(f.toPath(), s.getBytes("UTF-8"));
    }

    static void reset() {
        WirelessImport.values = new LinkedHashMap<String, Boolean>();
    }

    public static void main(String[] a) throws Exception {
        File d = Files.createTempDirectory("tbt-import-").toFile();
        File mine = new File(d, "projection-tbt.json"), theirs = new File(d, "wireless-carplay-aa.json");
        ModContext mod = new ModContext();

        // nothing to take over
        eq("no Wireless settings: nothing", null, WirelessImport.run(d, mod.id));
        check("...and no file made", !mine.exists());
        write(theirs, "{\"bridge\":true,\"audio\":false}");
        eq("Wireless settings without turn-by-turn's: nothing", null, WirelessImport.run(d, mod.id));
        check("...and no file made", !mine.exists());

        // an owner of 2.x with turn-by-turn off and the car's format on (hmgapid's file: the app's JSONObject)
        write(theirs, "{\"bridge\":true,\"tbt_side_streets\":true,\"tbt\":false,\"tbt_standard\":true,\"menu\":false}");
        String said = WirelessImport.run(d, mod.id);
        check("taken over and logged", said != null && said.contains("taken over from the Wireless mod, saved"));
        eq("saved as this mod's settings, the three keys only", "{\"tbt\": false, \"tbt_standard\": true, \"tbt_side_streets\": true}", read(mine));
        check("owner-only file, no temporary left", mine.canRead() && !new File(d, ".projection-tbt.json.tmp").exists());
        check("the Wireless mod's file is untouched", read(theirs).contains("\"tbt\":false") && read(theirs).contains("\"menu\":false"));
        check("this start: tbt off", !WirelessImport.bool(mod, "tbt", true));
        check("this start: standard turns on", WirelessImport.bool(mod, "tbt_standard", false));
        check("other settings: HMG-API's", !WirelessImport.bool(mod, "tbt_lane_side_streets", false));

        // the next start: this mod has its file, so it's never taken over again
        reset();
        write(theirs, "{\"tbt\":true,\"tbt_standard\":false}");
        eq("next start: nothing taken over", null, WirelessImport.run(d, mod.id));
        eq("...the owner's values stay", "{\"tbt\": false, \"tbt_standard\": true, \"tbt_side_streets\": true}", read(mine));
        mod.settings.put("tbt_standard", false);
        check("...and HMG-API's are used", !WirelessImport.bool(mod, "tbt_standard", true));
        // an owner who saved this mod's settings before: never overwritten
        write(mine, "{}");
        eq("an owner's own (even empty) settings: nothing", null, WirelessImport.run(d, mod.id));
        eq("...file kept", "{}", read(mine));

        // spacing and a value that isn't true/false
        reset();
        mine.delete();
        write(theirs, "{ \"tbt\" : true , \"tbt_standard\": \"yes\" }");
        WirelessImport.run(d, mod.id);
        eq("spaces read, a non-boolean left out", "{\"tbt\": true}", read(mine));

        // a folder it can't write: this start only, again at the next
        reset();
        mine.delete();
        write(theirs, "{\"tbt\":false}");
        d.setWritable(false);
        said = WirelessImport.run(d, mod.id);
        d.setWritable(true);
        check("can't save: still taken over for this start", said != null && said.contains("this start only") && !WirelessImport.bool(mod, "tbt", true));
        check("...nothing written, so the next start tries again", !mine.exists());

        for (File f : d.listFiles()) f.delete();
        d.delete();
        System.out.println(oks + " ok, " + (fail == 0 ? "0 failed" : "FAILED"));
        System.exit(fail);
    }
}
