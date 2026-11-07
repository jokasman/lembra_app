package pt.lembra.ui;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import pt.lembra.R;
import pt.lembra.alarm.AlarmService;
import pt.lembra.alarm.Engine;
import pt.lembra.alarm.Notifier;
import pt.lembra.core.Categories;
import pt.lembra.core.Fmt;
import pt.lembra.core.Recurrence;
import pt.lembra.core.Reminder;
import pt.lembra.data.Db;
import pt.lembra.data.Prefs;

/** Ecrã inteiro do despertador, visível por cima do ecrã de bloqueio. */
public class AlarmActivity extends BaseActivity {
    private static final List<AlarmActivity> open = new ArrayList<AlarmActivity>();
    private long id;

    public static void closeAll() {
        for (AlarmActivity a : new ArrayList<AlarmActivity>(open)) a.finish();
    }

    private int dp(float v) { return Ui.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        open.add(this);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        build();
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); build(); }

    @Override protected void onDestroy() { open.remove(this); super.onDestroy(); }

    @Override public void onBackPressed() { /* não fecha sem uma decisão */ }

    private void build() {
        id = getIntent().getLongExtra("id", Long.MIN_VALUE);
        final boolean test = id == AlarmService.TEST_ID;
        Reminder r = test ? null : Db.get(this).get(id);
        if (!test && (r == null || r.done)) { finish(); return; }
        String title = test ? "Teste do despertador" : r.title;
        String notes = test ? "Se ouviu o som com o telemóvel em silêncio, está tudo a funcionar." : r.notes;
        int color = test || r.category == 0 ? Ui.col(this, R.color.accent) : Categories.color(r.category);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{darken(color), 0xFF0E1016});
        root.setBackground(bg);
        root.setPadding(dp(28), dp(72), dp(28), dp(40));

        long now = System.currentTimeMillis();
        TextView clock = new TextView(this);
        clock.setText(Fmt.time(now));
        clock.setTextSize(72);
        clock.setTextColor(0xFFFFFFFF);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        root.addView(clock);
        TextView day = new TextView(this);
        day.setText(Fmt.dateLong(now));
        day.setTextSize(16);
        day.setTextColor(0xCCFFFFFF);
        root.addView(day);

        TextView t = new TextView(this);
        t.setText((r != null && r.important ? "★ " : "") + title);
        t.setTextSize(28);
        t.setTextColor(0xFFFFFFFF);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(48), 0, dp(8));
        root.addView(t);
        StringBuilder sub = new StringBuilder();
        if (notes != null && !notes.trim().isEmpty()) sub.append(notes.trim());
        if (r != null && r.isRecurring()) sub.append(sub.length() > 0 ? "\n" : "").append("↻ ").append(Recurrence.describe(r));
        if (sub.length() > 0) {
            TextView n = new TextView(this);
            n.setText(sub.toString());
            n.setTextSize(16);
            n.setTextColor(0xCCFFFFFF);
            n.setGravity(Gravity.CENTER);
            n.setMaxLines(6);
            root.addView(n);
        }

        // Foto (primeiro anexo de imagem) e checklist
        if (r != null) {
            for (final Db.Att a : Db.get(this).attachments(r.id)) {
                if (!pt.lembra.data.Attachments.isImage(a)) continue;
                android.graphics.Bitmap bm = pt.lembra.data.Attachments.thumbnail(this, a.file, dp(240));
                if (bm == null) break;
                android.widget.ImageView iv = new android.widget.ImageView(this);
                iv.setImageBitmap(bm);
                iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                iv.setClipToOutline(true);
                iv.setBackground(Ui.round(0x22FFFFFF, dp(18)));
                iv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { EditActivity.openAttachment(AlarmActivity.this, a); } });
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, dp(160));
                ilp.topMargin = dp(18);
                root.addView(iv, ilp);
                break;
            }
            List<Db.Sub> subs = Db.get(this).subtasks(r.id);
            if (!subs.isEmpty()) {
                android.widget.ScrollView ss = new android.widget.ScrollView(this);
                LinearLayout sl = new LinearLayout(this);
                sl.setOrientation(LinearLayout.VERTICAL);
                sl.setBackground(Ui.round(0x22FFFFFF, dp(18)));
                sl.setPadding(dp(16), dp(10), dp(16), dp(10));
                for (final Db.Sub s : subs) {
                    final TextView it = new TextView(this);
                    it.setTextSize(17);
                    it.setTextColor(0xFFFFFFFF);
                    it.setPadding(0, dp(8), 0, dp(8));
                    final boolean[] st = {s.done};
                    it.setText((st[0] ? "☑  " : "☐  ") + s.text);
                    it.setAlpha(st[0] ? 0.6f : 1f);
                    it.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                        st[0] = !st[0];
                        Db.get(AlarmActivity.this).setSubDone(s.id, st[0]);
                        it.setText((st[0] ? "☑  " : "☐  ") + s.text);
                        it.setAlpha(st[0] ? 0.6f : 1f);
                    } });
                    sl.addView(it);
                }
                ss.addView(sl);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2, 1);
                slp.topMargin = dp(16);
                root.addView(ss, slp);
            }
        }

        View space = new View(this);
        root.addView(space, new LinearLayout.LayoutParams(1, 0, 1));

        if (test) {
            root.addView(bigButton("Parar", 0xFFFFFFFF, 0xFF0E1016, new View.OnClickListener() { public void onClick(View v) { AlarmService.stop(null); finish(); } }));
        } else {
            root.addView(bigButton("Feito", 0xFFFFFFFF, 0xFF0E1016, new View.OnClickListener() { public void onClick(View v) {
                Reminder fresh = Db.get(AlarmActivity.this).get(id);
                if (fresh != null) Engine.complete(AlarmActivity.this, fresh);
                AlarmService.stop(null);
                finish();
            } }));
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, dp(12), 0, 0);
            final int quick = Prefs.quickSnooze(this);
            LinearLayout.LayoutParams lp = Ui.lpWeight(1); lp.setMarginEnd(dp(10));
            row.addView(bigButton("+" + Fmt.duration(quick), 0x33FFFFFF, 0xFFFFFFFF, new View.OnClickListener() { public void onClick(View v) {
                Reminder fresh = Db.get(AlarmActivity.this).get(id);
                if (fresh != null) Engine.snooze(AlarmActivity.this, fresh, System.currentTimeMillis() + quick * 60000L);
                AlarmService.stop(null);
                finish();
            } }), lp);
            row.addView(bigButton("Adiar…", 0x33FFFFFF, 0xFFFFFFFF, new View.OnClickListener() { public void onClick(View v) {
                // Silencia enquanto escolhe; se cancelar, o alarme volta a tocar.
                AlarmService.pause();
                startActivity(new Intent(AlarmActivity.this, SnoozeActivity.class).putExtra("id", id).putExtra("fromAlarm", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            } }), Ui.lpWeight(1));
            root.addView(row, Ui.lpMW());
        }
        setContentView(root);
    }

    private TextView bigButton(String label, int bg, int fg, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(18);
        b.setTextColor(fg);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(18), dp(16), dp(18));
        b.setBackground(Ui.ripple(this, Ui.round(bg, dp(32)), dp(32)));
        b.setOnClickListener(l);
        b.setLayoutParams(Ui.lpMW());
        return b;
    }

    private static int darken(int c) {
        int r = ((c >> 16) & 0xFF) * 6 / 10, g = ((c >> 8) & 0xFF) * 6 / 10, b = (c & 0xFF) * 6 / 10;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
