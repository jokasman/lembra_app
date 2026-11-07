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
import pt.lembra.core.Categories;
import pt.lembra.core.Fmt;
import pt.lembra.core.Reminder;
import pt.lembra.data.Db;

/** Widget do ecrã inicial com os próximos lembretes. */
public class NextWidget extends AppWidgetProvider {
    private static final int[] ROWS = {R.id.row0, R.id.row1, R.id.row2, R.id.row3};
    private static final int[] TITLES = {R.id.title0, R.id.title1, R.id.title2, R.id.title3};
    private static final int[] TIMES = {R.id.time0, R.id.time1, R.id.time2, R.id.time3};
    private static final int[] DOTS = {R.id.dot0, R.id.dot1, R.id.dot2, R.id.dot3};

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
        for (int i = 0; i < ROWS.length; i++) {
            if (i < pending.size()) {
                Reminder r = pending.get(i);
                v.setViewVisibility(ROWS[i], View.VISIBLE);
                v.setTextViewText(TITLES[i], (r.important ? "★ " : "") + r.title);
                long eff = r.effectiveTime();
                v.setTextViewText(TIMES[i], Fmt.dayTime(eff, now));
                v.setTextColor(DOTS[i], eff < now ? 0xFFE53935 : Categories.color(r.category));
                Intent edit = new Intent(ctx, EditActivity.class).putExtra("id", r.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                v.setOnClickPendingIntent(ROWS[i], PendingIntent.getActivity(ctx, 5000 + (int) r.id, edit,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            } else {
                v.setViewVisibility(ROWS[i], View.GONE);
            }
        }
        Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.header, PendingIntent.getActivity(ctx, 4000, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        v.setOnClickPendingIntent(R.id.empty, PendingIntent.getActivity(ctx, 4000, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        Intent add = new Intent(ctx, MainActivity.class).putExtra("focusQuick", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.add, PendingIntent.getActivity(ctx, 4001, add, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        m.updateAppWidget(ids, v);
    }
}
