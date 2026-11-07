package pt.lembra.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

import pt.lembra.core.Recurrence;
import pt.lembra.core.Reminder;
import pt.lembra.data.Attachments;
import pt.lembra.data.Db;
import pt.lembra.data.Prefs;
import pt.lembra.ui.NextWidget;

/** Regras de negócio: agendar alarmes, concluir, adiar, disparar. */
public final class Engine {
    private Engine() {}

    public static final String A_FIRE = "pt.lembra.FIRE", A_PRE = "pt.lembra.PRE", A_NAG = "pt.lembra.NAG",
            A_DONE = "pt.lembra.DONE", A_SNOOZE = "pt.lembra.SNOOZE", A_STOP = "pt.lembra.STOP", A_GEO = "pt.lembra.GEO";
    static final int T_MAIN = 0, T_PRE = 1, T_NAG = 2;

    // ---------- Notificação de alterações à UI ----------
    private static final List<Runnable> listeners = new ArrayList<Runnable>();
    private static final Handler main = new Handler(Looper.getMainLooper());

    public static void addListener(Runnable r) { synchronized (listeners) { listeners.add(r); } }
    public static void removeListener(Runnable r) { synchronized (listeners) { listeners.remove(r); } }

    public static void changed(final Context ctx) {
        NextWidget.updateAll(ctx);
        AlarmService.sync(ctx);
        final List<Runnable> copy;
        synchronized (listeners) { copy = new ArrayList<Runnable>(listeners); }
        main.post(new Runnable() { public void run() { for (Runnable r : copy) r.run(); } });
    }

    // ---------- Alarmes ----------
    static PendingIntent pi(Context ctx, String action, long id, int type, boolean create) {
        Intent i = new Intent(ctx, AlarmReceiver.class).setAction(action).putExtra("id", id);
        int flags = PendingIntent.FLAG_IMMUTABLE | (create ? PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_NO_CREATE);
        return PendingIntent.getBroadcast(ctx, (int) (id * 4 + type), i, flags);
    }

    public static boolean canExact(Context ctx) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        return am.canScheduleExactAlarms();
    }

    static void setAlarm(Context ctx, long at, PendingIntent p) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        }
    }

    static void cancelAlarm(Context ctx, String action, long id, int type) {
        PendingIntent p = pi(ctx, action, id, type, false);
        if (p != null) {
            ((AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE)).cancel(p);
            p.cancel();
        }
    }

    public static void cancelAll(Context ctx, long id) {
        Geo.unregister(ctx, id);
        cancelAlarm(ctx, A_FIRE, id, T_MAIN);
        cancelAlarm(ctx, A_PRE, id, T_PRE);
        cancelAlarm(ctx, A_NAG, id, T_NAG);
    }

    /** (Re)agenda todos os alarmes de um lembrete de acordo com o seu estado. */
    public static void schedule(Context ctx, Reminder r) {
        cancelAll(ctx, r.id);
        if (r.done) return;
        long now = System.currentTimeMillis();
        if (r.isLocation()) {
            if (r.isSnoozed() && !r.notified) {
                // Adiado: passa a ser um alarme por hora até essa altura.
                setAlarm(ctx, Math.max(r.snoozedUntil, now + 1000), pi(ctx, A_FIRE, r.id, T_MAIN, true));
            } else if (!r.notified) {
                Geo.register(ctx, r);
            } else if (r.important) {
                setAlarm(ctx, now + Prefs.nagInterval(ctx) * 60000L, pi(ctx, A_NAG, r.id, T_NAG, true));
            }
            return;
        }
        long eff = r.effectiveTime();
        if (!r.notified) {
            setAlarm(ctx, Math.max(eff, now + 1000), pi(ctx, A_FIRE, r.id, T_MAIN, true));
            if (r.preAlertMin > 0 && !r.preNotified && !r.isSnoozed()) {
                long pre = eff - r.preAlertMin * 60000L;
                if (pre > now) setAlarm(ctx, pre, pi(ctx, A_PRE, r.id, T_PRE, true));
            }
        } else {
            // Ocorrência atual já notificada e pendente.
            if (r.isRecurring()) {
                long next = Recurrence.next(r, r.dueAt, r.count + 1);
                if (next > 0) setAlarm(ctx, Math.max(next, now + 1000), pi(ctx, A_FIRE, r.id, T_MAIN, true));
            }
            if (r.important) {
                setAlarm(ctx, now + Prefs.nagInterval(ctx) * 60000L, pi(ctx, A_NAG, r.id, T_NAG, true));
            }
        }
    }

    public static void rescheduleAll(Context ctx) {
        for (Reminder r : Db.get(ctx).all()) if (!r.done) schedule(ctx, r);
        changed(ctx);
    }

    // ---------- Ações ----------

    /** Guarda e agenda. */
    public static void save(Context ctx, Reminder r) {
        Db.get(ctx).save(r);
        Notifier.cancel(ctx, r.id);
        AlarmService.stop(r.id);
        schedule(ctx, r);
        changed(ctx);
    }

    public static void delete(Context ctx, Reminder r) {
        cancelAll(ctx, r.id);
        Notifier.cancel(ctx, r.id);
        AlarmService.stop(r.id);
        Attachments.deleteFilesFor(ctx, r.id);
        Db.get(ctx).delete(r.id);
        changed(ctx);
    }

    /** Restaura um estado anterior (anular). */
    public static void restore(Context ctx, Reminder snapshot) {
        Reminder r = snapshot.copy();
        Db.get(ctx).save(r);
        schedule(ctx, r);
        changed(ctx);
    }

    /** Marca como feito. Nos recorrentes avança para a próxima ocorrência. */
    public static void complete(Context ctx, Reminder r) {
        long now = System.currentTimeMillis();
        Notifier.cancel(ctx, r.id);
        AlarmService.stop(r.id);
        Db.get(ctx).log(r, Db.H_DONE);
        if (r.isLocation() && r.locRepeat) {
            // "Sempre que chegar/sair": volta a ficar à espera do local.
            r.resetAlertState();
            r.snoozedUntil = 0;
            Db.get(ctx).resetSubtasks(r.id);
        } else if (r.isRecurring() && Recurrence.advance(r, now)) {
            Db.get(ctx).resetSubtasks(r.id); // nova ocorrência: checklist limpa
        } else {
            r.done = true;
            r.doneAt = now;
            r.snoozedUntil = 0;
        }
        Db.get(ctx).save(r);
        schedule(ctx, r);
        changed(ctx);
    }

    public static void uncomplete(Context ctx, Reminder r) {
        r.done = false;
        r.doneAt = 0;
        r.resetAlertState();
        long now = System.currentTimeMillis();
        if (r.dueAt < now && r.isRecurring()) Recurrence.advance(r, now);
        save(ctx, r);
    }

    public static void snooze(Context ctx, Reminder r, long until) {
        Notifier.cancel(ctx, r.id);
        AlarmService.stop(r.id);
        Db.get(ctx).log(r, Db.H_SNOOZE);
        r.snoozedUntil = until;
        r.notified = false;
        r.preNotified = true;
        Db.get(ctx).save(r);
        schedule(ctx, r);
        changed(ctx);
    }

    /** Disparo do alarme principal. */
    public static void fire(Context ctx, Reminder r) {
        long now = System.currentTimeMillis();
        if (r.done) return;
        if (r.notified && r.isRecurring()) {
            // A ocorrência anterior ficou por tratar e chegou a seguinte: avança.
            Db.get(ctx).log(r, Db.H_MISSED);
            Db.get(ctx).resetSubtasks(r.id);
            if (!Recurrence.advance(r, now - 60000)) {
                r.done = true; r.doneAt = now;
                Db.get(ctx).save(r);
                Notifier.cancel(ctx, r.id);
                AlarmService.stop(r.id);
                changed(ctx);
                return;
            }
        } else if (!(r.isLocation() && !r.isSnoozed()) && r.effectiveTime() > now + 60000) {
            schedule(ctx, r); // alarme desatualizado
            return;
        }
        r.notified = true;
        if (r.isLocation() && !r.isSnoozed()) r.dueAt = now; // hora a que chegou/saiu
        Db.get(ctx).save(r);
        if (r.alarm) AlarmService.start(ctx, r.id); else Notifier.showMain(ctx, r, false);
        schedule(ctx, r);
        changed(ctx);
    }
}
