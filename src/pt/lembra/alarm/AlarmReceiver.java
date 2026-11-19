package pt.lembra.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import pt.lembra.core.Reminder;
import pt.lembra.data.Db;
import pt.lembra.data.Prefs;

/** Recebe alarmes e ações das notificações. */
public class AlarmReceiver extends BroadcastReceiver {
    static final String A_TEST = "pt.lembra.TEST";

    /** Agenda uma notificação de teste para verificar a fiabilidade. */
    public static void scheduleTest(Context ctx, long delayMs) {
        android.app.PendingIntent p = android.app.PendingIntent.getBroadcast(ctx, 999_999,
                new Intent(ctx, AlarmReceiver.class).setAction(A_TEST),
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        Engine.setAlarm(ctx, System.currentTimeMillis() + delayMs, p);
    }

    @Override public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        long id = intent.getLongExtra("id", -1);
        if (QuietPeriod.A_START.equals(a)) { QuietPeriod.onStart(ctx); return; }
        if (QuietPeriod.A_END.equals(a)) { QuietPeriod.onEnd(ctx); return; }
        if (A_TEST.equals(a)) { AlarmService.start(ctx, AlarmService.TEST_ID); return; }
        if (Engine.A_STOP.equals(a)) { AlarmService.stop(null); return; }
        if (a == null || id < 0) return;
        Reminder r = Db.get(ctx).get(id);
        if (r == null) { Notifier.cancel(ctx, id); return; }
        switch (a) {
            case Engine.A_GEO: {
                boolean entering = intent.getBooleanExtra(android.location.LocationManager.KEY_PROXIMITY_ENTERING, false);
                if (r.done || !r.isLocation() || r.notified || r.isSnoozed()) break;
                boolean match = (entering && r.locTrigger == Reminder.LOC_ARRIVE) || (!entering && r.locTrigger == Reminder.LOC_LEAVE);
                if (match && !Geo.justRegistered(ctx, r.id)) {
                    if (r.alarm && QuietPeriod.isActive(ctx)) {
                        r.snoozedUntil = QuietPeriod.end(ctx);
                        r.preNotified = true;
                        Db.get(ctx).save(r);
                        Engine.schedule(ctx, r);
                    } else Engine.fire(ctx, r);
                }
                break;
            }
            case Engine.A_FIRE:
                Engine.fire(ctx, r);
                break;
            case Engine.A_PRE:
                if (!r.done && !r.notified) {
                    if (r.alarm && QuietPeriod.isActive(ctx)) break;
                    r.preNotified = true;
                    Db.get(ctx).save(r);
                    Notifier.showPre(ctx, r);
                }
                break;
            case Engine.A_NAG:
                if (!r.done && r.notified && r.important) {
                    if (r.alarm && QuietPeriod.isActive(ctx)) break;
                    if (r.alarm) AlarmService.start(ctx, r.id); else Notifier.showMain(ctx, r, true);
                    Engine.schedule(ctx, r);
                }
                break;
            case Engine.A_DONE:
                if (r.needsDoneAuth(Prefs.doneAuth(ctx))) {
                    // Botão de uma notificação antiga, ou do relógio: a conclusão exige autenticação no telemóvel.
                    try { ctx.startActivity(pt.lembra.ui.DoneActivity.intent(ctx, r.id)); } catch (Exception e) { /* sem permissão para abrir */ }
                    break;
                }
                Engine.complete(ctx, r);
                break;
            case Engine.A_SNOOZE: {
                int min = intent.getIntExtra("min", 10);
                Engine.snooze(ctx, r, System.currentTimeMillis() + min * 60000L);
                break;
            }
        }
    }
}
