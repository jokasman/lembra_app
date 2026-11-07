package pt.lembra.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;

import pt.lembra.R;
import pt.lembra.core.Categories;
import pt.lembra.core.Fmt;
import pt.lembra.core.Recurrence;
import pt.lembra.core.Reminder;
import pt.lembra.data.Prefs;
import pt.lembra.ui.AlarmActivity;
import pt.lembra.ui.EditActivity;
import pt.lembra.ui.SnoozeActivity;

/** Construção das notificações. */
public final class Notifier {
    private Notifier() {}

    static final String CH_MAIN = "lembretes", CH_IMP = "importantes", CH_PRE = "avisos", CH_ALARM_SOUND = "alarme_som",
            CH_WATCH_ALARM = "despertador_relogio";
    private static int watchAlarmId(long id) { return 0x60000000 | ((int) id & 0x0FFFFFFF); }

    /** Som de alarme incluído na app (funciona sempre, sem depender do som do sistema). */
    public static android.net.Uri bundledTone(Context ctx) {
        return android.net.Uri.parse("android.resource://" + ctx.getPackageName() + "/" + R.raw.alarm_tone);
    }

    public static void ensureChannels(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        NotificationChannel main = new NotificationChannel(CH_MAIN, "Lembretes", NotificationManager.IMPORTANCE_HIGH);
        main.setDescription("Notificações na hora do lembrete");
        main.enableVibration(true);
        main.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationChannel imp = new NotificationChannel(CH_IMP, "Lembretes importantes", NotificationManager.IMPORTANCE_HIGH);
        imp.setDescription("Lembretes em modo insistente: repetem até marcar como feito");
        imp.enableVibration(true);
        imp.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 600});
        imp.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        imp.enableLights(true);
        imp.setLightColor(0xFFFF5252);
        NotificationChannel pre = new NotificationChannel(CH_PRE, "Avisos antecipados", NotificationManager.IMPORTANCE_DEFAULT);
        pre.setDescription("Aviso alguns minutos antes do lembrete");
        nm.createNotificationChannel(main);
        nm.createNotificationChannel(imp);
        nm.createNotificationChannel(pre);
        // Canal de reserva para despertadores: som no fluxo de ALARME (ouve-se em silêncio), repetido até agir.
        NotificationChannel al = new NotificationChannel(CH_ALARM_SOUND, "Despertador (reserva)", NotificationManager.IMPORTANCE_HIGH);
        al.setDescription("Usado se o ecrã do despertador não puder abrir");
        al.setSound(bundledTone(ctx), new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        al.enableVibration(true);
        al.setVibrationPattern(new long[]{0, 700, 500, 700, 500, 700});
        al.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(al);
        NotificationChannel watch = new NotificationChannel(CH_WATCH_ALARM, "Controlos no relógio", NotificationManager.IMPORTANCE_DEFAULT);
        watch.setDescription("Notificação do despertador com ações para adiar ou parar");
        watch.setSound(null, null);
        watch.enableVibration(false);
        watch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(watch);
    }

    static int mainId(long id) { return (int) (id * 2); }
    static int preId(long id) { return (int) (id * 2 + 1); }

    public static void cancel(Context ctx, long id) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.cancel(mainId(id));
        nm.cancel(preId(id));
        cancelWatchAlarm(ctx, id);
    }

    /** Notificação normal, separada da foreground, para poder ser espelhada no Wear OS. */
    public static void showWatchAlarm(Context ctx, Reminder r) {
        ensureChannels(ctx);
        int quick = Prefs.quickSnooze(ctx);
        Intent open = new Intent(ctx, AlarmActivity.class).putExtra("id", r.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(ctx, 777_004, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String body = "Alarme a tocar · " + Fmt.time(r.effectiveTime());
        Notification n = new Notification.Builder(ctx, CH_WATCH_ALARM)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(Categories.color(r.category))
                .setContentTitle("⏰ " + r.title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setWhen(r.effectiveTime()).setShowWhen(true)
                .setContentIntent(openPi)
                .setLocalOnly(false)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, "Adiar " + Fmt.duration(quick),
                        action(ctx, Engine.A_SNOOZE, r.id, 1, quick)).build())
                .addAction(new Notification.Action.Builder(null, "Parar",
                        action(ctx, Engine.A_DONE, r.id, 0, 0)).build())
                .build();
        ctx.getSystemService(NotificationManager.class).notify(watchAlarmId(r.id), n);
    }

    public static void cancelWatchAlarm(Context ctx, long id) {
        ctx.getSystemService(NotificationManager.class).cancel(watchAlarmId(id));
    }

    static PendingIntent openIntent(Context ctx, long id) {
        Intent i = new Intent(ctx, EditActivity.class).putExtra("id", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx, (int) id, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static PendingIntent action(Context ctx, String action, long id, int code, int minutes) {
        Intent i = new Intent(ctx, AlarmReceiver.class).setAction(action).putExtra("id", id).putExtra("min", minutes);
        return PendingIntent.getBroadcast(ctx, (int) (id * 8 + code) + 1_000_000, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void showMain(Context ctx, Reminder r, boolean nag) {
        ensureChannels(ctx);
        long now = System.currentTimeMillis();
        StringBuilder text = new StringBuilder();
        long eff = r.effectiveTime();
        text.append(Fmt.dayTime(eff, now));
        if (r.isRecurring()) text.append(" · ").append(Recurrence.describe(r));
        if (r.category > 0) text.append(" · ").append(Categories.name(r.category));
        String big = text.toString();
        if (r.notes != null && !r.notes.trim().isEmpty()) big = r.notes.trim() + "\n" + big;

        int quick = Prefs.quickSnooze(ctx);
        Intent snoozeI = new Intent(ctx, SnoozeActivity.class).putExtra("id", r.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent snoozePi = PendingIntent.getActivity(ctx, (int) (r.id * 8 + 3) + 2_000_000, snoozeI,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = new Notification.Builder(ctx, r.alarm ? CH_ALARM_SOUND : r.important ? CH_IMP : CH_MAIN)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(Categories.color(r.category))
                .setContentTitle((r.important ? "⚠ " : "") + r.title)
                .setContentText(r.notes != null && !r.notes.trim().isEmpty() ? r.notes.trim() : text)
                .setStyle(new Notification.BigTextStyle().bigText(big))
                .setSubText(nag ? "Ainda por fazer" : null)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setWhen(eff).setShowWhen(true)
                .setContentIntent(openIntent(ctx, r.id))
                .setAutoCancel(!r.important)
                .setOngoing(r.important)
                .setOnlyAlertOnce(false)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(null, "Feito", action(ctx, Engine.A_DONE, r.id, 0, 0)).build())
                .addAction(new Notification.Action.Builder(null, "+" + Fmt.duration(quick), action(ctx, Engine.A_SNOOZE, r.id, 1, quick)).build())
                .addAction(new Notification.Action.Builder(null, "Adiar…", snoozePi).build());
        Notification built = b.build();
        if (r.alarm) built.flags |= Notification.FLAG_INSISTENT; // repete o som até abrir/agir
        ctx.getSystemService(NotificationManager.class).notify(mainId(r.id), built);
    }

    public static void showPre(Context ctx, Reminder r) {
        ensureChannels(ctx);
        long eff = r.effectiveTime();
        Notification.Builder b = new Notification.Builder(ctx, CH_PRE)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(Categories.color(r.category))
                .setContentTitle(r.title)
                .setContentText("Daqui a " + Fmt.duration(r.preAlertMin) + " (" + Fmt.time(eff) + ")")
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(openIntent(ctx, r.id))
                .setAutoCancel(true)
                .setTimeoutAfter(Math.max(60000, eff - System.currentTimeMillis()))
                .addAction(new Notification.Action.Builder(null, "Já está feito", action(ctx, Engine.A_DONE, r.id, 0, 0)).build());
        ctx.getSystemService(NotificationManager.class).notify(preId(r.id), b.build());
    }

    public static void showTest(Context ctx) {
        ensureChannels(ctx);
        Notification n = new Notification.Builder(ctx, CH_MAIN)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Teste do Lembra")
                .setContentText("Se está a ler isto, os lembretes estão a funcionar.")
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true).build();
        ctx.getSystemService(NotificationManager.class).notify(999_999, n);
    }
}
