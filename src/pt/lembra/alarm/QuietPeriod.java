package pt.lembra.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.List;
import java.util.Collections;
import java.util.Comparator;

import pt.lembra.core.Reminder;
import pt.lembra.data.Db;
import pt.lembra.data.Prefs;

/** Pausa única dos despertadores entre duas datas e horas. */
public final class QuietPeriod {
    public static final String A_START = "pt.lembra.QUIET_START", A_END = "pt.lembra.QUIET_END";
    private static final int PI_START = 999_997, PI_END = 999_998;

    private QuietPeriod() {}

    public static boolean isActive(Context ctx) { return isActive(ctx, System.currentTimeMillis()); }

    private static boolean isActive(Context ctx, long now) {
        long start = Prefs.quietStart(ctx), end = Prefs.quietEnd(ctx);
        return start > 0 && end > start && now >= start && now < end;
    }

    public static long end(Context ctx) { return Prefs.quietEnd(ctx); }

    public static void set(Context ctx, long start, long end) {
        if (start <= 0 || end <= start || end <= System.currentTimeMillis()) return;
        boolean wasActive = isActive(ctx);
        long oldEnd = Prefs.quietEnd(ctx);
        cancelBoundary(ctx, A_START, PI_START);
        cancelBoundary(ctx, A_END, PI_END);
        Prefs.setQuietPeriod(ctx, start, end);
        rescheduleBoundaries(ctx, start, end);
        if (isActive(ctx)) {
            moveDeferredLocations(ctx, oldEnd, end);
            AlarmService.deferForQuiet();
        }
        else if (wasActive) releaseOverdue(ctx, oldEnd);
    }

    public static void clear(Context ctx) {
        boolean wasActive = isActive(ctx);
        long oldEnd = Prefs.quietEnd(ctx);
        cancelBoundary(ctx, A_START, PI_START);
        cancelBoundary(ctx, A_END, PI_END);
        Prefs.clearQuietPeriod(ctx);
        if (wasActive) releaseOverdue(ctx, oldEnd);
    }

    /** Reinstala os limites após reinício, atualização ou mudança do relógio. */
    static void reschedule(Context ctx) {
        long start = Prefs.quietStart(ctx), end = Prefs.quietEnd(ctx), now = System.currentTimeMillis();
        cancelBoundary(ctx, A_START, PI_START);
        cancelBoundary(ctx, A_END, PI_END);
        if (start <= 0 || end <= start) return;
        if (end <= now) {
            Prefs.clearQuietPeriod(ctx);
            releaseOverdue(ctx, end);
            return;
        }
        rescheduleBoundaries(ctx, start, end);
        if (isActive(ctx, now)) AlarmService.deferForQuiet();
    }

    static void onStart(Context ctx) {
        if (isActive(ctx)) AlarmService.deferForQuiet();
        else reschedule(ctx);
    }

    static void onEnd(Context ctx) {
        long end = Prefs.quietEnd(ctx);
        if (end > 0 && System.currentTimeMillis() >= end) {
            cancelBoundary(ctx, A_START, PI_START);
            cancelBoundary(ctx, A_END, PI_END);
            Prefs.clearQuietPeriod(ctx);
            releaseOverdue(ctx, end);
        } else reschedule(ctx);
    }

    private static void rescheduleBoundaries(Context ctx, long start, long end) {
        long now = System.currentTimeMillis();
        if (start > now) scheduleBoundary(ctx, A_START, PI_START, start);
        scheduleBoundary(ctx, A_END, PI_END, end);
    }

    private static void scheduleBoundary(Context ctx, String action, int requestCode, long at) {
        Intent i = new Intent(ctx, AlarmReceiver.class).setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCode, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Engine.setAlarm(ctx, Math.max(at, System.currentTimeMillis() + 1000), pi);
    }

    private static void cancelBoundary(Context ctx, String action, int requestCode) {
        Intent i = new Intent(ctx, AlarmReceiver.class).setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCode, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
        if (pi != null) {
            ((AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE)).cancel(pi);
            pi.cancel();
        }
    }

    private static void moveDeferredLocations(Context ctx, long oldEnd, long newEnd) {
        if (oldEnd <= 0 || oldEnd == newEnd) return;
        for (Reminder r : Db.get(ctx).all()) {
            if (!r.done && r.isLocation() && !r.notified && r.snoozedUntil == oldEnd) {
                r.snoozedUntil = newEnd;
                Db.get(ctx).save(r);
                Engine.schedule(ctx, r);
            }
        }
    }

    /** Reproduz por ordem os alarmes que venceram durante a pausa. */
    private static void releaseOverdue(Context ctx, long quietEnd) {
        long now = System.currentTimeMillis();
        List<Reminder> reminders = Db.get(ctx).all();
        Collections.sort(reminders, new Comparator<Reminder>() {
            public int compare(Reminder a, Reminder b) {
                return Long.compare(a.effectiveTime(), b.effectiveTime());
            }
        });
        for (Reminder r : reminders) {
            boolean deferredLocation = r.isLocation() && !r.notified && quietEnd > 0 && r.snoozedUntil == quietEnd;
            if (r.done || !r.alarm || (r.effectiveTime() > now && !deferredLocation)) continue;
            if (r.isLocation() && !r.notified && !r.isSnoozed()) continue;
            if (r.notified) {
                Engine.schedule(ctx, r);
                AlarmService.start(ctx, r.id);
            } else {
                if (deferredLocation) {
                    r.snoozedUntil = 0;
                    Db.get(ctx).save(r);
                }
                Engine.fire(ctx, r);
            }
        }
    }
}
