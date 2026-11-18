package pt.lembra.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import pt.lembra.R;
import pt.lembra.alarm.QuietPeriod;
import pt.lembra.core.Fmt;
import pt.lembra.core.PauseWindow;
import pt.lembra.data.Prefs;

/** Ecrã para pausar os lembretes: atalhos em horas e início/fim com data e hora sempre visíveis. */
public class PauseActivity extends BaseActivity {
    private long start, end;
    private int pickedHours = -1;
    private TextView startDate, startTime, endDate, endTime, summary;
    private final TextView[] tiles = new TextView[PauseWindow.HOURS.length];

    private int dp(float v) { return Ui.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        long now = System.currentTimeMillis();
        long savedStart = Prefs.quietStart(this), savedEnd = Prefs.quietEnd(this);
        if (PauseWindow.state(savedStart, savedEnd, now) != PauseWindow.NONE) {
            start = savedStart;
            end = savedEnd;
        } else {
            long[] w = PauseWindow.forHours(now, 1);
            start = w[0];
            end = w[1];
        }
        build();
    }

    private void build() {
        long now = System.currentTimeMillis();
        int state = PauseWindow.state(Prefs.quietStart(this), Prefs.quietEnd(this), now);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(8), dp(12), dp(4));
        top.addView(Ui.iconButton(this, R.drawable.ic_back, "Voltar", new View.OnClickListener() { public void onClick(View v) { finish(); } }));
        TextView t = Ui.text(this, "Pausar lembretes", 20, R.color.text, true);
        t.setPadding(dp(6), 0, 0, 0);
        top.addView(t);
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // Estado atual
        if (state != PauseWindow.NONE) {
            LinearLayout st = new LinearLayout(this);
            st.setOrientation(LinearLayout.VERTICAL);
            st.setBackground(Ui.round(Ui.col(this, R.color.pauseSoft), dp(20)));
            st.setPadding(dp(18), dp(16), dp(18), dp(16));
            long qs = Prefs.quietStart(this), qe = Prefs.quietEnd(this);
            String head = state == PauseWindow.ACTIVE ? "Em pausa até " + Fmt.dayTime(qe, now).toLowerCase()
                    : "Pausa programada";
            st.addView(Ui.text(this, head, 17, R.color.pause, true));
            String detail = state == PauseWindow.ACTIVE ? "Falta " + Fmt.relative(qe, now).replace("daqui a ", "")
                    : Fmt.dayTime(qs, now) + " – " + Fmt.dayTime(qe, now).toLowerCase();
            TextView dt = Ui.text(this, detail, 14, R.color.pause, false);
            dt.setPadding(0, dp(2), 0, dp(12));
            st.addView(dt);
            TextView stop = Ui.button(this, state == PauseWindow.ACTIVE ? "Terminar pausa agora" : "Cancelar pausa", true);
            stop.setBackground(Ui.ripple(this, Ui.round(Ui.col(this, R.color.pause), dp(24)), dp(24)));
            stop.setTextColor(Ui.col(this, R.color.onAccent));
            stop.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                QuietPeriod.clear(PauseActivity.this);
                Toast.makeText(PauseActivity.this, "Pausa terminada", Toast.LENGTH_SHORT).show();
                finish();
            } });
            st.addView(stop, Ui.lpMW());
            LinearLayout.LayoutParams slp = Ui.lpMW();
            slp.bottomMargin = dp(12);
            body.addView(st, slp);
        }

        // Atalhos em horas
        LinearLayout quick = Ui.card(this);
        quick.addView(Ui.sectionLabel(this, "Pausar durante"));
        TextView hint = Ui.text(this, "A contar de agora. Os campos abaixo são preenchidos e pode ajustá-los.", 13, R.color.text2, false);
        hint.setPadding(dp(4), 0, 0, dp(10));
        quick.addView(hint);
        int[] hours = PauseWindow.HOURS;
        LinearLayout row = null;
        for (int i = 0; i < hours.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams rlp = Ui.lpMW();
                if (i > 0) rlp.topMargin = dp(8);
                quick.addView(row, rlp);
            }
            final int h = hours[i];
            TextView tile = Ui.text(this, h + " h", 18, R.color.accent, true);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(0, dp(14), 0, dp(14));
            tile.setContentDescription("Pausar " + h + (h == 1 ? " hora" : " horas"));
            tile.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                long[] w = PauseWindow.forHours(System.currentTimeMillis(), h);
                start = w[0];
                end = w[1];
                pickedHours = h;
                bindFields();
            } });
            tiles[i] = tile;
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i % 3 < 2) tlp.setMarginEnd(dp(8));
            row.addView(tile, tlp);
        }
        body.addView(quick);

        // Início e fim
        LinearLayout win = Ui.card(this);
        LinearLayout.LayoutParams wlp = Ui.lpMW();
        wlp.topMargin = dp(12);
        win.addView(Ui.sectionLabel(this, "Período da pausa"));
        win.addView(Ui.subLabel(this, "Início"));
        startDate = field(R.drawable.ic_calendar);
        startTime = field(R.drawable.ic_clock);
        win.addView(pair(startDate, startTime));
        TextView endLabel = Ui.subLabel(this, "Fim");
        endLabel.setPadding(dp(2), dp(14), 0, dp(8));
        win.addView(endLabel);
        endDate = field(R.drawable.ic_calendar);
        endTime = field(R.drawable.ic_clock);
        win.addView(pair(endDate, endTime));
        summary = Ui.text(this, "", 13, R.color.text2, false);
        summary.setPadding(dp(4), dp(14), dp(4), 0);
        summary.setLineSpacing(0, 1.15f);
        win.addView(summary);
        body.addView(win, wlp);

        startDate.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickDate(true); } });
        startTime.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickTime(true); } });
        endDate.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickDate(false); } });
        endTime.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickTime(false); } });

        TextView note = Ui.text(this, "Durante a pausa, os lembretes que tocam como despertador ficam em silêncio. "
                + "Quando a pausa termina, os que venceram entretanto tocam em fila. "
                + "Os lembretes só com notificação continuam a aparecer.", 13, R.color.text2, false);
        note.setPadding(dp(8), dp(14), dp(8), 0);
        note.setLineSpacing(0, 1.15f);
        body.addView(note);

        // Guardar
        TextView save = Ui.button(this, state == PauseWindow.NONE ? "Pausar" : "Guardar alterações", true);
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { save(); } });
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(16), dp(8), dp(16), dp(16));
        bar.addView(save, Ui.lpWeight(1));
        root.addView(bar, Ui.lpMW());

        setContentView(root);
        bindFields();
    }

    private LinearLayout pair(TextView date, TextView time) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, -2, 1.4f);
        dlp.setMarginEnd(dp(8));
        row.addView(date, dlp);
        row.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private TextView field(int icon) {
        TextView t = Ui.text(this, "", 15, R.color.text, false);
        t.setBackground(Ui.ripple(this, Ui.round(Ui.col(this, R.color.chip), dp(12)), dp(12)));
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setSingleLine(true);
        android.graphics.drawable.Drawable d = getDrawable(icon).mutate();
        d.setTint(Ui.col(this, R.color.text2));
        d.setBounds(0, 0, dp(20), dp(20));
        t.setCompoundDrawablesRelative(d, null, null, null);
        t.setCompoundDrawablePadding(dp(8));
        return t;
    }

    private void bindFields() {
        long now = System.currentTimeMillis();
        startDate.setText(Fmt.day(start, now));
        startTime.setText(Fmt.time(start));
        endDate.setText(Fmt.day(end, now));
        endTime.setText(Fmt.time(end));
        String err = PauseWindow.validate(start, end, now);
        if (err != null) {
            summary.setText(err);
            summary.setTextColor(Ui.col(this, R.color.danger));
        } else {
            summary.setText("Pausa de " + PauseWindow.length(start, end)
                    + (start <= now ? ", começa já" : ", começa " + Fmt.dayTime(start, now).toLowerCase())
                    + ". Os alarmes voltam a tocar " + Fmt.dayTime(end, now).toLowerCase() + ".");
            summary.setTextColor(Ui.col(this, R.color.text2));
        }
        for (int i = 0; i < tiles.length; i++) {
            boolean sel = PauseWindow.HOURS[i] == pickedHours;
            tiles[i].setTextColor(Ui.col(this, sel ? R.color.onAccent : R.color.accent));
            tiles[i].setBackground(Ui.ripple(this, Ui.round(Ui.col(this, sel ? R.color.accent : R.color.accentSoft), dp(16)), dp(16)));
        }
    }

    private LocalDateTime ldt(long ms) { return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()); }

    private long ms(LocalDateTime d) { return d.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }

    private void apply(boolean isStart, LocalDateTime value) {
        pickedHours = -1;
        long v = ms(value);
        if (isStart) {
            end = PauseWindow.shiftEnd(start, end, v); // mantém a duração
            start = v;
        } else end = v;
        bindFields();
    }

    private void pickDate(final boolean isStart) {
        final LocalDateTime cur = ldt(isStart ? start : end);
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            public void onDateSet(DatePicker view, int y, int m, int d) { apply(isStart, cur.withYear(y).withMonth(m + 1).withDayOfMonth(d)); }
        }, cur.getYear(), cur.getMonthValue() - 1, cur.getDayOfMonth()).show();
    }

    private void pickTime(final boolean isStart) {
        final LocalDateTime cur = ldt(isStart ? start : end);
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            public void onTimeSet(TimePicker view, int h, int m) { apply(isStart, cur.withHour(h).withMinute(m).withSecond(0).withNano(0)); }
        }, cur.getHour(), cur.getMinute(), true).show();
    }

    private void save() {
        long now = System.currentTimeMillis();
        String err = PauseWindow.validate(start, end, now);
        if (err != null) { Toast.makeText(this, err, Toast.LENGTH_LONG).show(); return; }
        QuietPeriod.set(this, start, end);
        String msg = start <= now ? "Lembretes em pausa até " + Fmt.dayTime(end, now).toLowerCase()
                : "Pausa programada para " + Fmt.dayTime(start, now).toLowerCase();
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        finish();
    }
}
