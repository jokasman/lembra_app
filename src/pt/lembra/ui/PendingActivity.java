package pt.lembra.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import pt.lembra.R;
import pt.lembra.alarm.AlarmService;
import pt.lembra.alarm.Engine;
import pt.lembra.core.Categories;
import pt.lembra.core.Fmt;
import pt.lembra.core.Recurrence;
import pt.lembra.core.Reminder;
import pt.lembra.data.Db;
import pt.lembra.data.Prefs;

/** "Por tratar": aparece ao desbloquear o telemóvel enquanto houver lembretes notificados sem decisão. */
public class PendingActivity extends BaseActivity {
    private LinearLayout list;
    private TextView header;
    private final Runnable refresher = new Runnable() { public void run() { load(); } };

    private int dp(float v) { return Ui.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(24), 0, 0);
        header = Ui.text(this, "", 24, R.color.text, true);
        header.setPadding(dp(20), dp(8), dp(20), dp(2));
        root.addView(header);
        TextView sub = Ui.text(this, "Marque como feito ou adie para outra hora, para nada ficar esquecido.", 14, R.color.text2, false);
        sub.setPadding(dp(20), 0, dp(20), dp(10));
        root.addView(sub);
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, dp(16));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout bottom = new LinearLayout(this);
        bottom.setPadding(dp(16), dp(8), dp(16), dp(20));
        TextView later = Ui.button(this, "Decidir mais tarde", false);
        later.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        bottom.addView(later, Ui.lpWeight(1));
        root.addView(bottom);
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); Engine.addListener(refresher); load(); }
    @Override protected void onPause() { super.onPause(); Engine.removeListener(refresher); }

    private void load() {
        List<Reminder> p = AlarmService.pending(this);
        if (p.isEmpty()) { finish(); return; }
        Collections.sort(p, new Comparator<Reminder>() {
            public int compare(Reminder a, Reminder b) { return Long.compare(a.effectiveTime(), b.effectiveTime()); }
        });
        header.setText(p.size() == 1 ? "1 lembrete por tratar" : p.size() + " lembretes por tratar");
        list.removeAllViews();
        long now = System.currentTimeMillis();
        final int quick = Prefs.quickSnooze(this);
        for (final Reminder r : p) {
            LinearLayout card = Ui.card(this);
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            View bar = new View(this);
            bar.setBackground(Ui.round(r.category > 0 ? Categories.color(r.category) : Ui.col(this, R.color.accent), dp(2)));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(4), dp(36));
            blp.setMarginEnd(dp(12));
            top.addView(bar, blp);
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView t = Ui.text(this, (r.important ? "★ " : "") + r.title, 17, R.color.text, true);
            Ui.ellipsize(t, 2);
            texts.addView(t);
            String meta = Fmt.dayTime(r.effectiveTime(), now) + " (" + Fmt.relative(r.effectiveTime(), now) + ")";
            if (r.isRecurring()) meta += " · ↻ " + Recurrence.describe(r);
            texts.addView(Ui.text(this, meta, 13, R.color.danger, false));
            if (r.notes != null && !r.notes.trim().isEmpty()) {
                TextView n = Ui.text(this, r.notes.trim(), 13, R.color.text2, false);
                Ui.ellipsize(n, 2);
                texts.addView(n);
            }
            top.addView(texts, Ui.lpWeight(1));
            card.addView(top);

            LinearLayout actions = new LinearLayout(this);
            actions.setPadding(0, dp(12), 0, 0);
            TextView done = Ui.button(this, "Feito", true);
            done.setPadding(dp(8), dp(10), dp(8), dp(10));
            done.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                Reminder f = Db.get(PendingActivity.this).get(r.id);
                if (f != null) Engine.complete(PendingActivity.this, f);
            } });
            LinearLayout.LayoutParams lp1 = Ui.lpWeight(1); lp1.setMarginEnd(dp(8));
            actions.addView(done, lp1);
            TextView plus = Ui.button(this, "+" + Fmt.duration(quick), false);
            plus.setPadding(dp(8), dp(10), dp(8), dp(10));
            plus.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                Reminder f = Db.get(PendingActivity.this).get(r.id);
                if (f != null) Engine.snooze(PendingActivity.this, f, System.currentTimeMillis() + quick * 60000L);
            } });
            LinearLayout.LayoutParams lp2 = Ui.lpWeight(1); lp2.setMarginEnd(dp(8));
            actions.addView(plus, lp2);
            TextView snooze = Ui.button(this, "Adiar…", false);
            snooze.setPadding(dp(8), dp(10), dp(8), dp(10));
            snooze.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                startActivity(new Intent(PendingActivity.this, SnoozeActivity.class).putExtra("id", r.id));
            } });
            actions.addView(snooze, Ui.lpWeight(1));
            card.addView(actions);
            list.addView(card);
        }
    }
}
