package hmgapi.mods.wireless;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.SystemClock;

/**
 * Android Auto's side of the map's side streets (setting tbt_map_side_streets): AaTurns,
 * in GPM, asks the car navigation (NaviGraph in com.mnsoft.navi) about the junction of the
 * turn it's sending. An answer that's fresh and for that question (NaviGraph.usable) is
 * held for the turn (NaviGraph.Held) until a newer match, a new turn, or the car is no
 * longer closing on it. Asks at most about once a second, only within NaviGraph.MAX_METERS.
 * Everything comes and goes on AaTurns' handler. Broadcasts go to one package each
 * (com.mnsoft.navi, and back to this one); the answer must name this turn's id and be
 * where its distance says.
 */
final class MapJunction implements AaTurns.Junctions {
    final Context context;
    final String self;
    final NaviGraph.Held held = new NaviGraph.Held();
    int askedId = -1, askedMeters = -1;
    long askedAt;

    MapJunction(Context context, Handler handler) {
        this.context = context;
        this.self = context.getPackageName();
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                int id = i.getIntExtra("id", -1);
                if (id != askedId) return;
                held.answer(id, askedMeters, i.getIntExtra("sides", 0) & 0x7fff, i.getIntExtra("meters", -1),
                        SystemClock.elapsedRealtime() - askedAt);
            }
        }, new IntentFilter(NaviGraph.ANSWER), null, handler);
    }

    @Override
    public int sides(int id, int meters, int angle) {
        long now = SystemClock.elapsedRealtime();
        if (meters > 0 && meters <= NaviGraph.MAX_METERS && (id != askedId || now - askedAt >= 900)) {
            Intent i = new Intent(NaviGraph.ASK);
            i.setPackage(NaviGraph.NAVIGATION);
            i.putExtra("id", id);
            i.putExtra("meters", meters);
            i.putExtra("angle", angle);
            i.putExtra("reply", self);
            context.sendBroadcast(i);
            askedId = id;
            askedMeters = meters;
            askedAt = now;
        }
        return held.sides(id, meters);
    }
}
