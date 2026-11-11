package pt.lembra.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import pt.lembra.R;
import pt.lembra.alarm.Engine;
import pt.lembra.alarm.Notifier;
import pt.lembra.core.CallFollowUp;
import pt.lembra.core.Fmt;
import pt.lembra.core.Reminder;
import pt.lembra.data.Prefs;

/** Folha "Agendar lembrete?" mostrada quando uma chamada termina. */
public class CallPromptActivity extends BaseActivity {
    private static final long AUTO_CLOSE_MS = 30000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoClose = new Runnable() { public void run() { finish(); } };
    private EditText titleE;

    private int dp(float v) { return Ui.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true);
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        Notifier.cancelCallPrompt(this);
        setFinishOnTouchOutside(true);

        Window w = getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setGravity(Gravity.BOTTOM);
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setDimAmount(0.45f);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.col(this, R.color.surface));
        float rad = dp(28);
        bg.setCornerRadii(new float[]{rad, rad, rad, rad, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setPadding(dp(20), dp(10), dp(20), dp(20));

        View handle = new View(this);
        handle.setBackground(Ui.round(Ui.col(this, R.color.divider), dp(3)));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(40), dp(5));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = dp(14);
        sheet.addView(handle, hlp);

        // Cabeçalho
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = Ui.icon(this, R.drawable.ic_bell, R.color.accent, 44, 10);
        ic.setBackground(Ui.round(Ui.col(this, R.color.accentSoft), dp(14)));
        LinearLayout.LayoutParams iclp = new LinearLayout.LayoutParams(dp(44), dp(44));
        iclp.setMarginEnd(dp(14));
        head.addView(ic, iclp);
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.addView(Ui.text(this, "Agendar lembrete?", 19, R.color.text, true), Ui.lpMW());
        ht.addView(Ui.text(this, CallFollowUp.describe(getIntent().getIntExtra("kind", CallFollowUp.ANSWERED)), 13, R.color.text2, false), Ui.lpMW());
        head.addView(ht, Ui.lpWeight(1));
        sheet.addView(head, Ui.lpMW());

        // Título do lembrete (editável)
        titleE = new EditText(this);
        String title = getIntent().getStringExtra("title");
        titleE.setText(title == null ? "" : title);
        titleE.setHint("Lembrete");
        titleE.setTextSize(16);
        titleE.setSingleLine(true);
        titleE.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        titleE.setTextColor(Ui.col(this, R.color.text));
        titleE.setHintTextColor(Ui.col(this, R.color.text2));
        titleE.setBackground(Ui.round(Ui.col(this, R.color.chip), dp(12)));
        titleE.setPadding(dp(14), dp(12), dp(14), dp(12));
        titleE.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            public void onTextChanged(CharSequence s, int a, int c, int d) { }
            public void afterTextChanged(Editable s) { handler.removeCallbacks(autoClose); } // a escrever: não fecha sozinha
        });
        LinearLayout.LayoutParams tlp = Ui.lpMW();
        tlp.topMargin = dp(16);
        sheet.addView(titleE, tlp);

        // Daqui a… (4 mosaicos grandes)
        TextView label = Ui.text(this, "LEMBRAR DAQUI A", 12, R.color.text2, true);
        label.setLetterSpacing(0.08f);
        label.setPadding(dp(4), dp(18), 0, dp(8));
        sheet.addView(label, Ui.lpMW());
        LinearLayout tiles = new LinearLayout(this);
        int[] mins = CallFollowUp.MINUTES;
        for (int i = 0; i < mins.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(84), 1);
            if (i < mins.length - 1) lp.setMarginEnd(dp(8));
            boolean hours = mins[i] >= 60;
            int n = hours ? mins[i] / 60 : mins[i];
            tiles.addView(tile(String.valueOf(n), hours ? (n == 1 ? "hora" : "horas") : "min", mins[i]), lp);
        }
        sheet.addView(tiles, Ui.lpMW());

        TextView cancel = Ui.button(this, "Agora não", false);
        cancel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        LinearLayout.LayoutParams clp = Ui.lpMW();
        clp.topMargin = dp(16);
        sheet.addView(cancel, clp);

        setContentView(sheet);
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        handler.postDelayed(autoClose, AUTO_CLOSE_MS);
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); recreate(); }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        // Qualquer toque adia o fecho automático.
        handler.removeCallbacks(autoClose);
        handler.postDelayed(autoClose, AUTO_CLOSE_MS * 2);
        return super.dispatchTouchEvent(ev);
    }

    @Override protected void onDestroy() { handler.removeCallbacks(autoClose); super.onDestroy(); }

    /** Mosaico "10 min · 11:55". */
    private View tile(String big, String unit, final int minutes) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.ripple(this, Ui.round(Ui.col(this, R.color.accentSoft), dp(18)), dp(18)));
        TextView b = Ui.text(this, big, 24, R.color.accent, true);
        b.setGravity(Gravity.CENTER);
        t.addView(b, new LinearLayout.LayoutParams(-2, -2));
        t.addView(Ui.text(this, unit, 12, R.color.accent, false), new LinearLayout.LayoutParams(-2, -2));
        TextView at = Ui.text(this, Fmt.time(System.currentTimeMillis() + minutes * 60000L), 11, R.color.text2, false);
        at.setPadding(0, dp(2), 0, 0);
        t.addView(at, new LinearLayout.LayoutParams(-2, -2));
        t.setContentDescription("Lembrar daqui a " + big + " " + unit);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { schedule(minutes); } });
        return t;
    }

    private void schedule(int minutes) {
        String title = titleE.getText().toString().trim();
        if (title.isEmpty()) title = CallFollowUp.title(null, null, Fmt.time(System.currentTimeMillis()));
        long now = System.currentTimeMillis();
        Reminder r = new Reminder();
        r.title = title;
        r.dueAt = now + minutes * 60000L;
        r.preAlertMin = 0; // prazos curtos: sem aviso antecipado
        r.alarm = Prefs.alarmDefault(this);
        Engine.save(this, r);
        Toast.makeText(this, "Lembrete para " + Fmt.dayTime(r.dueAt, now).toLowerCase(), Toast.LENGTH_SHORT).show();
        finish();
    }
}
