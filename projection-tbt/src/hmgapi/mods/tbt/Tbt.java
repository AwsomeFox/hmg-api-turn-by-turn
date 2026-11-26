package hmgapi.mods.tbt;

import hmgapi.hook.Car;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.MethodHook;
import hmgapi.hook.ModContext;
import hmgapi.hook.ModEntry;
import hmgapi.hook.Points;

/**
 * Phone turn-by-turn: Android Auto's and CarPlay's directions on the cluster and the HUD,
 * wired or wireless. It came out of the Wireless CarPlay + Android Auto mod (Open-HMG
 * hmgapi-wireless-carplay-aa), which keeps the wireless connection itself.
 *
 * Android Auto (GPM): AaTurns, from the Android Auto library's callbacks (gpmTurnByTurn).
 * CarPlay (DiPO): the mod's DiPO part, hmgapi.mods.tbt.dipo.Dipo, with CpTurns.
 * The iAP2 server: route guidance is offered to the iPhone (iap2).
 * The car's navigation: its own turn-by-turn calls are skipped while a phone guides, and
 * the side-street and road-graph settings live there (navigation).
 */
public final class Tbt extends ModEntry {
    private final java.io.File aaLibrary;
    private Boolean aaTbtSupported;

    public Tbt() {
        this(new java.io.File("/system/lib/libautoreceiver_infobank.so"));
    }

    /** Allows off-car tests to exercise the same capability check with private stock or fixtures. */
    Tbt(java.io.File aaLibrary) {
        this.aaLibrary = aaLibrary;
    }

    /** side streets from the car's map: needs turn-by-turn and the car's own format (TURN frames) */
    static boolean mapSideStreets(ModContext mod) {
        return mod.settingBoolean("tbt_map_side_streets", false) && mod.settingBoolean("tbt_standard", false);
    }

    private boolean aaTbtSupported() {
        if (aaTbtSupported == null) aaTbtSupported = AaTbtSupport.supports(aaLibrary);
        return aaTbtSupported;
    }

    static final String GPM = "com.google.android.projection.sink";

    static final String NAVIGATION = "com.mnsoft.navi";
    static final String DIPO = "com.daudio.app.dipo";
    static final String IAP2 = "com.tcc.iap2server";
    /** NavigationCluster's turn-by-turn calls: the car navigation's own guidance, off while a phone guides */
    static final String[] NAVI_TBT = { "update_tbt_info", "update_tbt_sub_info", "update_tbt_sub_info_b", "update_tbt_sub_info2",
        "update_tbt_sub_info2_b", "update_tbt_sub_info_2c", "update_remain_dis", "update_tp_crossroad_name",
        "update_tp_destination_name", "update_tp_turn_list", "update_tp_turn_list_new", "update_tp_lane_info", "update_lane_info" };

    @Override
    public void appLoaded(final ModContext mod, LoadedApp app) {
        if (NAVIGATION.equals(app.packageName)) navigation(mod, app);
        if (IAP2.equals(app.packageName)) iap2(mod, app);
        if (GPM.equals(app.packageName)) gpmTurnByTurn(mod, app);
        if (DIPO.equals(app.packageName)) dipo(mod, app);
    }

    /**
     * DiPO (CarPlay): turn-by-turn, in the mod's DiPO part (hmgapi.mods.tbt.dipo.Dipo),
     * which links against DiPO's iAP2 library.
     */
    private void dipo(ModContext mod, LoadedApp app) {
        try {
            mod.appPart(app, "hmgapi.mods.tbt.dipo.Dipo").getMethod("start", ModContext.class, LoadedApp.class).invoke(null, mod, app);
        } catch (Throwable t) {
            mod.error("DiPO part", t instanceof java.lang.reflect.InvocationTargetException ? t.getCause() : t);
        }
    }

    /**
     * In the car navigation app, while a phone guides (Android Auto or CarPlay): its own
     * turn-by-turn calls to the cluster and HUD are skipped, so they don't fight the
     * phone's. Its speed limit, cameras, traps, warning distances and driver-assist data
     * still go through (phones carry none of that). Its lanes (updateLaneInfo) don't: with
     * no route it sends the lanes of every intersection it passes, none marked, in the
     * same 0xF1 frame as the phone's lanes for the next turn, which it overwrote (US
     * Ioniq 5, 2026-11-03). Framework calls only: no app names.
     *
     * Setting navi_side_streets (off by default): the car navigation's
     * own turns carry the intersection's side streets outside Korea too. Its engine
     * (libExSLNavi.so) works them out for every turn but keeps them for Korea
     * (CInformation::IsSupportSideStreet); a memory recipe per engine build
     * (recipes/navi-side-streets-*.recipe, from tools/navi/side-streets-recipe.py) makes
     * it always do so. On an engine build with no recipe nothing changes, and the log says so.
     *
     * Setting navi_road_graph (a test, off by default): NaviGraph reads the engine's road
     * graph at the intersection ahead and logs its roads. Read and log only.
     * Setting tbt_map_side_streets (off by default): NaviGraph answers Android Auto's
     * questions about the junction of the turn it's showing (MapJunction, in GPM).
     */
    private void navigation(final ModContext mod, final LoadedApp app) {
        if (mod.settingBoolean("navi_side_streets", false)) {
            mod.nativePatch(app, "libExSLNavi.so");
            mod.log("navigation: side streets on the car navigation's own turns, if its engine build has a recipe");
        }
        boolean probe = mod.settingBoolean("navi_road_graph", false), answer = mapSideStreets(mod);
        if (probe || answer) NaviGraph.start(mod, app, probe, answer);
        // Keep stock navigation available when this mod cannot supply AA turns. CarPlay
        // and cars with GPM's own turn-by-turn retain their existing arbitration.
        final boolean aaGuidance = Car.stepOfSpec() >= 999 || aaTbtSupported();
        MethodHook skip = new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                if (Car.carPlayGuiding(app.context) || (aaGuidance && Car.androidAutoGuiding(app.context))) p.setResult(null);
            }
        };
        for (String m : NAVI_TBT) mod.hook(null, "framework.navigation_cluster." + m, skip);
        mod.log("navigation: turn-by-turn yields to " + (aaGuidance ? "Android Auto and CarPlay" : "CarPlay")
                + " in " + app.processName);
    }

    /**
     * The iAP2 server: it offers RouteGuidance to the iPhone only when the car's stepofspec
     * is 999 or more (US/Canada: 105), so HMG-API sets that one flag after it decides.
     */
    private void iap2(final ModContext mod, LoadedApp app) {
        final ClassLoader cl = app.classLoader;
        mod.hook(cl, "iap2.init_info.check_supports_features", new MethodHook() {
            @Override
            protected void afterHookedMethod(Param p) {
                try {
                    Points.field(cl, "iap2.init_info.route_guidance").setInt(null, 1);
                } catch (Exception e) {
                    mod.error("route guidance flag", e);
                }
            }
        });
    }

    /**
     * Android Auto turn-by-turn (GPM). GPM's own turn-by-turn manager only exists when
     * the car's stepofspec is 999 or more (US/Canada: 105), and it claims the cluster in a
     * way these cars don't give back. HMG-API leaves GPM's manager off and runs its own
     * (AaTurns) from the AA library's callbacks, sending through HMG-API's Cluster API.
     * GPM tells the phone its cluster takes pictures (IMAGE); HMG-API says ENUM, so the
     * phone sends turn data the cluster can show. One AaTurns per connection (GalReceiver),
     * all on one thread.
     */
    private void gpmTurnByTurn(final ModContext mod, final LoadedApp app) {
        final ClassLoader cl = app.classLoader;
        if (Car.stepOfSpec() >= 999) {
            mod.log("Android Auto: stepofspec is 999 or more, GPM's own turn-by-turn runs (HMG-API's stays off)");
            return;
        }
        // nativePatch is asynchronous and returns no success result. Do not install
        // its dependent Java hooks on an unknown library. CarPlay is separate.
        if (!aaTbtSupported()) {
            mod.log("Android Auto: cluster/HUD turn-by-turn off; stock library has no verified recipe or could not be read");
            return;
        }
        // the AA library: protocol 1.6 and the turn-by-turn extras packed into its callbacks, in memory (file stays stock)
        mod.nativePatch(app, "libautoreceiver_infobank.so");
        mod.hook(cl, "gpm.navigation_status.init", new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                if (p.args[3] == Points.get(cl, "gpm.cluster_type.image", null)) p.args[3] = Points.get(cl, "gpm.cluster_type.enum", null);
            }
        });
        final java.util.Map<Object, AaTurns> turns = new java.util.WeakHashMap<Object, AaTurns>();
        final android.os.HandlerThread thread = new android.os.HandlerThread("hmgapi-aa-tbt");
        thread.start();
        final android.os.Handler handler = new android.os.Handler(thread.getLooper());
        abstract class OnTurns extends MethodHook {
            abstract void run(AaTurns t, Object[] a);

            /** receiver: the GalReceiver; create: make its AaTurns if there's none yet */
            void post(final Object receiver, final boolean create, final Object[] args) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AaTurns t;
                            synchronized (turns) {
                                t = turns.get(receiver);
                                if (t == null && create) {
                                    hmgapi.hook.Cluster c = hmgapi.hook.Cluster.of(app.context);
                                    if (c == null) return;
                                    turns.put(receiver, t = new AaTurns(app.context, c, handler));
                                    t.standardTurns = mod.settingBoolean("tbt_standard", false);
                                    if (mapSideStreets(mod)) t.junctions = new MapJunction(app.context, handler);
                                    t.laneSideStreets = t.standardTurns && mod.settingBoolean("tbt_lane_side_streets", false);
                                    mod.log("Android Auto: navigation callback received; cluster bridge created"
                                            + (t.standardTurns ? " (standard turns"
                                                    + (t.laneSideStreets ? ", intersections from the lanes" : "")
                                                    + (t.junctions != null ? (t.laneSideStreets ? ", else" : ",") + " side streets from the car's map" : "")
                                                    + ")" : ""));
                                }
                            }
                            if (t != null) OnTurns.this.run(t, args);
                        } catch (Throwable x) {
                            mod.error("Android Auto turn-by-turn", x);
                        }
                    }
                });
            }

            @Override
            protected void afterHookedMethod(Param p) {
                post(Points.get(cl, "gpm.navigation_status.receiver", p.thisObject), true, p.args.clone());
            }
        }
        mod.hook(cl, "gpm.navigation_status.next_turn", new OnTurns() {
            @Override
            void run(AaTurns t, Object[] a) {
                t.nextTurn((String) a[0], (Integer) a[1], (Integer) a[2], (Integer) a[4], (Integer) a[5]);
            }
        });
        mod.hook(cl, "gpm.navigation_status.next_turn_distance", new OnTurns() {
            @Override
            void run(AaTurns t, Object[] a) {
                t.nextTurnDistance((Integer) a[0], (Integer) a[1], (Integer) a[2], (Integer) a[3]);
            }
        });
        mod.hook(cl, "gpm.navigation_status.status", new OnTurns() {
            @Override
            void run(AaTurns t, Object[] a) {
                t.status((Integer) a[0]);
            }
        });
        mod.hook(cl, "gpm.receiver.send_navigation_focus", new OnTurns() {
            @Override
            void run(AaTurns t, Object[] a) {
                if (a[0].equals(Points.get(cl, "gpm.receiver.focus_native", null))) t.stop();
            }

            @Override
            protected void afterHookedMethod(Param p) {
                post(p.thisObject, false, p.args.clone());
            }
        });
        mod.hook(cl, "gpm.receiver.destroy", new OnTurns() {
            @Override
            void run(AaTurns t, Object[] a) {
                t.end();
                synchronized (turns) {
                    turns.values().remove(t);
                }
            }

            @Override
            protected void beforeHookedMethod(Param p) {
                post(p.thisObject, false, new Object[0]);
            }

            @Override
            protected void afterHookedMethod(Param p) {}
        });
    }

}
