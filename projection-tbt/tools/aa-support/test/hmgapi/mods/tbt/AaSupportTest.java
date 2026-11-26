package hmgapi.mods.tbt;

import hmgapi.hook.Car;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.MethodHook;
import hmgapi.hook.ModContext;
import hmgapi.hook.Points;
import hmgapi.hook.Cluster;
import android.os.Handler;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashSet;

public final class AaSupportTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static ModContext enabled() {
        ModContext mod = new ModContext();
        mod.settings.put("tbt_standard", true);
        return mod;
    }

    private static void rejectsGpm(File library) {
        ModContext mod = enabled();
        new Tbt(library).appLoaded(mod, new LoadedApp(Tbt.GPM));
        check(mod.natives.isEmpty(), "unverified AA must not request a native patch");
        check(mod.hooks.isEmpty(), "unverified AA gets no navigation/callback hooks");
        check(mod.logs.toString().contains("no verified recipe"), "owner gets a reason AA TBT is off");
    }

    private static void navigation(File library, int step, boolean aa, boolean cp, boolean skip) throws Throwable {
        Car.step = step;
        Car.aaGuiding = aa;
        Car.cpGuiding = cp;
        ModContext mod = enabled();
        new Tbt(library).appLoaded(mod, new LoadedApp(Tbt.NAVIGATION));
        check(mod.hooks.size() == Tbt.NAVI_TBT.length, "all navigation arbitration hooks remain");
        for (MethodHook hook : mod.hooks.values()) {
            check(hook.skipsOriginal() == skip, "navigation yields only to available phone guidance");
        }
    }

    /** Exercises the actual Tbt callback hooks, not only their declarations. */
    private static void supportedGpm(File library) throws Throwable {
        Car.step = 105;
        ModContext mod = enabled();
        new Tbt(library).appLoaded(mod, new LoadedApp(Tbt.GPM));
        check(mod.natives.equals(Arrays.asList("libautoreceiver_infobank.so")), "supported AA requests its native recipe once");
        check(mod.hooks.keySet().equals(new LinkedHashSet<String>(Arrays.asList(
                "gpm.navigation_status.init",
                "gpm.navigation_status.next_turn", "gpm.navigation_status.next_turn_distance",
                "gpm.navigation_status.status", "gpm.receiver.send_navigation_focus", "gpm.receiver.destroy"))),
                "supported AA installs all dependent hooks");
        Object receiver = new Object(), status = new Object();
        Object receiver2 = new Object(), status2 = new Object();
        Points.receivers.put(status, receiver);
        Points.receivers.put(status2, receiver2);
        AaTurns.instances.clear();

        MethodHook init = mod.hooks.get("gpm.navigation_status.init");
        MethodHook.Param p = init.before(status, 9, 123L, 500, Points.IMAGE, 480, 480, 16);
        check(p.args[3] == Points.ENUM && p.args[0].equals(9) && p.args[1].equals(123L)
                && p.args[2].equals(500) && p.args[4].equals(480) && p.args[5].equals(480) && p.args[6].equals(16),
                "navigation init changes only IMAGE to ENUM at Java argument 3");
        Object otherClusterType = new Object();
        p = init.before(status, 9, 123L, 500, otherClusterType, 480, 480, 16);
        check(p.args[3] == otherClusterType, "non-IMAGE cluster type is preserved");

        MethodHook focus = mod.hooks.get("gpm.receiver.send_navigation_focus");
        MethodHook route = mod.hooks.get("gpm.navigation_status.status");
        MethodHook turn = mod.hooks.get("gpm.navigation_status.next_turn");
        MethodHook distance = mod.hooks.get("gpm.navigation_status.next_turn_distance");
        MethodHook destroy = mod.hooks.get("gpm.receiver.destroy");
        focus.after(receiver, Points.FOCUS_NATIVE);
        Handler.drain();
        check(AaTurns.instances.isEmpty(), "focus alone never creates a turn manager");

        Cluster.available = false;
        route.after(status, 1);
        Handler.drain();
        check(AaTurns.instances.isEmpty(), "unavailable cluster is harmless and creates no manager");
        Cluster.available = true;
        route.after(status, 1);
        Object[] turnArgs = { "Road", 2, 4, new byte[] { 9 }, 90, 7 };
        turn.after(status, turnArgs);
        turnArgs[0] = "changed after callback";
        distance.after(status, 120, 10, 120000, 1);
        check(AaTurns.instances.isEmpty(), "callbacks are posted before being dispatched");
        Handler.drain();
        check(AaTurns.instances.size() == 1, "callbacks share one manager per receiver");
        AaTurns first = AaTurns.instances.get(0);
        check(first.standardTurns, "saved standard-turn preference is copied into the manager");
        check(first.calls.equals(Arrays.asList("status 1", "turn Road 2 4 90 7", "distance 120 10 120000 1")),
                "dispatch preserves callback order, argument snapshot, field positions and distance flags");

        mod.settings.put("tbt_standard", false);
        route.after(status2, 3);
        Handler.drain();
        check(AaTurns.instances.size() == 2 && !AaTurns.instances.get(1).standardTurns,
                "a second receiver gets an independent manager and current preference");
        AaTurns second = AaTurns.instances.get(1);
        focus.after(receiver, Points.FOCUS_NATIVE + 1);
        Handler.drain();
        check(first.calls.size() == 3, "non-native navigation focus does not stop phone guidance");
        focus.after(receiver, Points.FOCUS_NATIVE);
        Handler.drain();
        check(first.calls.get(first.calls.size() - 1).equals("stop") && second.calls.equals(Arrays.asList("status 3")),
                "native navigation focus stops only the matching receiver");
        route.after(status, 2);
        destroy.before(receiver);
        destroy.after(receiver);
        Handler.drain();
        check(first.calls.subList(4, first.calls.size()).equals(Arrays.asList("status 2", "end")),
                "route end and receiver destruction dispatch once and in order");
        focus.after(receiver, Points.FOCUS_NATIVE);
        Handler.drain();
        check(first.calls.size() == 6 && AaTurns.instances.size() == 2,
                "destroy removes the receiver and later focus cannot revive it");
        route.after(status, 1);
        Handler.drain();
        check(AaTurns.instances.size() == 3 && AaTurns.instances.get(2).calls.equals(Arrays.asList("status 1")),
                "later connection callbacks get a fresh manager after destruction");

        navigation(library, 105, true, false, true);
        navigation(library, 105, false, true, true);
        navigation(library, 105, false, false, false);
        ModContext own = enabled();
        Car.step = 999;
        new Tbt(library).appLoaded(own, new LoadedApp(Tbt.GPM));
        check(own.natives.isEmpty() && own.hooks.isEmpty(),
                "supported library leaves step999 stock turn manager alone");
        Car.step = 105;
        Points.receivers.clear();
        AaTurns.instances.clear();
    }

    public static void main(String[] args) throws Throwable {
        check(AaTbtSupport.supportsDigest("48ee793af8b86052503b8c952b034731"), "existing US library retained");
        check(AaTbtSupport.supportsDigest("a0bea1c4992ea983a8190c4e032d7190"), "existing Canadian library retained");
        check(AaTbtSupport.supportsDigest("dbfe7bd2e36152a50f97fed612cf6326"), "CEEV library has an exact recipe");
        check(!AaTbtSupport.supportsDigest(null), "unknown digest rejected");
        check(!AaTbtSupport.supportsDigest(""), "empty digest rejected");
        check(!AaTbtSupport.supportsDigest("48ee793af8b86052503b8c952b034730"), "different library rejected");

        File directory = Files.createTempDirectory("aa-support-").toFile();
        File unknown = new File(directory, "unknown.so");
        File missing = new File(directory, "missing.so");
        try {
            Files.write(unknown.toPath(), new byte[] { 1, 2, 3, 4 });
            check(!AaTbtSupport.supports(unknown), "unknown file rejected");
            check(!AaTbtSupport.supports(missing), "missing file rejected");
            check(!AaTbtSupport.supports(directory), "file read failure rejected");
            check(!AaTbtSupport.supports(null), "missing path rejected");
            rejectsGpm(unknown);
            rejectsGpm(missing);
            rejectsGpm(directory);
            navigation(unknown, 105, true, false, false);
            navigation(unknown, 105, false, true, true);
            navigation(unknown, 105, true, true, true);
            navigation(unknown, 105, false, false, false);
            navigation(unknown, 999, true, false, true);

            Car.step = 105;
            Tbt wireless = new Tbt(unknown);
            ModContext cp = enabled();
            wireless.appLoaded(cp, new LoadedApp(Tbt.DIPO));
            check(cp.cpStarts == 1, "unknown AA library does not block CarPlay's app part");
            ModContext iap2 = enabled();
            wireless.appLoaded(iap2, new LoadedApp(Tbt.IAP2));
            check(iap2.hooks.keySet().equals(java.util.Collections.singleton("iap2.init_info.check_supports_features")),
                    "CarPlay route guidance remains enabled, and nothing else is hooked in the iAP2 server (the bridge is the Wireless mod's)");
            ModContext other = enabled();
            wireless.appLoaded(other, new LoadedApp("com.android.btsettings"));
            check(other.hooks.isEmpty() && other.natives.isEmpty(), "apps that aren't turn-by-turn's get no hooks");
            ModContext stock = enabled();
            Car.step = 999;
            wireless.appLoaded(stock, new LoadedApp(Tbt.GPM));
            check(stock.natives.isEmpty() && stock.hooks.isEmpty(), "native GPM TBT is preserved at step 999");
            Car.step = 105;
            if (args.length > 0) {
                File ceev = new File(args[0]);
                check(ceev.isFile(), "private CEEV library exists");
                check(AaTbtSupport.supports(ceev), "private CEEV library matches the allowlist");
                supportedGpm(ceev);
            }
        } finally {
            Files.deleteIfExists(unknown.toPath());
            Files.deleteIfExists(directory.toPath());
        }
        System.out.println("ok    AA native support: " + checks + " checks (no car access)");
    }
}
