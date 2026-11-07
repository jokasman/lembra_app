package pt.lembra.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pt.lembra.R;
import pt.lembra.core.Categories;
import pt.lembra.core.Fmt;
import pt.lembra.core.Recurrence;
import pt.lembra.data.Db;

/** Histórico e estatísticas: o que foi feito, adiado e falhado. */
public class StatsActivity extends BaseActivity {
    private int days = 30;
    private LinearLayout body, periodRow;

    private int dp(float v) { return Ui.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(8), dp(8), dp(4));
        top.addView(Ui.iconButton(this, R.drawable.ic_back, "Voltar", new View.OnClickListener() { public void onClick(View v) { finish(); } }));
        TextView t = Ui.text(this, "Estatísticas", 20, R.color.text, true);
        t.setPadding(dp(6), 0, 0, 0);
        top.addView(t, Ui.lpWeight(1));
        top.addView(Ui.iconButton(this, R.drawable.ic_delete, "Limpar histórico", new View.OnClickListener() { public void onClick(View v) { confirmClear(); } }));
        root.addView(top);
        periodRow = new LinearLayout(this);
        periodRow.setPadding(dp(16), dp(2), dp(16), dp(6));
        root.addView(periodRow);
        ScrollView sv = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(32));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); load(); }

    private void confirmClear() {
        new AlertDialog.Builder(this).setMessage("Apagar todo o histórico? Os lembretes não são afetados.")
                .setPositiveButton("Apagar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { Db.get(StatsActivity.this).clearHistory(); load(); } })
                .setNegativeButton("Cancelar", null).show();
    }

    private void load() {
        periodRow.removeAllViews();
        int[] opts = {7, 30, 90};
        String[] labels = {"7 dias", "30 dias", "90 dias"};
        for (int i = 0; i < opts.length; i++) {
            final int o = opts[i];
            TextView c = Ui.chip(this, labels[i], days == o, 0);
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { days = o; load(); } });
            periodRow.addView(c);
        }

        body.removeAllViews();
        long now = System.currentTimeMillis();
        LocalDate today = LocalDate.now(Recurrence.zone);
        LocalDate start = today.minusDays(days - 1);
        long since = start.atStartOfDay(Recurrence.zone).toInstant().toEpochMilli();
        List<Db.Hist> hist = Db.get(this).history(since);

        int done = 0, snoozes = 0, missed = 0, onTime = 0;
        Map<Integer, Integer> byCat = new HashMap<Integer, Integer>();
        Map<String, Integer> snoozeByTitle = new HashMap<String, Integer>();
        Map<String, Long> titleToId = new HashMap<String, Long>();
        int buckets = days <= 30 ? days : (days + 6) / 7;
        int[] series = new int[buckets];
        for (Db.Hist h : hist) {
            if (h.action == Db.H_DONE) {
                done++;
                if (h.at - h.scheduledAt <= 15 * 60000L) onTime++;
                Integer c = byCat.get(h.category); byCat.put(h.category, c == null ? 1 : c + 1);
                LocalDate d = Instant.ofEpochMilli(h.at).atZone(Recurrence.zone).toLocalDate();
                int idx = (int) java.time.temporal.ChronoUnit.DAYS.between(start, d);
                if (days > 30) idx /= 7;
                if (idx >= 0 && idx < buckets) series[idx]++;
            } else if (h.action == Db.H_SNOOZE) {
                snoozes++;
                Integer c = snoozeByTitle.get(h.title); snoozeByTitle.put(h.title, c == null ? 1 : c + 1);
                titleToId.put(h.title, h.reminderId);
            } else if (h.action == Db.H_MISSED) missed++;
        }

        if (hist.isEmpty()) {
            LinearLayout c = Ui.card(this);
            c.addView(Ui.cardHeader(this, R.drawable.ic_chart, "Ainda sem dados"));
            c.addView(Ui.text(this, "À medida que for marcando lembretes como feitos ou adiando-os, aparecem aqui as suas estatísticas.", 14, R.color.text2, false));
            body.addView(c);
            return;
        }

        // KPIs
        LinearLayout kpi = Ui.card(this);
        kpi.addView(Ui.cardHeader(this, R.drawable.ic_chart, "Resumo"));
        LinearLayout r1 = new LinearLayout(this);
        r1.addView(tile(String.valueOf(done), "concluídos", R.color.accent), tileLp(true));
        r1.addView(tile(String.valueOf(snoozes), "adiamentos", R.color.text), tileLp(false));
        kpi.addView(r1, Ui.lpMW());
        LinearLayout r2 = new LinearLayout(this);
        r2.setPadding(0, dp(8), 0, 0);
        int rate = done + missed == 0 ? 0 : Math.round(100f * done / (done + missed));
        r2.addView(tile(rate + "%", "cumprimento", rate >= 80 ? R.color.accent : R.color.danger), tileLp(true));
        int ot = done == 0 ? 0 : Math.round(100f * onTime / done);
        r2.addView(tile(ot + "%", "feitos a horas", R.color.text), tileLp(false));
        kpi.addView(r2, Ui.lpMW());
        if (missed > 0) {
            TextView m = Ui.text(this, missed + (missed == 1 ? " ocorrência ficou" : " ocorrências ficaram") + " por fazer (passou a seguinte sem decisão).", 13, R.color.danger, false);
            m.setPadding(dp(2), dp(10), 0, 0);
            kpi.addView(m);
        }
        body.addView(kpi);

        // Gráfico de barras
        LinearLayout ch = Ui.card(this);
        ch.addView(Ui.cardHeader(this, R.drawable.ic_calendar, days > 30 ? "Concluídos por semana" : "Concluídos por dia"));
        String[] xl = new String[buckets];
        for (int i = 0; i < buckets; i++) {
            LocalDate d = days > 30 ? start.plusDays(i * 7L) : start.plusDays(i);
            xl[i] = days == 7 ? Fmt.DAYS_SHORT[d.getDayOfWeek().getValue() - 1] : String.valueOf(d.getDayOfMonth());
        }
        BarChart bc = new BarChart(this, series, xl, Ui.col(this, R.color.accent), Ui.col(this, R.color.text2), Ui.col(this, R.color.divider));
        ch.addView(bc, new LinearLayout.LayoutParams(-1, dp(170)));
        body.addView(ch);

        // Por categoria
        if (!byCat.isEmpty()) {
            LinearLayout cc = Ui.card(this);
            cc.addView(Ui.cardHeader(this, R.drawable.ic_label, "Concluídos por categoria"));
            List<Map.Entry<Integer, Integer>> ents = new ArrayList<Map.Entry<Integer, Integer>>(byCat.entrySet());
            Collections.sort(ents, new Comparator<Map.Entry<Integer, Integer>>() {
                public int compare(Map.Entry<Integer, Integer> a, Map.Entry<Integer, Integer> b) { return b.getValue() - a.getValue(); }
            });
            int max = ents.get(0).getValue();
            for (Map.Entry<Integer, Integer> e : ents) cc.addView(hbar(Categories.name(e.getKey()), e.getValue(), max, Categories.color(e.getKey())));
            body.addView(cc);
        }

        // Mais adiados
        if (!snoozeByTitle.isEmpty()) {
            LinearLayout sc = Ui.card(this);
            sc.addView(Ui.cardHeader(this, R.drawable.ic_snooze, "Mais adiados"));
            List<Map.Entry<String, Integer>> ents = new ArrayList<Map.Entry<String, Integer>>(snoozeByTitle.entrySet());
            Collections.sort(ents, new Comparator<Map.Entry<String, Integer>>() {
                public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) { return b.getValue() - a.getValue(); }
            });
            for (int i = 0; i < Math.min(5, ents.size()); i++) {
                final Map.Entry<String, Integer> e = ents.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(44));
                row.setBackground(Ui.ripple(this, null, dp(10)));
                TextView tt = Ui.text(this, e.getKey(), 15, R.color.text, false);
                Ui.ellipsize(tt, 1);
                row.addView(tt, Ui.lpWeight(1));
                row.addView(Ui.text(this, e.getValue() + (e.getValue() == 1 ? " vez" : " vezes"), 14, R.color.text2, true));
                final Long rid = titleToId.get(e.getKey());
                row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                    if (rid != null && Db.get(StatsActivity.this).get(rid) != null)
                        startActivity(new Intent(StatsActivity.this, EditActivity.class).putExtra("id", rid));
                } });
                sc.addView(row, Ui.lpMW());
            }
            TextView tip = Ui.text(this, "Dica: um lembrete adiado muitas vezes talvez precise de outra hora.", 12, R.color.text2, false);
            tip.setPadding(0, dp(6), 0, 0);
            sc.addView(tip);
            body.addView(sc);
        }

        // Histórico
        LinearLayout hc = Ui.card(this);
        hc.addView(Ui.cardHeader(this, R.drawable.ic_clock, "Histórico"));
        for (int i = 0; i < Math.min(60, hist.size()); i++) {
            Db.Hist h = hist.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));
            String badge = h.action == Db.H_DONE ? "✓" : h.action == Db.H_SNOOZE ? "⏱" : "✕";
            int bc2 = h.action == Db.H_DONE ? R.color.accent : h.action == Db.H_SNOOZE ? R.color.text2 : R.color.danger;
            TextView bd = Ui.text(this, badge, 15, bc2, true);
            bd.setGravity(Gravity.CENTER);
            row.addView(bd, new LinearLayout.LayoutParams(dp(28), -2));
            LinearLayout tx = new LinearLayout(this);
            tx.setOrientation(LinearLayout.VERTICAL);
            TextView tt = Ui.text(this, h.title, 15, R.color.text, false);
            Ui.ellipsize(tt, 1);
            tx.addView(tt);
            String act = h.action == Db.H_DONE ? "Feito" : h.action == Db.H_SNOOZE ? "Adiado" : "Ficou por fazer";
            tx.addView(Ui.text(this, act + " · " + Fmt.dayTime(h.at, now).toLowerCase(), 12, R.color.text2, false));
            row.addView(tx, Ui.lpWeight(1));
            hc.addView(row, Ui.lpMW());
        }
        body.addView(hc);
    }

    private LinearLayout.LayoutParams tileLp(boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(84), 1);
        if (first) lp.setMarginEnd(dp(8));
        return lp;
    }

    private View tile(String big, String label, int colorRes) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.round(Ui.col(this, R.color.chip), dp(16)));
        t.addView(Ui.text(this, big, 26, colorRes, true));
        t.addView(Ui.text(this, label, 13, R.color.text2, false));
        return t;
    }

    private View hbar(String label, int value, int max, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        LinearLayout line = new LinearLayout(this);
        line.addView(Ui.text(this, label, 14, R.color.text, false), Ui.lpWeight(1));
        line.addView(Ui.text(this, String.valueOf(value), 14, R.color.text2, true));
        row.addView(line, Ui.lpMW());
        LinearLayout track = new LinearLayout(this);
        track.setBackground(Ui.round(Ui.col(this, R.color.chip), dp(5)));
        View fill = new View(this);
        fill.setBackground(Ui.round(color, dp(5)));
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(10), Math.max(0.02f, value / (float) max)));
        View rest = new View(this);
        track.addView(rest, new LinearLayout.LayoutParams(0, dp(10), Math.max(0.0001f, 1f - value / (float) max)));
        LinearLayout.LayoutParams tlp = Ui.lpMW(); tlp.topMargin = dp(4);
        row.addView(track, tlp);
        return row;
    }

    /** Gráfico de barras simples desenhado à mão. */
    static class BarChart extends View {
        final int[] v; final String[] labels; final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG), grid = new Paint();
        BarChart(Context c, int[] values, String[] labels, int barColor, int textColor, int gridColor) {
            super(c);
            v = values; this.labels = labels;
            bar.setColor(barColor);
            txt.setColor(textColor);
            txt.setTextSize(Ui.dp(c, 10));
            txt.setTextAlign(Paint.Align.CENTER);
            grid.setColor(gridColor);
            grid.setStrokeWidth(Math.max(1, Ui.dp(c, 1)));
            int total = 0; for (int x : values) total += x;
            setContentDescription("Gráfico: " + total + " concluídos no período");
        }
        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            float top = Ui.dp(getContext(), 16), bottom = h - Ui.dp(getContext(), 18);
            int max = 1; for (int x : v) max = Math.max(max, x);
            cv.drawLine(0, bottom, w, bottom, grid);
            float slot = w / (float) v.length, bw = Math.max(Ui.dp(getContext(), 3), slot * 0.62f);
            int every = v.length <= 14 ? 1 : v.length <= 31 ? 5 : 2;
            for (int i = 0; i < v.length; i++) {
                float cx = slot * i + slot / 2;
                float bh = (bottom - top) * v[i] / max;
                if (v[i] > 0) cv.drawRoundRect(new RectF(cx - bw / 2, bottom - bh, cx + bw / 2, bottom), Ui.dp(getContext(), 3), Ui.dp(getContext(), 3), bar);
                if (v[i] > 0 && v.length <= 14) cv.drawText(String.valueOf(v[i]), cx, bottom - bh - Ui.dp(getContext(), 4), txt);
                if (i % every == 0 || i == v.length - 1) cv.drawText(labels[i], cx, h - Ui.dp(getContext(), 4), txt);
            }
        }
    }
}
