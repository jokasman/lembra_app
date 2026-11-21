package pt.lembra.ui;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import pt.lembra.R;
import pt.lembra.alarm.Notifier;
import pt.lembra.core.Categories;
import pt.lembra.core.Fmt;
import pt.lembra.core.PauseWindow;
import pt.lembra.core.Reminder;
import pt.lembra.data.Db;
import pt.lembra.data.Prefs;

/** Widget do ecrã inicial com os próximos lembretes. */
public class NextWidget extends AppWidgetProvider {
    private static final int[] ROWS = {R.id.row0, R.id.row1, R.id.row2, R.id.row3};
    private static final int[] TITLES = {R.id.title0, R.id.title1, R.id.title2, R.id.title3};
    private static final int[] TIMES = {R.id.time0, R.id.time1, R.id.time2, R.id.time3};
    private static final int[] DAYS = {R.id.day0, R.id.day1, R.id.day2, R.id.day3};
    private static final int[] DONES = {R.id.done0, R.id.done1, R.id.done2, R.id.done3};

    @Override public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) { update(ctx, m, ids); }

    public static void updateAll(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        if (m == null) return;
        int[] ids = m.getAppWidgetIds(new ComponentName(ctx, NextWidget.class));
        if (ids != null && ids.length > 0) update(ctx, m, ids);
    }

    static void update(Context ctx, AppWidgetManager m, int[] ids) {
        List<Reminder> pending = new ArrayList<Reminder>();
        for (Reminder r : Db.get(ctx).all()) if (!r.done && !(r.isLocation() && !r.isSnoozed() && !r.notified)) pending.add(r);
        Collections.sort(pending, new Comparator<Reminder>() {
            public int compare(Reminder a, Reminder b) { return Long.compare(a.effectiveTime(), b.effectiveTime()); }
        });
        long now = System.currentTimeMillis();
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        v.setViewVisibility(R.id.empty, pending.isEmpty() ? View.VISIBLE : View.GONE);
        String lastDay = null;
        for (int i = 0; i < ROWS.length; i++) {
            if (i < pending.size()) {
                Reminder r = pending.get(i);
                // Agrupado por dia: o cabeçalho só aparece quando o dia muda; à direita fica só a hora.
                String day = Fmt.cap(Fmt.day(r.effectiveTime(), now));
                v.setViewVisibility(DAYS[i], day.equals(lastDay) ? View.GONE : View.VISIBLE);
                v.setTextViewText(DAYS[i], day);
                lastDay = day;
                v.setViewVisibility(ROWS[i], View.VISIBLE);
                v.setTextViewText(TITLES[i], (r.important ? "★ " : "") + r.title);
                long eff = r.effectiveTime();
                v.setTextViewText(TIMES[i], Fmt.time(eff));
                // Visto à esquerda, na cor que antes tinha o ponto: vermelho se atrasado, senão a cor da categoria.
                v.setInt(DONES[i], "setColorFilter", eff < now ? 0xFFE53935 : Categories.color(r.category));
                // Tocar no lembrete abre a folha de ações: adiar, marcar como feito ou abrir.
                Intent actions = new Intent(ctx, SnoozeActivity.class).putExtra("id", r.id).putExtra("fromWidget", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                v.setOnClickPendingIntent(ROWS[i], PendingIntent.getActivity(ctx, 5000 + (int) r.id, actions,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
                // Marcar como feito: o mesmo que o botão "Feito" da notificação (pede PIN ou impressão digital se for exigido).
                v.setOnClickPendingIntent(DONES[i], Notifier.doneAction(ctx, r));
                v.setContentDescription(DONES[i], "Marcar como feito: " + r.title);
            } else {
                v.setViewVisibility(ROWS[i], View.GONE);
                v.setViewVisibility(DAYS[i], View.GONE);
            }
        }
        // Pausa: fundo e faixa na cor da pausa.
        long qs = Prefs.quietStart(ctx), qe = Prefs.quietEnd(ctx);
        int st = PauseWindow.state(qs, qe, now);
        v.setInt(R.id.root, "setBackgroundResource", st == PauseWindow.ACTIVE ? R.drawable.widget_bg_paused : R.drawable.widget_bg);
        v.setViewVisibility(R.id.paused, st == PauseWindow.NONE ? View.GONE : View.VISIBLE);
        if (st == PauseWindow.ACTIVE) v.setTextViewText(R.id.paused, "⏸ Em pausa até " + Fmt.dayTime(qe, now).toLowerCase());
        else if (st == PauseWindow.SCHEDULED) v.setTextViewText(R.id.paused, "⏸ Pausa às " + Fmt.dayTime(qs, now).toLowerCase());
        Intent pauseI = new Intent(ctx, PauseActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.paused, PendingIntent.getActivity(ctx, 4003, pauseI, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.header, PendingIntent.getActivity(ctx, 4000, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        v.setOnClickPendingIntent(R.id.empty, PendingIntent.getActivity(ctx, 4000, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        Intent add = new Intent(ctx, MainActivity.class).putExtra("focusQuick", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.add, PendingIntent.getActivity(ctx, 4001, add, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        Intent voice = new Intent(ctx, MainActivity.class).putExtra("startVoice", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.voice, PendingIntent.getActivity(ctx, 4002, voice, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        m.updateAppWidget(ids, v);
    }
}
