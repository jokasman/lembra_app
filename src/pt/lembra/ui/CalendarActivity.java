package pt.lembra.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pt.lembra.R;
import pt.lembra.alarm.Engine;
import pt.lembra.core.Categories;
import pt.lembra.core.Fmt;
import pt.lembra.core.Occurrences;
import pt.lembra.core.Recurrence;
import pt.lembra.core.Reminder;
import pt.lembra.data.Db;
import pt.lembra.data.Prefs;

/** Vista de calendário mensal: pontos por dia (incluindo recorrências) e lista do dia selecionado. */
public class CalendarActivity extends BaseActivity {
    private YearMonth month;
    private LocalDate selected;
    private TextView monthTitle, dayTitle;
    private LinearLayout grid, dayList;
    /** epochDay → ocorrências desse dia, ordenadas por hora. */
    private final Map<Long, List<Occ>> byDay = new HashMap<Long, List<Occ>>();
    /** Categorias selecionadas no filtro (vazio = todas). Guardado entre aberturas. */
    private final java.util.Set<Integer> catFilter = new java.util.HashSet<Integer>();
    private LinearLayout filterRow;
    private final Runnable refresher = new Runnable() { public void run() { load(); } };

    static final class Occ {
        final Reminder r; final long at; boolean conflict;
        Occ(Reminder r, long at) { this.r = r; this.at = at; }
    }

    private int dp(float v) { return Ui.dp(this, v); }
    private GestureDetector gd;

    /** Deslizar na horizontal muda de mês. */
    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gd != null) gd.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        selected = LocalDate.now(Recurrence.zone);
        month = YearMonth.from(selected);

        FrameLayout root = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(8), dp(8), dp(4));
        top.addView(Ui.iconButton(this, R.drawable.ic_back, "Voltar", new View.OnClickListener() { public void onClick(View v) { finish(); } }));
        monthTitle = Ui.text(this, "", 20, R.color.text, true);
        monthTitle.setPadding(dp(6), 0, 0, 0);
        top.addView(monthTitle, Ui.lpWeight(1));
        TextView today = Ui.chip(this, "Hoje", false, 0);
        today.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            selected = LocalDate.now(Recurrence.zone); month = YearMonth.from(selected); load();
        } });
        top.addView(today);
        top.addView(Ui.iconButton(this, R.drawable.ic_prev, "Mês anterior", new View.OnClickListener() { public void onClick(View v) { shift(-1); } }));
        top.addView(Ui.iconButton(this, R.drawable.ic_next, "Mês seguinte", new View.OnClickListener() { public void onClick(View v) { shift(1); } }));
        col.addView(top);

        // Filtro por categoria (seleção múltipla; "Todas" limpa o filtro)
        loadFilter();
        android.widget.HorizontalScrollView fs = new android.widget.HorizontalScrollView(this);
        fs.setHorizontalScrollBarEnabled(false);
        fs.setHorizontalFadingEdgeEnabled(true);
        fs.setFadingEdgeLength(dp(24));
        filterRow = new LinearLayout(this);
        filterRow.setPadding(dp(16), dp(2), dp(16), dp(6));
        fs.addView(filterRow);
        col.addView(fs);

        // Cabeçalho dos dias da semana (segunda primeiro)
        LinearLayout wd = new LinearLayout(this);
        wd.setPadding(dp(12), dp(4), dp(12), dp(2));
        String[] names = {"S", "T", "Q", "Q", "S", "S", "D"};
        for (String n : names) {
            TextView t = Ui.text(this, n, 12, R.color.text2, true);
            t.setGravity(Gravity.CENTER);
            wd.addView(t, Ui.lpWeight(1));
        }
        col.addView(wd);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(12), 0, dp(12), dp(6));
        col.addView(grid);
        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return false; }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dx) > dp(60) && Math.abs(vx) > Math.abs(vy)) { shift(dx < 0 ? 1 : -1); return true; }
                return false;
            }
        });

        View div = new View(this);
        div.setBackgroundColor(Ui.col(this, R.color.divider));
        col.addView(div, new LinearLayout.LayoutParams(-1, 1));

        dayTitle = Ui.text(this, "", 15, R.color.text, true);
        dayTitle.setPadding(dp(20), dp(12), dp(20), dp(4));
        col.addView(dayTitle);
        ScrollView sv = new ScrollView(this);
        dayList = new LinearLayout(this);
        dayList.setOrientation(LinearLayout.VERTICAL);
        dayList.setPadding(0, 0, 0, dp(96));
        sv.addView(dayList);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        ImageView fab = Ui.icon(this, R.drawable.ic_add, R.color.onAccent, 60, 16);
        fab.setBackground(Ui.ripple(this, Ui.round(Ui.col(this, R.color.accent), dp(20)), dp(20)));
        fab.setElevation(dp(6));
        fab.setContentDescription("Novo lembrete neste dia");
        fab.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Intent i = new Intent(CalendarActivity.this, EditActivity.class).putExtra("day", selected.toEpochDay());
            if (catFilter.size() == 1) i.putExtra("category", catFilter.iterator().next()); // pré-seleciona a categoria filtrada
            startActivity(i);
        } });
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.BOTTOM | Gravity.END);
        flp.setMargins(0, 0, dp(20), dp(24));
        root.addView(fab, flp);
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); Engine.addListener(refresher); load(); }
    @Override protected void onPause() { super.onPause(); Engine.removeListener(refresher); }

    private void loadFilter() {
        catFilter.clear();
        String s = Prefs.str(this, "cal_filter");
        if (s == null || s.isEmpty()) return;
        for (String p : s.split(",")) {
            try { int id = Integer.parseInt(p.trim()); if (Categories.get(id) != null) catFilter.add(id); } catch (NumberFormatException ignored) { }
        }
    }

    private void saveFilter() {
        StringBuilder sb = new StringBuilder();
        for (Integer id : catFilter) { if (sb.length() > 0) sb.append(','); sb.append(id); }
        Prefs.setStr(this, "cal_filter", sb.toString());
    }

    private void renderFilter() {
        if (filterRow == null) return;
        // Remove categorias que entretanto foram apagadas
        java.util.Iterator<Integer> it = catFilter.iterator();
        while (it.hasNext()) if (Categories.get(it.next()) == null) it.remove();
        filterRow.removeAllViews();
        TextView all = Ui.chip(this, "Todas", catFilter.isEmpty(), 0);
        all.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { catFilter.clear(); saveFilter(); load(); } });
        filterRow.addView(all);
        for (final Categories.Cat ct : Categories.all()) {
            TextView c = Ui.chip(this, ct.name, catFilter.contains(ct.id), ct.color);
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (!catFilter.remove(ct.id)) catFilter.add(ct.id);
                saveFilter();
                load();
            } });
            filterRow.addView(c);
        }
    }

    private void shift(int months) {
        month = month.plusMonths(months);
        selected = month.atDay(Math.min(selected.getDayOfMonth(), month.lengthOfMonth()));
        if (YearMonth.from(LocalDate.now(Recurrence.zone)).equals(month)) selected = LocalDate.now(Recurrence.zone);
        load();
    }

    private long ms(LocalDate d) { return d.atStartOfDay(Recurrence.zone).toInstant().toEpochMilli(); }

    private void load() {
        LocalDate gridStart = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate gridEnd = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        long from = ms(gridStart), to = ms(gridEnd.plusDays(1)) - 1;
        byDay.clear();
        renderFilter();
        for (Reminder r : Db.get(this).all()) {
            if (!catFilter.isEmpty() && !catFilter.contains(r.category)) continue;
            if (r.done) {
                if (r.doneAt >= from && r.doneAt <= to && !r.isRecurring()) add(new Occ(r, r.dueAt));
                continue;
            }
            for (Long t : Occurrences.between(r, from, to, 200)) add(new Occ(r, t));
        }
        int tol = Prefs.conflictTolerance(this);
        for (List<Occ> l : byDay.values()) {
            Collections.sort(l, new Comparator<Occ>() { public int compare(Occ a, Occ b) { return Long.compare(a.at, b.at); } });
            if (tol < 0) continue;
            for (int i = 0; i < l.size(); i++) for (int j = i + 1; j < l.size(); j++) {
                Occ a = l.get(i), b = l.get(j);
                if (a.r.done || b.r.done) continue;
                if (Math.abs(a.at / 60000 - b.at / 60000) <= tol) { a.conflict = true; b.conflict = true; }
            }
        }
        String mn = Fmt.cap(Fmt.MONTHS[month.getMonthValue() - 1]);
        monthTitle.setText(mn + " " + month.getYear());
        buildGrid(gridStart, gridEnd);
        buildDay();
    }

    private void add(Occ o) {
        long day = Instant.ofEpochMilli(o.at).atZone(Recurrence.zone).toLocalDate().toEpochDay();
        List<Occ> l = byDay.get(day);
        if (l == null) { l = new ArrayList<Occ>(); byDay.put(day, l); }
        l.add(o);
    }

    private void buildGrid(LocalDate start, LocalDate end) {
        grid.removeAllViews();
        LocalDate today = LocalDate.now(Recurrence.zone);
        LocalDate d = start;
        while (!d.isAfter(end)) {
            LinearLayout week = new LinearLayout(this);
            for (int i = 0; i < 7; i++, d = d.plusDays(1)) week.addView(cell(d, today), new LinearLayout.LayoutParams(0, dp(54), 1));
            grid.addView(week);
        }
    }

    private View cell(final LocalDate d, LocalDate today) {
        Context c = this;
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(0, dp(4), 0, dp(2));
        boolean inMonth = YearMonth.from(d).equals(month);
        boolean isSel = d.equals(selected), isToday = d.equals(today);

        TextView num = Ui.text(c, String.valueOf(d.getDayOfMonth()), 15, isSel ? R.color.onAccent : R.color.text, isToday || isSel);
        num.setGravity(Gravity.CENTER);
        if (isSel) num.setBackground(Ui.round(Ui.col(c, R.color.accent), dp(16)));
        else if (isToday) num.setBackground(Ui.stroke(0, Ui.col(c, R.color.accent), dp(2), dp(16)));
        if (isToday && !isSel) num.setTextColor(Ui.col(c, R.color.accent));
        num.setAlpha(inMonth ? 1f : 0.35f);
        cell.addView(num, new LinearLayout.LayoutParams(dp(32), dp(32)));

        List<Occ> l = byDay.get(d.toEpochDay());
        LinearLayout dots = new LinearLayout(c);
        dots.setGravity(Gravity.CENTER);
        dots.setPadding(0, dp(3), 0, 0);
        if (l != null) {
            boolean conflict = false;
            int shown = 0;
            for (Occ o : l) {
                if (o.conflict) conflict = true;
                if (shown >= 3) continue;
                View dot = new View(c);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(o.r.done ? Ui.col(c, R.color.divider) : o.r.category > 0 ? Categories.color(o.r.category) : Ui.col(c, R.color.accent));
                dot.setBackground(g);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(5), dp(5));
                lp.setMargins(dp(1), 0, dp(1), 0);
                dots.addView(dot, lp);
                shown++;
            }
            if (l.size() > 3) {
                TextView more = Ui.text(c, "+", 10, R.color.text2, true);
                dots.addView(more);
            }
            if (conflict) {
                ImageView w = Ui.icon(c, R.drawable.ic_warn, R.color.danger, 10, 0);
                dots.addView(w);
            }
            dots.setAlpha(inMonth ? 1f : 0.4f);
        }
        cell.addView(dots, new LinearLayout.LayoutParams(-1, dp(12)));
        cell.setBackground(Ui.ripple(c, null, dp(12)));
        cell.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            selected = d;
            if (!YearMonth.from(d).equals(month)) month = YearMonth.from(d);
            load();
        } });
        return cell;
    }

    private void buildDay() {
        long now = System.currentTimeMillis();
        long selMs = ms(selected);
        dayTitle.setText(Fmt.day(selMs + 12 * 3600000L, now).equals("Hoje") || Fmt.day(selMs + 12 * 3600000L, now).equals("Amanhã")
                ? Fmt.day(selMs + 12 * 3600000L, now) + " · " + Fmt.dateLong(selMs + 12 * 3600000L)
                : Fmt.dateLong(selMs + 12 * 3600000L));
        dayList.removeAllViews();
        List<Occ> l = byDay.get(selected.toEpochDay());
        if (l == null || l.isEmpty()) {
            TextView e = Ui.text(this, catFilter.isEmpty() ? "Sem lembretes neste dia. Toque em + para criar." : "Sem lembretes destas categorias neste dia.", 14, R.color.text2, false);
            e.setPadding(dp(20), dp(12), dp(20), dp(12));
            dayList.addView(e);
            return;
        }
        int conflicts = 0;
        for (Occ o : l) if (o.conflict) conflicts++;
        if (conflicts > 0) {
            TextView w = Ui.text(this, conflicts + " lembretes coincidem neste dia", 13, R.color.danger, true);
            w.setBackground(Ui.round(Ui.col(this, R.color.dangerSoft), dp(12)));
            w.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams lp = Ui.lpMW(); lp.setMargins(dp(16), dp(4), dp(16), dp(4));
            dayList.addView(w, lp);
        }
        for (final Occ o : l) dayList.addView(row(o, now));
    }

    private View row(final Occ o, long now) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.ripple(this, Ui.stroke(Ui.col(this, R.color.surface), Ui.col(this, R.color.cardStroke), Math.max(1, dp(1)), dp(16)), dp(16)));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView time = Ui.text(this, Fmt.time(o.at), 15, o.conflict ? R.color.danger : R.color.text, true);
        time.setMinWidth(dp(56));
        card.addView(time);
        View bar = new View(this);
        bar.setBackground(Ui.round(o.r.category > 0 ? Categories.color(o.r.category) : Ui.col(this, R.color.accent), dp(2)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(4), dp(32));
        blp.setMargins(dp(4), 0, dp(12), 0);
        card.addView(bar, blp);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, (o.r.important ? "★ " : "") + o.r.title, 16, R.color.text, false);
        Ui.ellipsize(t, 2);
        if (o.r.done) { t.setAlpha(0.5f); t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG); }
        texts.addView(t);
        StringBuilder m = new StringBuilder();
        if (o.r.isRecurring()) m.append("↻ ").append(Recurrence.describe(o.r));
        if (o.at == o.r.effectiveTime() && o.r.isSnoozed()) m.append(m.length() > 0 ? " · " : "").append("adiado");
        if (o.at < now && !o.r.done) m.append(m.length() > 0 ? " · " : "").append("atrasado");
        if (o.conflict) m.append(m.length() > 0 ? " · " : "").append("⚠ coincide com outro lembrete");
        if (m.length() > 0) {
            TextView s = Ui.text(this, m.toString(), 13, o.conflict ? R.color.danger : R.color.text2, false);
            texts.addView(s);
        }
        card.addView(texts, Ui.lpWeight(1));
        card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            startActivity(new Intent(CalendarActivity.this, EditActivity.class).putExtra("id", o.r.id));
        } });
        LinearLayout.LayoutParams lp = Ui.lpMW(); lp.setMargins(dp(16), dp(4), dp(16), dp(4));
        card.setLayoutParams(lp);
        return card;
    }
}
