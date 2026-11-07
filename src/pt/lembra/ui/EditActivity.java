package pt.lembra.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import pt.lembra.alarm.Geo;
import java.util.ArrayList;
import java.util.List;
import android.graphics.drawable.GradientDrawable;
import pt.lembra.R;
import pt.lembra.alarm.Engine;
import pt.lembra.core.Categories;
import pt.lembra.core.Fmt;
import pt.lembra.core.QuickParser;
import pt.lembra.core.Recurrence;
import pt.lembra.core.Reminder;
import pt.lembra.data.Db;
import pt.lembra.data.Prefs;

/** Criar / editar um lembrete. */
public class EditActivity extends BaseActivity {
    private Reminder r;
    private Reminder original;
    private boolean isNew;
    private boolean conflictConfirmed;

    // Local
    private LinearLayout timePanel, locPanel, repeatCard;
    private TextView modeTime, modeLoc, placeBtn, trigArrive, trigLeave;
    private Switch locRepeatSw;
    static final int REQ_PLACE = 30, REQ_CAMERA = 31, REQ_FILES = 32, REQ_LOC = 21, REQ_BGLOC = 22;

    // Checklist e anexos
    private final List<Db.Sub> subs = new ArrayList<Db.Sub>();
    private LinearLayout subList;
    private EditText subInput;
    private final List<Db.Att> atts = new ArrayList<Db.Att>();
    private LinearLayout attGrid;
    private String pendingPhoto;
    private LocalDateTime when;

    private EditText titleE, notesE, intervalE, endCountE;
    private TextView dateBtn, timeBtn, repeatBtn, endBtn, preBtn, endDateBtn, unitBtn, snoozeInfo, summary;
    private LinearLayout weekRow, customRow, endRow, catRow, snoozeRow, endDetail;
    private Switch importantSw, alarmSw;
    private TextView[] dayToggles = new TextView[7];

    static final String[] REPEAT_LABELS = {"Não repete", "Todos os dias", "Dias úteis (seg–sex)", "Semanal…", "Todos os meses", "Todos os anos", "Personalizado…"};
    static final String[] END_LABELS = {"Nunca", "Numa data", "Após N vezes"};
    static final int[] PRE_VALUES = {0, 5, 10, 15, 30, 60, 120, 1440};
    static final String[] UNIT_LABELS = {"horas", "dias", "semanas", "meses"};

    private int dp(float v) { return Ui.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        long id = getIntent().getLongExtra("id", 0);
        if (id > 0) r = Db.get(this).get(id);
        isNew = r == null;
        long now = System.currentTimeMillis();
        if (isNew) {
            r = new Reminder();
            r.preAlertMin = Prefs.defaultPreAlert(this);
            r.alarm = Prefs.alarmDefault(this);
            String pre = getIntent().getStringExtra("prefill");
            int def = Prefs.defaultTime(this);
            if (pre != null && !pre.trim().isEmpty()) {
                QuickParser.Result p = QuickParser.parse(pre, now, def / 60, def % 60);
                Reminder pr = p.toReminder();
                pr.preAlertMin = r.preAlertMin;
                pr.alarm = r.alarm;
                r = pr;
                if (p.dueAt == null) r.dueAt = 0;
            }
            int presetCat = getIntent().getIntExtra("category", -1);
            if (presetCat >= 0 && Categories.get(presetCat) != null) r.category = presetCat;
            long day = getIntent().getLongExtra("day", -1);
            if (day >= 0 && r.dueAt == 0) {
                LocalDateTime t = LocalDate.ofEpochDay(day).atTime(def / 60, def % 60);
                if (LocalDate.ofEpochDay(day).equals(LocalDate.now(Recurrence.zone)) && t.isBefore(LocalDateTime.now(Recurrence.zone)))
                    t = LocalDateTime.now(Recurrence.zone).plusHours(1).withMinute(0).withSecond(0).withNano(0);
                r.dueAt = t.atZone(Recurrence.zone).toInstant().toEpochMilli();
            }
            if (r.dueAt == 0) {
                // Próxima hora "redonda"
                LocalDateTime t = LocalDateTime.now(Recurrence.zone).plusHours(1).withMinute(0).withSecond(0).withNano(0);
                r.dueAt = t.atZone(Recurrence.zone).toInstant().toEpochMilli();
            }
        }
        original = r.copy();
        if (!isNew) {
            subs.addAll(Db.get(this).subtasks(r.id));
            atts.addAll(Db.get(this).attachments(r.id));
        }
        if (b != null) {
            pendingPhoto = b.getString("pendingPhoto");
        }
        when = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(r.dueAt), Recurrence.zone);
        buildUi();
        bindAll();
        if (isNew) getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    // ------------------------------------------------------------------ UI
    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(8), dp(12), dp(4));
        top.addView(Ui.iconButton(this, R.drawable.ic_close, "Cancelar", new View.OnClickListener() { public void onClick(View v) { finish(); } }));
        TextView t = Ui.text(this, isNew ? "Novo lembrete" : "Editar lembrete", 20, R.color.text, true);
        t.setPadding(dp(6), 0, 0, 0);
        top.addView(t, Ui.lpWeight(1));
        TextView save = Ui.button(this, "Guardar", true);
        save.setPadding(dp(20), dp(9), dp(20), dp(9));
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { save(); } });
        top.addView(save);
        root.addView(top);

        ScrollView sv = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(32));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        // Título e notas
        LinearLayout c1 = Ui.card(this);
        titleE = new EditText(this);
        titleE.setHint("O que quer lembrar?");
        titleE.setTextSize(20);
        titleE.setBackground(null);
        titleE.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        c1.addView(titleE);
        View div = new View(this);
        div.setBackgroundColor(Ui.col(this, R.color.divider));
        c1.addView(div, new LinearLayout.LayoutParams(-1, 1));
        notesE = new EditText(this);
        notesE.setHint("Notas (opcional)");
        notesE.setTextSize(15);
        notesE.setBackground(null);
        notesE.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        notesE.setMinLines(1);
        c1.addView(notesE);
        body.addView(c1);

        // Quando
        LinearLayout c2 = Ui.card(this);
        c2.addView(Ui.cardHeader(this, R.drawable.ic_calendar, "Quando"));
        LinearLayout modes = new LinearLayout(this);
        modes.setPadding(0, 0, 0, dp(12));
        modeTime = Ui.chip(this, "🕒 Numa hora", true, 0);
        modeLoc = Ui.chip(this, "📍 Num local", false, 0);
        modes.addView(modeTime);
        modes.addView(modeLoc);
        c2.addView(modes);
        modeTime.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.locPlace = 0; bindAll(); } });
        modeLoc.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { chooseLocationMode(); } });
        timePanel = new LinearLayout(this);
        timePanel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout dt = new LinearLayout(this);
        dateBtn = field(R.drawable.ic_calendar);
        timeBtn = field(R.drawable.ic_clock);
        LinearLayout.LayoutParams dlp = Ui.lpWeight(1.4f); dlp.setMarginEnd(dp(8));
        dt.addView(dateBtn, dlp);
        dt.addView(timeBtn, Ui.lpWeight(1));
        timePanel.addView(dt);
        dateBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickDate(); } });
        timeBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickTime(); } });
        HorizontalScrollView qs = new HorizontalScrollView(this);
        qs.setHorizontalScrollBarEnabled(false);
        qs.setHorizontalFadingEdgeEnabled(true);
        qs.setFadingEdgeLength(dp(24));
        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setPadding(0, dp(10), 0, dp(2));
        addQuick(quickRow, "+1 hora", 0);
        addQuick(quickRow, "Esta noite", 1);
        addQuick(quickRow, "Amanhã 9h", 2);
        addQuick(quickRow, "Próx. segunda", 3);
        addQuick(quickRow, "Próx. semana", 4);
        qs.addView(quickRow);
        timePanel.addView(qs);
        c2.addView(timePanel, Ui.lpMW());

        locPanel = new LinearLayout(this);
        locPanel.setOrientation(LinearLayout.VERTICAL);
        placeBtn = field(R.drawable.ic_place);
        placeBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickPlace(); } });
        locPanel.addView(placeBtn, Ui.lpMW());
        LinearLayout trig = new LinearLayout(this);
        trig.setPadding(0, dp(10), 0, 0);
        trigArrive = Ui.chip(this, "Ao chegar", true, 0);
        trigLeave = Ui.chip(this, "Ao sair", false, 0);
        trig.addView(trigArrive);
        trig.addView(trigLeave);
        trigArrive.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.locTrigger = Reminder.LOC_ARRIVE; bindAll(); } });
        trigLeave.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.locTrigger = Reminder.LOC_LEAVE; bindAll(); } });
        locPanel.addView(trig);
        locPanel.addView(Ui.divider(this));
        locRepeatSw = new Switch(this);
        locRepeatSw.setChecked(r.locRepeat);
        locPanel.addView(switchRow("Sempre que acontecer", "Se desligado, avisa só uma vez.", locRepeatSw));
        c2.addView(locPanel, Ui.lpMW());
        snoozeRow = new LinearLayout(this);
        snoozeRow.setGravity(Gravity.CENTER_VERTICAL);
        snoozeRow.setBackground(Ui.round(Ui.col(this, R.color.accentSoft), dp(12)));
        snoozeRow.setPadding(dp(12), dp(4), dp(4), dp(4));
        snoozeInfo = Ui.text(this, "", 14, R.color.accent, false);
        snoozeRow.addView(snoozeInfo, Ui.lpWeight(1));
        TextView rm = Ui.text(this, "REMOVER", 13, R.color.accent, true);
        rm.setPadding(dp(10), dp(10), dp(10), dp(10));
        rm.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.snoozedUntil = 0; bindAll(); } });
        snoozeRow.addView(rm);
        LinearLayout.LayoutParams slp = Ui.lpMW(); slp.topMargin = dp(10);
        c2.addView(snoozeRow, slp);
        body.addView(c2);

        // Repetir
        LinearLayout c3 = Ui.card(this);
        repeatCard = c3;
        c3.addView(Ui.cardHeader(this, R.drawable.ic_repeat, "Repetir"));
        repeatBtn = field(R.drawable.ic_repeat);
        repeatBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickRepeat(); } });
        c3.addView(repeatBtn);
        weekRow = new LinearLayout(this);
        weekRow.setPadding(0, dp(10), 0, 0);
        String[] letters = {"S", "T", "Q", "Q", "S", "S", "D"};
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            TextView d = Ui.text(this, letters[i], 15, R.color.text, true);
            d.setGravity(Gravity.CENTER);
            d.setContentDescription(Fmt.DAYS[i]);
            d.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                int m = r.weekMask == 0 ? Recurrence.bit(when.getDayOfWeek()) : r.weekMask;
                m ^= 1 << idx;
                if (m == 0) m = 1 << idx;
                r.weekMask = m;
                bindAll();
            } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1);
            lp.setMargins(dp(2), 0, dp(2), 0);
            weekRow.addView(d, lp);
            dayToggles[i] = d;
        }
        c3.addView(weekRow);
        customRow = new LinearLayout(this);
        customRow.setGravity(Gravity.CENTER_VERTICAL);
        customRow.setPadding(0, dp(10), 0, 0);
        customRow.addView(Ui.text(this, "A cada", 15, R.color.text, false));
        intervalE = new EditText(this);
        intervalE.setInputType(InputType.TYPE_CLASS_NUMBER);
        intervalE.setGravity(Gravity.CENTER);
        intervalE.setEms(3);
        customRow.addView(intervalE);
        unitBtn = field(0);
        unitBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickUnit(); } });
        customRow.addView(unitBtn, Ui.lpWeight(1));
        c3.addView(customRow);
        endRow = new LinearLayout(this);
        endRow.setOrientation(LinearLayout.VERTICAL);
        endRow.setPadding(0, 0, 0, 0);
        endRow.addView(Ui.divider(this));
        endRow.addView(Ui.subLabel(this, "Termina"));
        endBtn = field(0);
        endBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickEnd(); } });
        endRow.addView(endBtn);
        endDetail = new LinearLayout(this);
        endDetail.setGravity(Gravity.CENTER_VERTICAL);
        endDetail.setPadding(0, dp(8), 0, 0);
        endDateBtn = field(R.drawable.ic_calendar);
        endDateBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickEndDate(); } });
        endDetail.addView(endDateBtn, Ui.lpWeight(1));
        endCountE = new EditText(this);
        endCountE.setInputType(InputType.TYPE_CLASS_NUMBER);
        endCountE.setHint("N.º de vezes");
        endCountE.setEms(5);
        endDetail.addView(endCountE);
        endRow.addView(endDetail);
        c3.addView(endRow);
        body.addView(c3);

        // Avisos
        LinearLayout c4 = Ui.card(this);
        c4.addView(Ui.cardHeader(this, R.drawable.ic_bell, "Avisos"));
        c4.addView(Ui.subLabel(this, "Aviso antecipado"));
        preBtn = field(R.drawable.ic_clock);
        preBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickPre(); } });
        c4.addView(preBtn);
        c4.addView(Ui.divider(this));
        alarmSw = new Switch(this);
        c4.addView(switchRow("⏰ Tocar como despertador", "Som de alarme mesmo com o telemóvel em silêncio, em ecrã inteiro.", alarmSw));
        c4.addView(Ui.divider(this));
        importantSw = new Switch(this);
        c4.addView(switchRow("★ Importante · modo insistente", "Volta a avisar a cada " + Prefs.nagInterval(this) + " min até marcar como feito.", importantSw));
        body.addView(c4);

        // Categoria
        LinearLayout c5 = Ui.card(this);
        c5.addView(Ui.cardHeader(this, R.drawable.ic_label, "Categoria"));
        HorizontalScrollView cs = new HorizontalScrollView(this);
        cs.setHorizontalScrollBarEnabled(false);
        cs.setHorizontalFadingEdgeEnabled(true);
        cs.setFadingEdgeLength(dp(24));
        catRow = new LinearLayout(this);
        cs.addView(catRow);
        c5.addView(cs);
        body.addView(c5);

        // Checklist
        LinearLayout c6 = Ui.card(this);
        c6.addView(Ui.cardHeader(this, R.drawable.ic_list, "Checklist"));
        subList = new LinearLayout(this);
        subList.setOrientation(LinearLayout.VERTICAL);
        c6.addView(subList, Ui.lpMW());
        LinearLayout addRow = new LinearLayout(this);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        subInput = new EditText(this);
        subInput.setHint("Adicionar item (ou colar uma lista)");
        // Aceita várias linhas para que um texto colado mantenha as quebras de linha.
        subInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        subInput.setMaxLines(4);
        subInput.setTextSize(15);
        subInput.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable e) {
                // Enter ou texto colado com quebras de linha: cria um item por linha.
                if (e.toString().indexOf('\n') >= 0) addSub();
            }
        });
        addRow.addView(subInput, Ui.lpWeight(1));
        addRow.addView(Ui.iconButton(this, R.drawable.ic_paste, "Colar lista da área de transferência", new View.OnClickListener() { public void onClick(View v) { pasteList(); } }));
        addRow.addView(Ui.iconButton(this, R.drawable.ic_add, "Adicionar item", new View.OnClickListener() { public void onClick(View v) { addSub(); } }));
        c6.addView(addRow, Ui.lpMW());
        body.addView(c6);

        // Fotos e anexos
        LinearLayout c7 = Ui.card(this);
        c7.addView(Ui.cardHeader(this, R.drawable.ic_attach, "Fotos e anexos"));
        HorizontalScrollView ag = new HorizontalScrollView(this);
        ag.setHorizontalScrollBarEnabled(false);
        attGrid = new LinearLayout(this);
        ag.addView(attGrid);
        c7.addView(ag, Ui.lpMW());
        LinearLayout attBtns = new LinearLayout(this);
        attBtns.setPadding(0, dp(10), 0, 0);
        TextView cam = Ui.button(this, "📷  Tirar foto", false);
        cam.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { takePhoto(); } });
        LinearLayout.LayoutParams cl = Ui.lpWeight(1); cl.setMarginEnd(dp(8));
        attBtns.addView(cam, cl);
        TextView pick = Ui.button(this, "📎  Ficheiro", false);
        pick.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickFiles(); } });
        attBtns.addView(pick, Ui.lpWeight(1));
        c7.addView(attBtns, Ui.lpMW());
        body.addView(c7);

        summary = Ui.text(this, "", 13, R.color.text2, false);
        summary.setPadding(dp(24), dp(8), dp(24), dp(8));
        body.addView(summary);

        // Ações (edição)
        if (!isNew) {
            LinearLayout actions = new LinearLayout(this);
            actions.setPadding(dp(16), dp(8), dp(16), 0);
            TextView doneB = Ui.button(this, r.done ? "Reativar" : "Marcar como feito", false);
            doneB.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (r.done) Engine.uncomplete(EditActivity.this, Db.get(EditActivity.this).get(r.id));
                else Engine.complete(EditActivity.this, Db.get(EditActivity.this).get(r.id));
                finish();
            } });
            LinearLayout.LayoutParams alp = Ui.lpWeight(1); alp.setMarginEnd(dp(8));
            actions.addView(doneB, alp);
            TextView del = Ui.button(this, "Apagar", false);
            del.setTextColor(Ui.col(this, R.color.danger));
            del.setBackground(Ui.ripple(this, Ui.round(Ui.col(this, R.color.dangerSoft), dp(24)), dp(24)));
            del.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { confirmDelete(); } });
            actions.addView(del, Ui.lpWeight(1));
            body.addView(actions);
        }

        titleE.setText(r.title);
        titleE.setSelection(titleE.getText().length());
        notesE.setText(r.notes);
        importantSw.setChecked(r.important);
        alarmSw.setChecked(r.alarm);
        intervalE.setText(String.valueOf(Math.max(1, r.interval)));
        if (r.endCount > 0) endCountE.setText(String.valueOf(r.endCount));
        setContentView(root);
    }

    private LinearLayout switchRow(String title, String sub, Switch sw) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(this, title, 16, R.color.text, true));
        TextView s = Ui.text(this, sub, 13, R.color.text2, false);
        s.setPadding(0, dp(2), dp(8), 0);
        texts.addView(s);
        row.addView(texts, Ui.lpWeight(1));
        row.addView(sw);
        return row;
    }

    private TextView field(int icon) {
        TextView t = Ui.text(this, "", 15, R.color.text, false);
        t.setBackground(Ui.ripple(this, Ui.round(Ui.col(this, R.color.chip), dp(12)), dp(12)));
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setSingleLine(true);
        if (icon != 0) {
            android.graphics.drawable.Drawable d = getDrawable(icon).mutate();
            d.setTint(Ui.col(this, R.color.text2));
            d.setBounds(0, 0, dp(20), dp(20));
            t.setCompoundDrawablesRelative(d, null, null, null);
            t.setCompoundDrawablePadding(dp(10));
        }
        return t;
    }

    private void addQuick(LinearLayout row, String label, final int kind) {
        TextView c = Ui.chip(this, label, false, 0);
        c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            LocalDateTime now = LocalDateTime.now(Recurrence.zone).withSecond(0).withNano(0);
            int def = Prefs.defaultTime(EditActivity.this);
            LocalTime defT = LocalTime.of(def / 60, def % 60);
            switch (kind) {
                case 0: when = now.plusHours(1); break;
                case 1: when = now.toLocalDate().atTime(20, 0); if (!when.isAfter(now)) when = when.plusDays(1); break;
                case 2: when = now.toLocalDate().plusDays(1).atTime(9, 0); break;
                case 3: when = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(defT); break;
                default: when = now.toLocalDate().plusWeeks(1).atTime(when.toLocalTime()); break;
            }
            r.snoozedUntil = 0;
            bindAll();
        } });
        row.addView(c);
    }

    private void bindAll() {
        long now = System.currentTimeMillis();
        long ms = when.atZone(Recurrence.zone).toInstant().toEpochMilli();
        dateBtn.setText(Fmt.day(ms, now).equals("Hoje") || Fmt.day(ms, now).equals("Amanhã")
                ? Fmt.day(ms, now) + ", " + when.getDayOfMonth() + " " + Fmt.MONTHS_SHORT[when.getMonthValue() - 1]
                : Fmt.cap(Fmt.day(ms, now)));
        timeBtn.setText(Fmt.time(ms));
        r.dueAt = ms;
        boolean loc = r.isLocation();
        styleChip(modeTime, !loc);
        styleChip(modeLoc, loc);
        timePanel.setVisibility(loc ? View.GONE : View.VISIBLE);
        locPanel.setVisibility(loc ? View.VISIBLE : View.GONE);
        repeatCard.setVisibility(loc ? View.GONE : View.VISIBLE);
        if (loc) {
            Db.Place pl = Db.get(this).place(r.locPlace);
            placeBtn.setText(pl != null ? pl.name + " · raio " + pl.radius + " m" : "Escolher local");
            styleChip(trigArrive, r.locTrigger != Reminder.LOC_LEAVE);
            styleChip(trigLeave, r.locTrigger == Reminder.LOC_LEAVE);
        }
        renderSubs();
        renderAtts();
        repeatBtn.setText(r.repeatType == Reminder.REPEAT_NONE ? "Não repete" : Recurrence.describe(r));
        weekRow.setVisibility(r.repeatType == Reminder.REPEAT_WEEKLY ? View.VISIBLE : View.GONE);
        int mask = r.weekMask == 0 ? Recurrence.bit(when.getDayOfWeek()) : r.weekMask;
        for (int i = 0; i < 7; i++) {
            boolean on = (mask & (1 << i)) != 0;
            dayToggles[i].setBackground(Ui.ripple(this, Ui.round(Ui.col(this, on ? R.color.accent : R.color.chip), dp(20)), dp(20)));
            dayToggles[i].setTextColor(Ui.col(this, on ? R.color.onAccent : R.color.text));
        }
        customRow.setVisibility(r.repeatType == Reminder.REPEAT_CUSTOM ? View.VISIBLE : View.GONE);
        unitBtn.setText(UNIT_LABELS[Math.max(0, Math.min(3, r.intervalUnit - 1))]);
        endRow.setVisibility(r.isRecurring() ? View.VISIBLE : View.GONE);
        endBtn.setText(END_LABELS[r.endType]);
        endDetail.setVisibility(r.endType == Reminder.END_NEVER ? View.GONE : View.VISIBLE);
        endDateBtn.setVisibility(r.endType == Reminder.END_DATE ? View.VISIBLE : View.GONE);
        endCountE.setVisibility(r.endType == Reminder.END_COUNT ? View.VISIBLE : View.GONE);
        if (r.endType == Reminder.END_DATE) {
            if (r.endDate == 0) r.endDate = ms + 30L * 86400000L;
            endDateBtn.setText("Até " + Fmt.dateShort(r.endDate));
        }
        preBtn.setText(r.preAlertMin == 0 ? "Sem aviso antecipado" : Fmt.duration(r.preAlertMin) + " antes");
        catRow.removeAllViews();
        for (final Categories.Cat ct : Categories.all()) {
            TextView chip = Ui.chip(this, ct.name, r.category == ct.id, ct.color);
            chip.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.category = ct.id; bindAll(); } });
            catRow.addView(chip);
        }
        TextView manage = Ui.chip(this, "+ Gerir", false, 0);
        manage.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            startActivity(new android.content.Intent(EditActivity.this, CategoriesActivity.class));
        } });
        catRow.addView(manage);
        snoozeRow.setVisibility(r.isSnoozed() && !r.done ? View.VISIBLE : View.GONE);
        if (r.isSnoozed()) snoozeInfo.setText("Adiado para " + Fmt.dayTime(r.snoozedUntil, now).toLowerCase());
        StringBuilder s = new StringBuilder();
        if (!isNew) {
            s.append("Criado ").append(Fmt.dateShort(r.createdAt));
            if (r.isRecurring() && r.count > 0) s.append(" · ").append(r.count).append(r.count == 1 ? " ocorrência concluída" : " ocorrências concluídas");
        }
        summary.setText(s.toString());
        summary.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
    }

    @Override protected void onResume() {
        super.onResume();
        // Pode ter voltado da gestão de categorias.
        if (dateBtn != null) {
            if (Categories.get(r.category) == null) r.category = Categories.GENERAL;
            bindAll();
        }
    }

    // ------------------------------------------------------------------ Pickers
    private void pickDate() {
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            public void onDateSet(DatePicker v, int y, int m, int d) {
                when = LocalDate.of(y, m + 1, d).atTime(when.toLocalTime());
                if (r.repeatType == Reminder.REPEAT_MONTHLY) r.monthDay = d;
                r.snoozedUntil = 0;
                bindAll();
            }
        }, when.getYear(), when.getMonthValue() - 1, when.getDayOfMonth()).show();
    }

    private void pickTime() {
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            public void onTimeSet(TimePicker v, int h, int m) {
                when = when.toLocalDate().atTime(h, m);
                r.snoozedUntil = 0;
                bindAll();
            }
        }, when.getHour(), when.getMinute(), true).show();
    }

    private void pickRepeat() {
        new AlertDialog.Builder(this).setTitle("Repetir").setSingleChoiceItems(REPEAT_LABELS, r.repeatType, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                r.repeatType = which;
                if (which == Reminder.REPEAT_WEEKLY && r.weekMask == 0) r.weekMask = Recurrence.bit(when.getDayOfWeek());
                if (which == Reminder.REPEAT_MONTHLY) r.monthDay = when.getDayOfMonth();
                if (which == Reminder.REPEAT_CUSTOM && r.interval < 2) { r.interval = 2; r.intervalUnit = Reminder.UNIT_DAY; intervalE.setText("2"); }
                if (which == Reminder.REPEAT_NONE) r.endType = Reminder.END_NEVER;
                d.dismiss();
                bindAll();
            }
        }).show();
    }

    private void pickUnit() {
        new AlertDialog.Builder(this).setItems(UNIT_LABELS, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) { r.intervalUnit = which + 1; bindAll(); }
        }).show();
    }

    private void pickEnd() {
        new AlertDialog.Builder(this).setTitle("Termina").setSingleChoiceItems(END_LABELS, r.endType, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                r.endType = which;
                if (which == Reminder.END_COUNT && endCountE.getText().length() == 0) endCountE.setText("10");
                d.dismiss();
                bindAll();
            }
        }).show();
    }

    private void pickEndDate() {
        LocalDate d = java.time.Instant.ofEpochMilli(r.endDate).atZone(Recurrence.zone).toLocalDate();
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            public void onDateSet(DatePicker v, int y, int m, int day) {
                r.endDate = LocalDate.of(y, m + 1, day).atTime(12, 0).atZone(Recurrence.zone).toInstant().toEpochMilli();
                bindAll();
            }
        }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
    }

    private void pickPre() {
        String[] labels = new String[PRE_VALUES.length];
        int sel = 0;
        for (int i = 0; i < PRE_VALUES.length; i++) {
            labels[i] = PRE_VALUES[i] == 0 ? "Sem aviso" : Fmt.duration(PRE_VALUES[i]) + " antes";
            if (PRE_VALUES[i] == r.preAlertMin) sel = i;
        }
        new AlertDialog.Builder(this).setTitle("Aviso antecipado").setSingleChoiceItems(labels, sel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) { r.preAlertMin = PRE_VALUES[which]; d.dismiss(); bindAll(); }
        }).show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this).setMessage("Apagar este lembrete?")
                .setPositiveButton("Apagar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { Engine.delete(EditActivity.this, r); finish(); }
                })
                .setNegativeButton("Cancelar", null).show();
    }

    // ------------------------------------------------------------------ Guardar
    private void save() {
        String title = titleE.getText().toString().trim();
        if (title.isEmpty()) {
            titleE.setError("Escreva o lembrete");
            titleE.requestFocus();
            return;
        }
        r.title = title;
        r.notes = notesE.getText().toString().trim();
        r.important = importantSw.isChecked();
        r.alarm = alarmSw.isChecked();
        if (r.repeatType == Reminder.REPEAT_CUSTOM) {
            try { r.interval = Math.max(1, Integer.parseInt(intervalE.getText().toString().trim())); } catch (Exception e) { r.interval = 1; }
        } else r.interval = 1;
        if (r.endType == Reminder.END_COUNT) {
            try { r.endCount = Math.max(1, Integer.parseInt(endCountE.getText().toString().trim())); } catch (Exception e) { r.endCount = 10; }
        }
        if (r.isLocation()) {
            if (Db.get(this).place(r.locPlace) == null) { Toast.makeText(this, "Escolha um local", Toast.LENGTH_SHORT).show(); pickPlace(); return; }
            r.locRepeat = locRepeatSw.isChecked();
            r.repeatType = Reminder.REPEAT_NONE;
            boolean locChanged = isNew || original.locPlace != r.locPlace || original.locTrigger != r.locTrigger || original.done;
            if (locChanged) { r.resetAlertState(); r.done = false; r.doneAt = 0; r.snoozedUntil = 0; r.dueAt = System.currentTimeMillis(); }
            Engine.save(this, r);
            saveExtras();
            if (isNew) Toast.makeText(this, "Lembrete guardado: avisa " + (r.locTrigger == Reminder.LOC_LEAVE ? "ao sair de " : "ao chegar a ") + Db.get(this).place(r.locPlace).name, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (r.repeatType == Reminder.REPEAT_MONTHLY && r.monthDay == 0) r.monthDay = when.getDayOfMonth();
        long ms = when.atZone(Recurrence.zone).toInstant().toEpochMilli();
        if (r.isRecurring()) ms = Recurrence.alignFirst(r, ms);
        r.dueAt = ms;

        boolean timingChanged = isNew || original.dueAt != r.dueAt || original.repeatType != r.repeatType
                || original.weekMask != r.weekMask || original.interval != r.interval || original.intervalUnit != r.intervalUnit
                || original.snoozedUntil != r.snoozedUntil || original.preAlertMin != r.preAlertMin || original.locPlace != r.locPlace;
        if (timingChanged) {
            r.resetAlertState();
            r.done = false;
            r.doneAt = 0;
        }
        long now = System.currentTimeMillis();
        if (r.effectiveTime() < now - 60000 && timingChanged && !r.isRecurring()) {
            Toast.makeText(this, "Atenção: a data já passou — será notificado de imediato.", Toast.LENGTH_LONG).show();
        }
        if (r.isRecurring() && r.dueAt < now - 60000 && timingChanged) {
            // Começa na próxima ocorrência futura
            long x = r.dueAt;
            for (int i = 0; i < 100000 && x < now; i++) { long n = Recurrence.rawNext(r, x); if (n < 0) break; x = n; }
            r.dueAt = x;
        }
        if (timingChanged && !conflictConfirmed && ConflictDialog.check(this, r, new Runnable() {
            public void run() { conflictConfirmed = true; save(); }
        }, null)) return;
        Engine.save(this, r);
        saveExtras();
        if (isNew) Toast.makeText(this, "Lembrete para " + Fmt.dayTime(r.effectiveTime(), now).toLowerCase(), Toast.LENGTH_SHORT).show();
        finish();
    }

    // ------------------------------------------------------------------ Extras (checklist, anexos, local)

    private void styleChip(TextView c, boolean sel) {
        c.setTextColor(Ui.col(this, sel ? R.color.onAccent : R.color.text));
        c.setBackground(Ui.ripple(this, Ui.round(Ui.col(this, sel ? R.color.accent : R.color.chip), dp(18)), dp(18)));
    }

    private void saveExtras() {
        Db.get(this).saveSubtasks(r.id, subs);
        Db.get(this).saveAttachments(r.id, atts);
        Engine.changed(this);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("pendingPhoto", pendingPhoto);
    }

    private boolean addingSubs;

    private void addSub() {
        if (addingSubs) return;
        String t = subInput.getText().toString();
        if (t.trim().isEmpty()) { if (t.length() > 0) setSubInput(""); return; }
        int n = addLines(t);
        setSubInput("");
        if (n > 1) Toast.makeText(this, n + " itens adicionados", Toast.LENGTH_SHORT).show();
    }

    private void setSubInput(String v) {
        addingSubs = true;
        subInput.setText(v);
        addingSubs = false;
    }

    /** Um item por linha (marcadores como "-", "•", "1." e "[ ]" são removidos; "[x]" fica marcado). */
    private int addLines(String text) {
        List<pt.lembra.core.ChecklistText.Item> items = pt.lembra.core.ChecklistText.parse(text);
        for (pt.lembra.core.ChecklistText.Item it : items) {
            Db.Sub s = new Db.Sub();
            s.text = it.text;
            s.done = it.done;
            subs.add(s);
        }
        renderSubs();
        return items.size();
    }

    private void pasteList() {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        CharSequence txt = null;
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0)
            txt = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (txt == null || txt.toString().trim().isEmpty()) {
            Toast.makeText(this, "A área de transferência está vazia", Toast.LENGTH_SHORT).show();
            return;
        }
        int n = addLines(txt.toString());
        Toast.makeText(this, n == 1 ? "1 item adicionado" : n + " itens adicionados", Toast.LENGTH_SHORT).show();
    }

    private void renderSubs() {
        if (subList == null) return;
        subList.removeAllViews();
        int done = 0;
        for (Db.Sub s : subs) if (s.done) done++;
        if (!subs.isEmpty()) {
            TextView prog = Ui.text(this, done + " de " + subs.size() + " feitos", 13, R.color.text2, false);
            prog.setPadding(dp(2), 0, 0, dp(6));
            subList.addView(prog);
        }
        for (final Db.Sub s : subs) {
            final LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(44));
            row.setBackground(Ui.ripple(this, Ui.round(Ui.col(this, R.color.surface), dp(10)), dp(10)));
            // Pega para arrastar e mudar a posição
            ImageView handle = Ui.icon(this, R.drawable.ic_drag, R.color.text2, 36, 8);
            handle.setContentDescription("Arrastar para mudar a posição");
            handle.setOnTouchListener(dragListener(row, s));
            row.addView(handle);
            ImageView box = new ImageView(this);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(6));
            if (s.done) { g.setColor(Ui.col(this, R.color.accent)); box.setImageResource(R.drawable.ic_check); box.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.col(this, R.color.onAccent))); }
            else g.setStroke(dp(2), Ui.col(this, R.color.text2));
            box.setBackground(g);
            box.setPadding(dp(2), dp(2), dp(2), dp(2));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(22), dp(22));
            blp.setMarginEnd(dp(12));
            row.addView(box, blp);
            TextView t = Ui.text(this, s.text, 15, R.color.text, false);
            if (s.done) { t.setAlpha(0.55f); t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG); }
            row.addView(t, Ui.lpWeight(1));
            ImageView del = Ui.iconButton(this, R.drawable.ic_close, "Remover item", new View.OnClickListener() { public void onClick(View v) { subs.remove(s); renderSubs(); } });
            del.setAlpha(0.6f);
            row.addView(del);
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { s.done = !s.done; renderSubs(); } });
            row.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { subMenu(s); return true; } });
            subList.addView(row, Ui.lpMW());
        }
    }

    /** Arrastar pela pega: o item segue o dedo e troca de lugar ao passar metade do vizinho. */
    private View.OnTouchListener dragListener(final LinearLayout row, final Db.Sub s) {
        return new View.OnTouchListener() {
            float startY;
            public boolean onTouch(View v, android.view.MotionEvent e) {
                int offset = subList.getChildCount() - subs.size(); // linha de progresso no topo
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startY = e.getRawY();
                        row.setElevation(dp(8));
                        row.setScaleX(1.02f); row.setScaleY(1.02f);
                        row.getParent().requestDisallowInterceptTouchEvent(true);
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        disallowScroll(row, true);
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE: {
                        float dy = e.getRawY() - startY;
                        int i = subList.indexOfChild(row);
                        if (dy > 0 && i < subList.getChildCount() - 1) {
                            View next = subList.getChildAt(i + 1);
                            if (dy > next.getHeight() / 2f) {
                                subList.removeView(next);
                                subList.addView(next, i);
                                java.util.Collections.swap(subs, i - offset, i + 1 - offset);
                                startY += next.getHeight();
                                dy = e.getRawY() - startY;
                            }
                        } else if (dy < 0 && i > offset) {
                            View prev = subList.getChildAt(i - 1);
                            if (-dy > prev.getHeight() / 2f) {
                                subList.removeView(prev);
                                subList.addView(prev, i);
                                java.util.Collections.swap(subs, i - offset, i - 1 - offset);
                                startY -= prev.getHeight();
                                dy = e.getRawY() - startY;
                            }
                        }
                        row.setTranslationY(dy);
                        return true;
                    }
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        disallowScroll(row, false);
                        row.animate().translationY(0).scaleX(1f).scaleY(1f).setDuration(120).withEndAction(new Runnable() {
                            public void run() { row.setElevation(0); renderSubs(); }
                        }).start();
                        return true;
                }
                return false;
            }
        };
    }

    private void disallowScroll(View v, boolean disallow) {
        android.view.ViewParent p = v.getParent();
        while (p != null) { p.requestDisallowInterceptTouchEvent(disallow); p = p.getParent(); }
    }

    /** Toque longo num item: mover, editar texto ou remover. */
    private void subMenu(final Db.Sub s) {
        final int i = subs.indexOf(s);
        String[] items = {"Mover para o topo", "Subir", "Descer", "Mover para o fim", "Editar texto", "Remover"};
        new AlertDialog.Builder(this).setTitle(s.text).setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                switch (w) {
                    case 0: subs.remove(i); subs.add(0, s); break;
                    case 1: if (i > 0) java.util.Collections.swap(subs, i, i - 1); break;
                    case 2: if (i < subs.size() - 1) java.util.Collections.swap(subs, i, i + 1); break;
                    case 3: subs.remove(i); subs.add(s); break;
                    case 4: {
                        final EditText et = new EditText(EditActivity.this);
                        et.setText(s.text);
                        et.setSelection(s.text.length());
                        LinearLayout box = new LinearLayout(EditActivity.this);
                        box.setPadding(dp(22), dp(8), dp(22), 0);
                        box.addView(et, Ui.lpMW());
                        new AlertDialog.Builder(EditActivity.this).setTitle("Editar item").setView(box)
                                .setPositiveButton("Guardar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d2, int w2) {
                                    String t = et.getText().toString().trim();
                                    if (!t.isEmpty()) { s.text = t; renderSubs(); }
                                } })
                                .setNegativeButton("Cancelar", null).show();
                        return;
                    }
                    case 5: subs.remove(s); break;
                }
                renderSubs();
            }
        }).show();
    }

    private void renderAtts() {
        if (attGrid == null) return;
        attGrid.removeAllViews();
        if (atts.isEmpty()) {
            TextView e = Ui.text(this, "Junte fotos (ex.: a fatura) ou ficheiros a este lembrete.", 13, R.color.text2, false);
            attGrid.addView(e);
            return;
        }
        for (final Db.Att a : atts) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setBackground(Ui.ripple(this, Ui.round(Ui.col(this, R.color.chip), dp(14)), dp(14)));
            ImageView iv = new ImageView(this);
            android.graphics.Bitmap bm = pt.lembra.data.Attachments.isImage(a) ? pt.lembra.data.Attachments.thumbnail(this, a.file, dp(96)) : null;
            if (bm != null) {
                iv.setImageBitmap(bm);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setClipToOutline(true);
                iv.setBackground(Ui.round(0, dp(14)));
                tile.addView(iv, new LinearLayout.LayoutParams(dp(96), dp(96)));
            } else {
                iv.setImageResource(R.drawable.ic_file);
                iv.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.col(this, R.color.accent)));
                tile.addView(iv, new LinearLayout.LayoutParams(dp(36), dp(36)));
                TextView n = Ui.text(this, a.name, 11, R.color.text2, false);
                n.setGravity(Gravity.CENTER);
                Ui.ellipsize(n, 2);
                n.setPadding(dp(6), dp(4), dp(6), 0);
                tile.addView(n, new LinearLayout.LayoutParams(-1, -2));
            }
            tile.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openAttachment(EditActivity.this, a); } });
            tile.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) {
                new AlertDialog.Builder(EditActivity.this).setMessage("Remover «" + a.name + "» deste lembrete?")
                        .setPositiveButton("Remover", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { atts.remove(a); renderAtts(); } })
                        .setNegativeButton("Cancelar", null).show();
                return true;
            } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(96), dp(96));
            lp.setMarginEnd(dp(8));
            attGrid.addView(tile, lp);
        }
    }

    /** Abre um anexo na app adequada do telemóvel (galeria, leitor de PDF…). */
    static void openAttachment(android.app.Activity act, Db.Att a) {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW)
                    .setDataAndType(pt.lembra.data.Attachments.uri(a.file), a.mime)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            act.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(act, "Não há nenhuma app para abrir este ficheiro", Toast.LENGTH_SHORT).show();
        }
    }

    private void takePhoto() {
        pendingPhoto = pt.lembra.data.Attachments.newPhotoName();
        android.net.Uri out = pt.lembra.data.Attachments.uri(pendingPhoto);
        android.content.Intent i = new android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(android.provider.MediaStore.EXTRA_OUTPUT, out)
                .addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION | android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(android.content.ClipData.newRawUri("foto", out));
        try { startActivityForResult(i, REQ_CAMERA); }
        catch (Exception e) { Toast.makeText(this, "Não foi encontrada uma app de câmara", Toast.LENGTH_SHORT).show(); }
    }

    private void pickFiles() {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, REQ_FILES);
    }

    // ---- Local

    private void chooseLocationMode() {
        if (!Geo.hasForeground(this)) {
            new AlertDialog.Builder(this).setTitle("Lembretes por localização")
                    .setMessage("Para avisar quando chegar ou sair de um local, o Lembra precisa da sua localização. Ela é usada só neste telemóvel e nunca é enviada para lado nenhum.")
                    .setPositiveButton("Continuar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                        requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
                    } })
                    .setNegativeButton("Cancelar", null).show();
            return;
        }
        if (!Geo.hasBackground(this)) { askBackground(); return; }
        pickPlace();
    }

    private void askBackground() {
        new AlertDialog.Builder(this).setTitle("Localização em segundo plano")
                .setMessage("Para o aviso funcionar com a app fechada, escolha \"Permitir sempre\" no ecrã seguinte.")
                .setPositiveButton("Continuar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                    if (android.os.Build.VERSION.SDK_INT >= 29) requestPermissions(new String[]{android.Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQ_BGLOC);
                } })
                .setNegativeButton("Agora não", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { pickPlace(); } })
                .show();
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_LOC) {
            if (Geo.hasForeground(this)) { if (!Geo.hasBackground(this)) askBackground(); else pickPlace(); }
            else Toast.makeText(this, "Sem acesso à localização não é possível avisar num local", Toast.LENGTH_LONG).show();
        } else if (req == REQ_BGLOC) {
            if (!Geo.hasBackground(this)) Toast.makeText(this, "Sem \"Permitir sempre\" o aviso só funciona com a app aberta", Toast.LENGTH_LONG).show();
            pickPlace();
        }
    }

    private void pickPlace() {
        final List<Db.Place> places = Db.get(this).places();
        if (places.isEmpty()) {
            startActivityForResult(new android.content.Intent(this, PlacesActivity.class).putExtra("pick", true), REQ_PLACE);
            return;
        }
        String[] items = new String[places.size() + 1];
        for (int i = 0; i < places.size(); i++) items[i] = "📍 " + places.get(i).name;
        items[places.size()] = "＋ Novo local / gerir locais…";
        new AlertDialog.Builder(this).setTitle("Escolher local").setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                if (w < places.size()) { r.locPlace = places.get(w).id; r.repeatType = Reminder.REPEAT_NONE; bindAll(); }
                else startActivityForResult(new android.content.Intent(EditActivity.this, PlacesActivity.class).putExtra("pick", true), REQ_PLACE);
            }
        }).show();
    }

    @Override protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PLACE && res == RESULT_OK && data != null) {
            long id = data.getLongExtra("placeId", 0);
            if (id > 0) { r.locPlace = id; r.repeatType = Reminder.REPEAT_NONE; bindAll(); }
        } else if (req == REQ_CAMERA) {
            if (pendingPhoto != null) {
                java.io.File f = pt.lembra.data.Attachments.file(this, pendingPhoto);
                if (res == RESULT_OK && f.exists() && f.length() > 0) {
                    Db.Att a = new Db.Att();
                    a.file = pendingPhoto; a.name = "Foto " + Fmt.dayTime(System.currentTimeMillis(), System.currentTimeMillis()); a.mime = "image/jpeg";
                    atts.add(a);
                    renderAtts();
                } else f.delete();
                pendingPhoto = null;
            }
        } else if (req == REQ_FILES && res == RESULT_OK && data != null) {
            final List<android.net.Uri> uris = new ArrayList<android.net.Uri>();
            if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            new Thread(new Runnable() { public void run() {
                final List<Db.Att> added = new ArrayList<Db.Att>();
                int fail = 0;
                for (android.net.Uri u : uris) {
                    try { added.add(pt.lembra.data.Attachments.importUri(EditActivity.this, u)); } catch (Exception e) { fail++; }
                }
                final int f = fail;
                runOnUiThread(new Runnable() { public void run() {
                    atts.addAll(added);
                    renderAtts();
                    if (f > 0) Toast.makeText(EditActivity.this, f + " ficheiro(s) não puderam ser anexados", Toast.LENGTH_SHORT).show();
                } });
            } }).start();
        }
    }
}
