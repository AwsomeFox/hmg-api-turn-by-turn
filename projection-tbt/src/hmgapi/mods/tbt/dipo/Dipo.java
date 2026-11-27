package hmgapi.mods.tbt.dipo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import com.tcc.iap2.IIap2Listener;
import com.tcc.iap2.IIap2Service;
import com.tcc.iap2.Iap2ListenerInfo;
import com.tcc.iap2.Iap2NotiMsg;

import hmgapi.hook.Car;
import hmgapi.hook.Cluster;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.MethodHook;
import hmgapi.hook.ModContext;

/**
 * The turn-by-turn mod in DiPO (the CarPlay app): its app part, linked against DiPO's
 * iAP2 library (com.tcc.iap2). The mod's entry starts it.
 *
 * DiPO's own route guidance manager only exists when the car's stepofspec is 999 or more,
 * and stays off (HMG-API never changes stepofspec). HMG-API registers its own listener
 * with the iAP2 server for route guidance (the server sends those notifications to every
 * listener that asks), and CpTurns sends them to the cluster. DiPO still starts and stops
 * the phone's route guidance itself when the phone or the car takes turn-by-turn; a stop
 * clears the HUD (hook on the iAP2 proxy).
 *
 * Wired CarPlay needs nothing else. With the Wireless CarPlay + Android Auto mod (3.0 or
 * newer) in the same app, the two work together without either requiring the other
 * (the Wireless repository's docs/tbt-contract.md has the whole agreement):
 *  - WIRELESS_UP: a system property of this process the Wireless mod sets to "1" while a
 *    wireless CarPlay session is up. iAP2 moves from the Bluetooth session (component 4)
 *    to the CarPlay channel's (5) then, and those two ending isn't the phone leaving.
 *  - TBT_UP: ours, "1" once this listener runs, so the Wireless mod asks the phone for
 *    route guidance again after its handover and re-attaches (its RgWatch).
 *  - RG_EVENT: a broadcast inside DiPO for that watchdog: "started" (a Start of ours),
 *    "stopped" (DiPO's stop) and "update" (route guidance is flowing; at most every 2 s).
 *  - A stop made on a thread whose name starts with OWN_THREADS is a mod's own restart.
 */
public final class Dipo {
    private Dipo() {}

    static final String IAP2_ACTION = "com.remote.service.IAP2SERVICE";
    /** iAP2 listener type for route guidance (notifications 120, 121, 122) */
    static final int LISTENER_ROUTE_GUIDANCE = 4096;
    static final int RG_UPDATE = 120, RG_MANEUVER = 121, RG_LANES = 122;
    static final String[] RG_COMPONENTS = { "RouteGuidanceComponent0" };
    /** iAP2 components: 2 isn't a session; 4 Bluetooth CarPlay, 5 the CarPlay session's channel */
    static final int COMPONENT_BT_CARPLAY = 4, COMPONENT_CARPLAY_CLIENT = 5;

    /** set by the Wireless mod in this process: "1" while a wireless CarPlay session is up */
    public static final String WIRELESS_UP = "hmgapi.wireless.carplay";
    /** set by this mod in this process: "1" while its route guidance listener runs */
    public static final String TBT_UP = "hmgapi.tbt.carplay";
    /** to the Wireless mod's watchdog, inside DiPO only: extra "event" = started, stopped or update */
    public static final String RG_EVENT = "hmgapi.mods.tbt.CP_RG";
    static final String RG_PERMISSION = "android.permission.BLUETOOTH_PRIVILEGED";
    /** threads of HMG-API mods that restart route guidance themselves */
    public static final String OWN_THREADS = "hmgapi-cp-";
    static final long UPDATE_EVERY_MS = 2000;

    static ModContext mod;
    static LoadedApp app;
    static volatile IIap2Service iap2;
    static Handler turnsHandler;
    static CpTurns turns;
    static RgListener rg;
    private static volatile long updateTold;
    /** set while HMG-API itself calls the iAP2 server (its own route guidance restart) */
    static final ThreadLocal<Boolean> OWN = new ThreadLocal<Boolean>();

    static void log(String m) {
        mod.log(m);
    }

    static void error(String m, Throwable t) {
        mod.error(m, t);
    }

    /** the mod's entry, in DiPO's process */
    public static void start(ModContext m, LoadedApp a) {
        mod = m;
        app = a;
        if (!turnByTurn()) return;
        System.setProperty(TBT_UP, "1");
        Intent i = new Intent(IAP2_ACTION);
        boolean bound = a.context.bindService(i, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder b) {
                IIap2Service s = IIap2Service.Stub.asInterface(b);
                iap2 = s;
                registerRouteGuidance(s);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                iap2 = null;
                log("DiPO: iAP2 server gone");
            }
        }, Context.BIND_AUTO_CREATE);
        log("DiPO: iAP2 server " + (bound ? "binding" : "can't be bound"));
    }

    private static boolean turnByTurn() {
        if (Car.stepOfSpec() >= 999) {
            log("CarPlay: stepofspec is 999 or more, DiPO's own turn-by-turn runs (HMG-API's stays off)");
            return false;
        }
        Cluster c = Cluster.of(app.context);
        if (c == null) return false;
        HandlerThread t = new HandlerThread("hmgapi-cp-tbt");
        t.start();
        turnsHandler = new Handler(t.getLooper());
        turns = new CpTurns(app.context, c, new Runnable() {
            @Override
            public void run() {
                tell("started");   // a Start of ours: the Wireless mod's watchdog follows it
                restartRouteGuidance();
            }
        });
        turns.standardTurns = hmgapi.mods.tbt.WirelessImport.bool(mod, "tbt_standard", false);
        turns.sideStreets = turns.standardTurns && hmgapi.mods.tbt.WirelessImport.bool(mod, "tbt_side_streets", false);
        if (turns.standardTurns) log("CarPlay: turns in the car's own format near a maneuver (standard turns)"
                + (turns.sideStreets ? ", with the iPhone's side streets" : ""));
        rg = new RgListener();
        mod.hook(app.classLoader, "dipo.iap2_proxy.stop_route_guidance", new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                // not ours, and not another HMG-API mod's (the Wireless mod asks again after a handover,
                // on a thread named as ours are): only DiPO's own stop ends the turns
                if (OWN.get() != null || Thread.currentThread().getName().startsWith(OWN_THREADS)) return;
                // logged every time (issue #24): a stop mid-route ends the phone's updates
                log("CarPlay: DiPO stopped route guidance");
                tell("stopped");
                post(new Runnable() {
                    @Override
                    public void run() {
                        turns.stop();
                    }
                });
            }
        });
        log("CarPlay: turn-by-turn to the cluster (HMG-API's route guidance listener)");
        return true;
    }

    static void post(Runnable r) {
        final Runnable run = r;
        turnsHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    run.run();
                } catch (Throwable x) {
                    error("CarPlay turn-by-turn", x);
                }
            }
        });
    }

    static void registerRouteGuidance(IIap2Service s) {
        try {
            Iap2ListenerInfo info = new Iap2ListenerInfo();
            info.onSetSupportType(LISTENER_ROUTE_GUIDANCE);
            info.onSetListenerName("HMGAPI_RG");
            log("CarPlay: Iap2SvcInit(HMGAPI_RG, 0x1000) = " + s.Iap2SvcInit(rg, info));
        } catch (Throwable t) {
            error("CarPlay: route guidance listener", t);
        }
    }

    /** after a source change: Stop, then Start, so the phone sends its whole current state (the build's HmgApiRgRestart) */
    static void restartRouteGuidance() {
        IIap2Service s = iap2;
        if (s == null) return;
        OWN.set(Boolean.TRUE);
        try {
            s.Iap2SvcStopRouteGuidanceUpdate(RG_COMPONENTS);
            Thread.sleep(200);
            s.Iap2SvcStartRouteGuidanceUpdate(RG_COMPONENTS, true, true);
        } catch (Throwable t) {
            // the session went away meanwhile
        } finally {
            OWN.remove();
        }
    }

    /** the Wireless mod has a wireless CarPlay session up, and this component's end is its handover's */
    static boolean wirelessKeeps(int component) {
        if (component != COMPONENT_BT_CARPLAY && component != COMPONENT_CARPLAY_CLIENT) return false;
        boolean up = "1".equals(System.getProperty(WIRELESS_UP));
        if (up) log("CarPlay: ignoring iAP2 disconnect comp=" + component + " (wireless CarPlay up)");
        return up;
    }

    /** route guidance is flowing: the Wireless mod's watchdog hears of it, at most every UPDATE_EVERY_MS */
    static void update() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - updateTold < UPDATE_EVERY_MS) return;
        updateTold = now;
        tell("update");
    }

    /** to the Wireless mod's route guidance watchdog, when that mod is here; nobody listens otherwise */
    static void tell(String event) {
        try {
            if ("started".equals(event) || "stopped".equals(event)) updateTold = 0;
            app.context.sendBroadcast(new Intent(RG_EVENT).setPackage(app.packageName).putExtra("event", event), RG_PERMISSION);
        } catch (Throwable t) {
            error("CarPlay: route guidance " + event, t);
        }
    }

    /** HMG-API's iAP2 listener for route guidance: everything goes to CpTurns on its thread */
    static final class RgListener extends IIap2Listener.Stub {
        @Override
        public void connected(int component, String serial) {}

        @Override
        public void disconnected(final int component) {
            // as DiPO's listener: component 2 isn't a session, and the bridge's own sessions end while wireless CarPlay runs
            if (component == 2 || wirelessKeeps(component)) return;
            post(new Runnable() {
                @Override
                public void run() {
                    turns.disconnected();
                }
            });
        }

        @Override
        public void notify(final int what, int a1, int a2, final Iap2NotiMsg msg) {
            if ((what != RG_UPDATE && what != RG_MANEUVER && what != RG_LANES) || msg == null) return;
            if (what != RG_LANES) update();
            post(new Runnable() {
                @Override
                public void run() {
                    if (what == RG_UPDATE) turns.routeGuidance(msg);
                    else if (what == RG_MANEUVER) turns.maneuver(msg);
                    else turns.lanes(msg);
                }
            });
        }
    }
}
