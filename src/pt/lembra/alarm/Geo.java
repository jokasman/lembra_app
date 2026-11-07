package pt.lembra.alarm;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;

import pt.lembra.core.Reminder;
import pt.lembra.data.Db;
import pt.lembra.data.Prefs;

/** Lembretes por localização com alertas de proximidade do Android (sem serviços Google). */
public final class Geo {
    private Geo() {}

    static PendingIntent pi(Context ctx, long id, boolean create) {
        Intent i = new Intent(ctx, AlarmReceiver.class).setAction(Engine.A_GEO).putExtra("id", id);
        // O sistema acrescenta o extra "entering", por isso tem de ser mutável.
        int flags = (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0)
                | (create ? PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_NO_CREATE);
        return PendingIntent.getBroadcast(ctx, (int) (id * 4 + 3), i, flags);
    }

    public static boolean hasForeground(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasBackground(Context ctx) {
        return Build.VERSION.SDK_INT < 29 || ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean anyLocationReminders(Context ctx) {
        for (Reminder r : Db.get(ctx).all()) if (!r.done && r.isLocation()) return true;
        return false;
    }

    static void register(Context ctx, Reminder r) {
        Db.Place p = Db.get(ctx).place(r.locPlace);
        if (p == null || !hasForeground(ctx)) return;
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        try {
            lm.addProximityAlert(p.lat, p.lng, Math.max(80, p.radius), -1, pi(ctx, r.id, true));
            Prefs.setStr(ctx, "geo_reg_" + r.id, String.valueOf(System.currentTimeMillis()));
        } catch (SecurityException ignored) { }
    }

    static void unregister(Context ctx, long id) {
        PendingIntent p = pi(ctx, id, false);
        if (p == null) return;
        try { ((LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE)).removeProximityAlert(p); } catch (Exception ignored) { }
        p.cancel();
    }

    /** Evita disparos falsos logo a seguir a registar (o sistema avisa logo se já estiver dentro/fora). */
    static boolean justRegistered(Context ctx, long id) {
        String s = Prefs.str(ctx, "geo_reg_" + id);
        try { return s != null && System.currentTimeMillis() - Long.parseLong(s) < 120000; } catch (NumberFormatException e) { return false; }
    }
}
