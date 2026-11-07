package pt.lembra.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.DatePicker;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import pt.lembra.R;
import pt.lembra.alarm.AlarmService;
import pt.lembra.alarm.Engine;
import pt.lembra.core.Fmt;
import pt.lembra.core.Recurrence;
import pt.lembra.core.Reminder;
import pt.lembra.data.Db;

/**
 * Folha "Adiar" (bottom sheet): atalhos grandes para adiar alguns minutos, sugestões para mais tarde
 * e um seletor de data e hora integrado, com pré-visualização do resultado.
 */
public class SnoozeActivity extends BaseActivity {
    private Reminder r;
    private boolean fromAlarm, applied;

    // Seletor personalizado
    private LinearLayout customPanel, dateChips;
    private LocalDate customDate;
    private NumberPicker hourP, minP;
    private TextView confirmBtn;
    private static final int MIN_STEP = 5;

    private int dp(float v) { return Ui.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        fromAlarm = getIntent().getBooleanExtra("fromAlarm", false);
        if (fromAlarm) {
            if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
            else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
        r = Db.get(this).get(getIntent().getLongExtra("id", -1));
        if (r == null || r.done) { finish(); return; }
        setFinishOnTouchOutside(!fromAlarm);

        // Janela: folha encostada ao fundo, largura total, fundo escurecido.
        Window w = getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setGravity(Gravity.BOTTOM);
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setDimAmount(0.55f);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.col(this, R.color.surface));
        float rad = dp(28);
        bg.setCornerRadii(new float[]{rad, rad, rad, rad, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setPadding(dp(20), dp(10), dp(20), dp(20));
        scroll.addView(sheet, new ScrollView.LayoutParams(-1, -2));

        // Pega
        View handle = new View(this);
        handle.setBackground(Ui.round(Ui.col(this, R.color.divider), dp(3)));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(40), dp(5));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = dp(14);
        sheet.addView(handle, hlp);

        // Cabeçalho
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = Ui.icon(this, R.drawable.ic_snooze, R.color.accent, 44, 10);
        ic.setBackground(Ui.round(Ui.col(this, R.color.accentSoft), dp(14)));
        LinearLayout.LayoutParams iclp = new LinearLayout.LayoutParams(dp(44), dp(44));
        iclp.setMarginEnd(dp(14));
        head.addView(ic, iclp);
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, r.title, 19, R.color.text, true);
        Ui.ellipsize(title, 2);
        ht.addView(title, Ui.lpMW());
        long now = System.currentTimeMillis();
        TextView was = Ui.text(this, "Adiar · estava para " + Fmt.dayTime(r.effectiveTime(), now).toLowerCase(), 13, R.color.text2, false);
        ht.addView(was, Ui.lpMW());
        head.addView(ht, Ui.lpWeight(1));
        sheet.addView(head, Ui.lpMW());

        // Daqui a… (4 mosaicos grandes)
        sheet.addView(label("Daqui a"));
        LinearLayout tiles = new LinearLayout(this);
        int[] mins = {10, 30, 60, 180};
        String[] big = {"10", "30", "1", "3"};
        String[] unit = {"min", "min", "hora", "horas"};
        for (int i = 0; i < mins.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(84), 1);
            if (i < mins.length - 1) lp.setMarginEnd(dp(8));
            tiles.addView(tile(big[i], unit[i], mins[i]), lp);
        }
        sheet.addView(tiles, Ui.lpMW());

        // Mais tarde
        sheet.addView(label("Mais tarde"));
        LinearLayout later = new LinearLayout(this);
        later.setOrientation(LinearLayout.VERTICAL);
        later.setBackground(Ui.round(Ui.col(this, R.color.chip), dp(18)));
        LocalDateTime n = LocalDateTime.now(Recurrence.zone).withSecond(0).withNano(0);
        LocalDate today = n.toLocalDate();
        boolean first = true;
        if (n.getHour() < 19) { addLater(later, "Esta noite", today.atTime(20, 0), first); first = false; }
        addLater(later, "Amanhã de manhã", today.plusDays(1).atTime(9, 0), first); first = false;
        if (n.getDayOfWeek().getValue() < 5) addLater(later, "No fim de semana", today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).atTime(10, 0), false);
        addLater(later, "Próxima segunda", today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(9, 0), false);
        sheet.addView(later, Ui.lpMW());

        // Escolher data e hora (painel integrado)
        final TextView custom = Ui.button(this, "Escolher data e hora", false);
        LinearLayout.LayoutParams clp = Ui.lpMW(); clp.topMargin = dp(16);
        sheet.addView(custom, clp);
        customPanel = buildCustomPanel();
        customPanel.setVisibility(View.GONE);
        sheet.addView(customPanel, Ui.lpMW());
        custom.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            boolean show = customPanel.getVisibility() != View.VISIBLE;
            customPanel.setVisibility(show ? View.VISIBLE : View.GONE);
            custom.setText(show ? "Fechar seletor" : "Escolher data e hora");
        } });

        TextView cancel = Ui.text(this, fromAlarm ? "Voltar ao alarme" : "Cancelar", 15, R.color.text2, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, dp(16), 0, dp(4));
        cancel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        sheet.addView(cancel, Ui.lpMW());

        setContentView(scroll);
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        recreate();
    }

    private TextView label(String s) {
        TextView t = Ui.text(this, s.toUpperCase(), 12, R.color.text2, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(4), dp(18), 0, dp(8));
        t.setLayoutParams(Ui.lpMW());
        return t;
    }

    /** Mosaico "10 min · 11:55". */
    private View tile(String big, String unit, final int minutes) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.ripple(this, Ui.round(Ui.col(this, R.color.accentSoft), dp(18)), dp(18)));
        TextView b = Ui.text(this, big, 24, R.color.accent, true);
        b.setGravity(Gravity.CENTER);
        t.addView(b, new LinearLayout.LayoutParams(-2, -2));
        TextView u = Ui.text(this, unit, 12, R.color.accent, false);
        t.addView(u, new LinearLayout.LayoutParams(-2, -2));
        TextView at = Ui.text(this, Fmt.time(System.currentTimeMillis() + minutes * 60000L), 11, R.color.text2, false);
        at.setPadding(0, dp(2), 0, 0);
        t.addView(at, new LinearLayout.LayoutParams(-2, -2));
        t.setContentDescription("Adiar " + big + " " + unit);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            apply(System.currentTimeMillis() + minutes * 60000L);
        } });
        return t;
    }

    private void addLater(LinearLayout group, String label, LocalDateTime t, boolean first) {
        final long ms = t.atZone(Recurrence.zone).toInstant().toEpochMilli();
        if (!first) {
            View div = new View(this);
            div.setBackgroundColor(Ui.col(this, R.color.divider));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, 1);
            dlp.setMargins(dp(16), 0, dp(16), 0);
            group.addView(div, dlp);
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setMinimumHeight(dp(56));
        row.setBackground(Ui.ripple(this, null, dp(18)));
        TextView l = Ui.text(this, label, 16, R.color.text, false);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = Ui.text(this, Fmt.dayTime(ms, System.currentTimeMillis()), 14, R.color.text2, false);
        row.addView(v, new LinearLayout.LayoutParams(-2, -2));
        row.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { apply(ms); } });
        group.addView(row, new LinearLayout.LayoutParams(-1, dp(56)));
    }

    // ------------------------------------------------------------------ Seletor integrado

    private LinearLayout buildCustomPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(0, dp(12), 0, 0);

        // Dias: hoje + 6 dias + "Outra data…"
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        dateChips = new LinearLayout(this);
        hs.addView(dateChips);
        p.addView(hs, Ui.lpMW());

        LocalDateTime base = LocalDateTime.now(Recurrence.zone).plusHours(1);
        customDate = base.toLocalDate();
        renderDateChips();

        // Hora: dois seletores giratórios (hora e minutos de 5 em 5)
        LinearLayout time = new LinearLayout(this);
        time.setGravity(Gravity.CENTER);
        time.setPadding(0, dp(8), 0, 0);
        hourP = new NumberPicker(this);
        hourP.setMinValue(0); hourP.setMaxValue(23);
        hourP.setFormatter(new NumberPicker.Formatter() { public String format(int v) { return v < 10 ? "0" + v : String.valueOf(v); } });
        hourP.setValue(base.getHour());
        minP = new NumberPicker(this);
        String[] mins = new String[60 / MIN_STEP];
        for (int i = 0; i < mins.length; i++) { int m = i * MIN_STEP; mins[i] = m < 10 ? "0" + m : String.valueOf(m); }
        minP.setMinValue(0); minP.setMaxValue(mins.length - 1);
        minP.setDisplayedValues(mins);
        minP.setValue(0);
        NumberPicker.OnValueChangeListener ch = new NumberPicker.OnValueChangeListener() {
            public void onValueChange(NumberPicker picker, int o, int nv) { updateConfirm(); }
        };
        hourP.setOnValueChangedListener(ch);
        minP.setOnValueChangedListener(ch);
        time.addView(hourP, new LinearLayout.LayoutParams(dp(72), -2));
        TextView colon = Ui.text(this, ":", 28, R.color.text, true);
        colon.setPadding(dp(10), 0, dp(10), 0);
        time.addView(colon);
        time.addView(minP, new LinearLayout.LayoutParams(dp(72), -2));
        p.addView(time, Ui.lpMW());

        confirmBtn = Ui.button(this, "", true);
        LinearLayout.LayoutParams lp = Ui.lpMW(); lp.topMargin = dp(10);
        p.addView(confirmBtn, lp);
        confirmBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            long ms = customMs();
            if (ms <= System.currentTimeMillis()) {
                Toast.makeText(SnoozeActivity.this, "Escolha uma hora no futuro", Toast.LENGTH_SHORT).show();
                return;
            }
            apply(ms);
        } });
        updateConfirm();
        return p;
    }

    private void renderDateChips() {
        dateChips.removeAllViews();
        LocalDate today = LocalDate.now(Recurrence.zone);
        boolean inRange = false;
        for (int i = 0; i < 7; i++) {
            final LocalDate d = today.plusDays(i);
            String label = i == 0 ? "Hoje" : i == 1 ? "Amanhã"
                    : Fmt.cap(Fmt.DAYS_SHORT[d.getDayOfWeek().getValue() - 1]) + " " + d.getDayOfMonth();
            boolean sel = d.equals(customDate);
            if (sel) inRange = true;
            TextView c = Ui.chip(this, label, sel, 0);
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { customDate = d; renderDateChips(); updateConfirm(); } });
            dateChips.addView(c);
        }
        String other = inRange ? "Outra data…" : customDate.getDayOfMonth() + " " + Fmt.MONTHS_SHORT[customDate.getMonthValue() - 1];
        TextView o = Ui.chip(this, other, !inRange, 0);
        o.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            DatePickerDialog dd = new DatePickerDialog(SnoozeActivity.this, new DatePickerDialog.OnDateSetListener() {
                public void onDateSet(DatePicker dp, int y, int m, int d) { customDate = LocalDate.of(y, m + 1, d); renderDateChips(); updateConfirm(); }
            }, customDate.getYear(), customDate.getMonthValue() - 1, customDate.getDayOfMonth());
            dd.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            dd.show();
        } });
        dateChips.addView(o);
    }

    private long customMs() {
        return customDate.atTime(LocalTime.of(hourP.getValue(), minP.getValue() * MIN_STEP))
                .atZone(Recurrence.zone).toInstant().toEpochMilli();
    }

    private void updateConfirm() {
        if (confirmBtn == null || hourP == null) return;
        long ms = customMs();
        boolean past = ms <= System.currentTimeMillis();
        confirmBtn.setText(past ? "Essa hora já passou" : "Adiar para " + Fmt.dayTime(ms, System.currentTimeMillis()).toLowerCase());
        confirmBtn.setAlpha(past ? 0.5f : 1f);
    }

    // ------------------------------------------------------------------ Aplicar

    @Override protected void onDestroy() {
        // Cancelou a escolha vindo do alarme: o alarme volta a tocar (só pára com uma decisão).
        if (fromAlarm && !applied && isFinishing()) {
            Reminder cur = r == null ? null : Db.get(this).get(r.id);
            if (cur != null && !cur.done) AlarmService.resume(); else AlarmService.stop(null);
        }
        super.onDestroy();
    }

    private void apply(long until) {
        applied = true;
        Reminder fresh = Db.get(this).get(r.id);
        if (fresh != null) {
            Engine.snooze(this, fresh, until);
            int tol = pt.lembra.data.Prefs.conflictTolerance(this);
            String msg = "Adiado para " + Fmt.dayTime(until, System.currentTimeMillis()).toLowerCase();
            if (tol >= 0) {
                Reminder after = Db.get(this).get(fresh.id);
                pt.lembra.core.Occurrences.Conflict cf = after == null ? null : pt.lembra.core.Occurrences.nextConflict(after, Db.get(this).all(), tol);
                if (cf != null) msg += "\nAtenção: coincide com «" + cf.other.title + "»";
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
