package pt.lembra.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import pt.lembra.R;
import pt.lembra.alarm.Engine;
import pt.lembra.alarm.Notifier;
import pt.lembra.core.Categories;
import pt.lembra.core.Fmt;
import pt.lembra.core.QuickParser;
import pt.lembra.core.Recurrence;
import pt.lembra.core.Reminder;
import pt.lembra.data.Db;
import pt.lembra.data.Prefs;

/** Ecrã principal: entrada rápida, filtros e lista agrupada de lembretes. */
public class MainActivity extends BaseActivity {
    static final int F_ALL = 0, F_TODAY = 1, F_IMPORTANT = 2, F_RECURRING = 3, F_DONE = 4, F_CAT = 100;

    private ListView list;
    private Adapter adapter;
    private EditText quick, search;
    private TextView preview, title, subtitle, undoText, emptyText;
    private LinearLayout chips, banner, undoBar, emptyView;
    private int filter = F_ALL;
    private List<Reminder> allCache = new ArrayList<Reminder>();
    private java.util.Map<Long, int[]> subProgress = new java.util.HashMap<Long, int[]>();
    private java.util.Map<Long, Integer> attCounts = new java.util.HashMap<Long, Integer>();
    private final java.util.Map<Long, String> placeNames = new java.util.HashMap<Long, String>();

    /** Lembrete por localização à espera do local (sem hora). */
    static boolean waitingLoc(Reminder r) { return r.isLocation() && !r.isSnoozed() && !r.notified && !r.done; }
    private int conflictTol = 0;
    private String query = "";
    private Reminder undoSnapshot;
    private boolean undoIsDelete;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideUndo = new Runnable() { public void run() { undoBar.setVisibility(View.GONE); } };
    private final Runnable refresher = new Runnable() { public void run() { refresh(); } };
    private final Runnable ticker = new Runnable() { public void run() { refresh(); handler.postDelayed(this, 60000); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Notifier.ensureChannels(this);
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() { public void run() { pt.lembra.data.Attachments.cleanOrphans(app); } }).start();
        buildUi();
        if (!Prefs.flag(this, "welcome")) {
            showWelcome();
        }
        handleIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); handleIntent(intent); }

    private void handleIntent(Intent i) {
        if (i != null && i.getBooleanExtra("focusQuick", false)) {
            quick.requestFocus();
            quick.postDelayed(new Runnable() { public void run() {
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(quick, 0);
            } }, 250);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        Recurrence.zone = java.time.ZoneId.systemDefault();
        Engine.addListener(refresher);
        refresh();
        if (Prefs.flag(this, "welcome")) setupPermissions();
        updateBanner();
        handler.postDelayed(ticker, 60000);
    }

    @Override protected void onPause() {
        super.onPause();
        Engine.removeListener(refresher);
        handler.removeCallbacks(ticker);
    }

    private int dp(float v) { return Ui.dp(this, v); }

    // ------------------------------------------------------------------ UI
    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // Cabeçalho
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(14), dp(8), dp(4));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        title = Ui.text(this, "Lembra", 28, R.color.text, true);
        subtitle = Ui.text(this, Fmt.dateLong(System.currentTimeMillis()), 14, R.color.text2, false);
        titles.addView(title);
        titles.addView(subtitle);
        search = new EditText(this);
        search.setHint("Pesquisar lembretes");
        search.setSingleLine(true);
        search.setTextSize(18);
        search.setVisibility(View.GONE);
        search.setBackground(null);
        search.addTextChangedListener(new SimpleWatcher() { public void afterTextChanged(Editable e) { query = e.toString(); refresh(); } });
        titles.addView(search);
        header.addView(titles, Ui.lpWeight(1));
        header.addView(Ui.iconButton(this, R.drawable.ic_calendar, "Calendário", new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, CalendarActivity.class)); }
        }));
        final ImageView searchBtn = Ui.iconButton(this, R.drawable.ic_search, "Pesquisar", null);
        searchBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleSearch(searchBtn); } });
        header.addView(searchBtn);
        final ImageView more = Ui.iconButton(this, R.drawable.ic_more, "Mais opções", null);
        more.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showMenu(more); } });
        header.addView(more);
        col.addView(header);

        // Aviso de fiabilidade
        banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setBackground(Ui.round(Ui.col(this, R.color.warnSoft), dp(14)));
        banner.setPadding(dp(14), dp(10), dp(10), dp(10));
        TextView bt = Ui.text(this, "Os lembretes podem não tocar a horas.", 14, R.color.warn, false);
        banner.addView(bt, Ui.lpWeight(1));
        TextView fix = Ui.text(this, "CORRIGIR", 14, R.color.warn, true);
        fix.setPadding(dp(10), dp(6), dp(6), dp(6));
        banner.addView(fix);
        banner.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); } });
        LinearLayout.LayoutParams blp = Ui.lpMW();
        blp.setMargins(dp(16), dp(6), dp(16), dp(2));
        col.addView(banner, blp);

        // Entrada rápida
        LinearLayout qcard = new LinearLayout(this);
        qcard.setOrientation(LinearLayout.VERTICAL);
        qcard.setBackground(Ui.stroke(Ui.col(this, R.color.surface), Ui.col(this, R.color.cardStroke), Math.max(1, dp(1)), dp(20)));
        qcard.setElevation(dp(2));
        qcard.setPadding(dp(16), dp(4), dp(6), dp(4));
        LinearLayout qrow = new LinearLayout(this);
        qrow.setGravity(Gravity.CENTER_VERTICAL);
        quick = new EditText(this);
        quick.setHint("Ex.: Ligar ao Tiago amanhã às 15h");
        quick.setBackground(null);
        quick.setTextSize(16);
        quick.setSingleLine(true);
        quick.setImeOptions(EditorInfo.IME_ACTION_DONE);
        quick.setPadding(0, dp(12), 0, dp(12));
        qrow.addView(quick, Ui.lpWeight(1));
        ImageView tune = Ui.iconButton(this, R.drawable.ic_tune, "Mais opções do lembrete", new View.OnClickListener() {
            public void onClick(View v) { openEditorFromQuick(); }
        });
        qrow.addView(tune);
        ImageView send = Ui.icon(this, R.drawable.ic_send, R.color.onAccent, 40, 9);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setShape(GradientDrawable.OVAL);
        sbg.setColor(Ui.col(this, R.color.accent));
        send.setBackground(Ui.ripple(this, sbg, dp(20)));
        send.setContentDescription("Criar lembrete");
        send.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { quickAdd(); } });
        qrow.addView(send);
        qcard.addView(qrow);
        preview = Ui.text(this, "", 13, R.color.accent, true);
        preview.setPadding(0, 0, dp(10), dp(10));
        preview.setVisibility(View.GONE);
        qcard.addView(preview);
        LinearLayout.LayoutParams qlp = Ui.lpMW();
        qlp.setMargins(dp(16), dp(10), dp(16), dp(6));
        col.addView(qcard, qlp);
        quick.addTextChangedListener(new SimpleWatcher() { public void afterTextChanged(Editable e) { updatePreview(); } });
        quick.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) { quickAdd(); return true; }
        });

        // Filtros
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(this);
        chips.setPadding(dp(16), dp(6), dp(16), dp(6));
        hs.addView(chips);
        col.addView(hs);

        // Lista
        FrameLayout listFrame = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(96));
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Object o = adapter.getItem(pos);
                if (o instanceof Reminder) openEditor(((Reminder) o).id);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                Object o = adapter.getItem(pos);
                if (o instanceof Reminder) { rowMenu(v, (Reminder) o); return true; }
                return false;
            }
        });
        listFrame.addView(list, new FrameLayout.LayoutParams(-1, -1));
        emptyView = new LinearLayout(this);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyView.setPadding(dp(32), dp(48), dp(32), dp(32));
        ImageView eicon = Ui.icon(this, R.drawable.ic_bell, R.color.accent, 72, 14);
        eicon.setBackground(Ui.round(Ui.col(this, R.color.accentSoft), dp(36)));
        emptyView.addView(eicon);
        emptyText = Ui.text(this, "", 15, R.color.text2, false);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(0, dp(16), 0, 0);
        emptyText.setLineSpacing(0, 1.2f);
        emptyView.addView(emptyText);
        listFrame.addView(emptyView, new FrameLayout.LayoutParams(-1, -2));
        col.addView(listFrame, new LinearLayout.LayoutParams(-1, 0, 1));

        // FAB
        ImageView fab = Ui.icon(this, R.drawable.ic_add, R.color.onAccent, 60, 16);
        fab.setBackground(Ui.ripple(this, Ui.round(Ui.col(this, R.color.accent), dp(20)), dp(20)));
        fab.setElevation(dp(6));
        fab.setContentDescription("Novo lembrete");
        fab.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openEditor(0); } });
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.BOTTOM | Gravity.END);
        flp.setMargins(0, 0, dp(20), dp(24));
        root.addView(fab, flp);

        // Barra de anular
        undoBar = new LinearLayout(this);
        undoBar.setGravity(Gravity.CENTER_VERTICAL);
        undoBar.setBackground(Ui.round(Ui.col(this, R.color.text), dp(14)));
        undoBar.setPadding(dp(16), dp(6), dp(6), dp(6));
        undoBar.setElevation(dp(8));
        undoText = new TextView(this);
        undoText.setTextColor(Ui.col(this, R.color.bg));
        undoText.setTextSize(14);
        undoBar.addView(undoText, Ui.lpWeight(1));
        TextView undoBtn = new TextView(this);
        undoBtn.setText("ANULAR");
        undoBtn.setTextColor(Ui.col(this, R.color.accentSoft));
        undoBtn.setTextSize(14);
        undoBtn.setPadding(dp(14), dp(12), dp(14), dp(12));
        undoBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { doUndo(); } });
        undoBar.addView(undoBtn);
        undoBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams ulp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        ulp.setMargins(dp(12), 0, dp(96), dp(28));
        root.addView(undoBar, ulp);

        setContentView(root);
    }

    private void toggleSearch(ImageView btn) {
        boolean show = search.getVisibility() != View.VISIBLE;
        search.setVisibility(show ? View.VISIBLE : View.GONE);
        title.setVisibility(show ? View.GONE : View.VISIBLE);
        subtitle.setVisibility(show ? View.GONE : View.VISIBLE);
        btn.setImageResource(show ? R.drawable.ic_close : R.drawable.ic_search);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (show) { search.requestFocus(); imm.showSoftInput(search, 0); }
        else { search.setText(""); imm.hideSoftInputFromWindow(search.getWindowToken(), 0); }
    }

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "Definições e fiabilidade");
        pm.getMenu().add(0, 2, 0, "Limpar concluídos");
        pm.getMenu().add(0, 4, 0, "Estatísticas e histórico");
        pm.getMenu().add(0, 5, 0, "Locais guardados");
        pm.getMenu().add(0, 3, 0, "Exemplos de escrita rápida");
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem item) {
                if (item.getItemId() == 1) startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                else if (item.getItemId() == 2) clearDone();
                else if (item.getItemId() == 4) startActivity(new Intent(MainActivity.this, StatsActivity.class));
                else if (item.getItemId() == 5) startActivity(new Intent(MainActivity.this, PlacesActivity.class));
                else showWelcome();
                return true;
            }
        });
        pm.show();
    }

    private void showWelcome() {
        new AlertDialog.Builder(this)
                .setTitle("Escreva como fala")
                .setMessage("Escreva o lembrete na caixa do topo e o Lembra percebe a data, a hora e a repetição:\n\n" +
                        "• Ligar ao Tiago amanhã às 15h30\n" +
                        "• Reunião na próxima sexta às 9\n" +
                        "• Tirar o bolo do forno daqui a 40 min\n" +
                        "• Pagar renda dia 8 todos os meses às 10h\n" +
                        "• Ginásio às segundas e quartas às 19h\n" +
                        "• Beber água a cada 2 horas\n" +
                        "• Enviar relatório dias úteis às 17h #trabalho !\n\n" +
                        "Use #categoria (pessoal, trabalho, saúde, casa, compras, finanças) e ! para importante (modo insistente).\n\n" +
                        "Na notificação pode marcar como Feito ou Adiar para outra data e hora.\n\n" +
                        "Importante: os lembretes ficam guardados só neste telemóvel e perdem-se se desinstalar a app. " +
                        "Faça cópias de segurança em ⋮ › Definições › Exportar lembretes.")
                .setPositiveButton("Percebi", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { Prefs.setFlag(MainActivity.this, "welcome"); setupPermissions(); }
                })
                .setCancelable(false)
                .show();
    }

    private void clearDone() {
        final List<Reminder> done = Db.get(this).doneList();
        if (done.isEmpty()) { toastBar("Não há lembretes concluídos", null, false); return; }
        new AlertDialog.Builder(this)
                .setMessage("Apagar " + done.size() + (done.size() == 1 ? " lembrete concluído?" : " lembretes concluídos?"))
                .setPositiveButton("Apagar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { for (Reminder r : done) Engine.delete(MainActivity.this, r); }
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void updateBanner() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean ok = nm.areNotificationsEnabled() && Engine.canExact(this);
        banner.setVisibility(ok ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ Entrada rápida
    private QuickParser.Result parseQuick() {
        int def = Prefs.defaultTime(this);
        return QuickParser.parse(quick.getText().toString(), System.currentTimeMillis(), def / 60, def % 60);
    }

    private void updatePreview() {
        String s = quick.getText().toString().trim();
        if (s.isEmpty()) { preview.setVisibility(View.GONE); return; }
        QuickParser.Result r = parseQuick();
        String sum = r.summary(System.currentTimeMillis());
        if (r.dueAt == null) sum = (sum.isEmpty() ? "" : sum + " · ") + "Sem hora — toque em enviar para escolher";
        preview.setText("→ " + sum);
        preview.setVisibility(View.VISIBLE);
    }

    /**
     * Assistente de permissões no primeiro arranque: pede, por ordem e uma vez cada,
     * notificações, alarmes exatos e isenção da otimização de bateria.
     * Volta a ser chamado em onResume, por isso continua depois de o utilizador regressar das definições.
     */
    private void setupPermissions() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !Prefs.flag(this, "asked_notif")) {
            Prefs.setFlag(this, "asked_notif");
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            return;
        }
        if (!Engine.canExact(this) && !Prefs.flag(this, "asked_exact") && Build.VERSION.SDK_INT >= 31) {
            Prefs.setFlag(this, "asked_exact");
            new AlertDialog.Builder(this).setTitle("Alarmes à hora exata")
                    .setMessage("Para os lembretes tocarem ao minuto, permita \"Alarmes e lembretes\" no ecrã seguinte.")
                    .setPositiveButton("Permitir", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            try {
                                startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        android.net.Uri.parse("package:" + getPackageName())));
                            } catch (Exception e) { setupPermissions(); }
                        }
                    })
                    .setNegativeButton("Agora não", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { setupPermissions(); }
                    }).setCancelable(false).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 34 && !getSystemService(NotificationManager.class).canUseFullScreenIntent()
                && !Prefs.flag(this, "asked_fullscreen")) {
            Prefs.setFlag(this, "asked_fullscreen");
            new AlertDialog.Builder(this).setTitle("Despertador em ecrã inteiro")
                    .setMessage("Para o lembrete aparecer em ecrã inteiro com o telemóvel bloqueado, como um despertador, permita \"Notificações em ecrã inteiro\" no ecrã seguinte.")
                    .setPositiveButton("Permitir", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            try {
                                startActivity(new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT",
                                        android.net.Uri.parse("package:" + getPackageName())));
                            } catch (Exception e) { setupPermissions(); }
                        }
                    })
                    .setNegativeButton("Agora não", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { setupPermissions(); }
                    }).setCancelable(false).show();
            return;
        }
        if (!android.provider.Settings.canDrawOverlays(this) && !Prefs.flag(this, "asked_overlay")) {
            Prefs.setFlag(this, "asked_overlay");
            new AlertDialog.Builder(this).setTitle("Abrir o alarme com o telemóvel em uso")
                    .setMessage("Para o ecrã do alarme abrir sozinho com o telemóvel desbloqueado (e para lhe mostrar os lembretes por tratar ao desbloquear), ative \"Mostrar sobre outras apps\" para o Lembra no ecrã seguinte.")
                    .setPositiveButton("Permitir", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            try {
                                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:" + getPackageName())));
                            } catch (Exception e) { setupPermissions(); }
                        }
                    })
                    .setNegativeButton("Agora não", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { setupPermissions(); }
                    }).setCancelable(false).show();
            return;
        }
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName()) && !Prefs.flag(this, "asked_battery")) {
            Prefs.setFlag(this, "asked_battery");
            new AlertDialog.Builder(this).setTitle("Funcionar em segundo plano")
                    .setMessage("Alguns telemóveis bloqueiam apps em segundo plano para poupar bateria, e os lembretes deixam de tocar. Permita que o Lembra funcione sem restrições (o consumo é praticamente nulo).")
                    .setPositiveButton("Permitir", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            try {
                                startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        android.net.Uri.parse("package:" + getPackageName())));
                            } catch (Exception e) {
                                startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                            }
                        }
                    })
                    .setNegativeButton("Agora não", null).setCancelable(false).show();
        }
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        updateBanner();
        setupPermissions();
    }

    private void quickAdd() {
        String s = quick.getText().toString().trim();
        if (s.isEmpty()) { openEditor(0); return; }
        QuickParser.Result p = parseQuick();
        if (p.dueAt == null || p.title.isEmpty()) { openEditorFromQuick(); return; }
        Reminder r = p.toReminder();
        r.preAlertMin = Prefs.defaultPreAlert(this);
        r.alarm = Prefs.alarmDefault(this);
        Engine.save(this, r);
        quick.setText("");
        long now = System.currentTimeMillis();
        toastBar("Criado para " + Fmt.dayTime(r.dueAt, now).toLowerCase(Locale.ROOT), r, true);
        final long newId = r.id;
        ConflictDialog.check(this, r, null, new Runnable() { public void run() { openEditor(newId); } });
        undoIsDelete = true;
    }

    private void openEditorFromQuick() {
        Intent i = new Intent(this, EditActivity.class).putExtra("prefill", quick.getText().toString());
        quick.setText("");
        startActivity(i);
    }

    private void openEditor(long id) {
        startActivity(new Intent(this, EditActivity.class).putExtra("id", id));
    }

    // ------------------------------------------------------------------ Ações sobre linhas
    private void rowMenu(View anchor, final Reminder r) {
        PopupMenu pm = new PopupMenu(this, anchor, Gravity.END);
        if (r.done) pm.getMenu().add(0, 4, 0, "Reativar");
        else {
            pm.getMenu().add(0, 1, 0, "Adiar…");
            pm.getMenu().add(0, 5, 0, "Marcar como feito");
        }
        pm.getMenu().add(0, 2, 0, "Duplicar");
        pm.getMenu().add(0, 3, 0, "Apagar");
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem item) {
                switch (item.getItemId()) {
                    case 1: startActivity(new Intent(MainActivity.this, SnoozeActivity.class).putExtra("id", r.id)); break;
                    case 2: {
                        Reminder c = r.copy();
                        c.id = 0; c.done = false; c.doneAt = 0; c.createdAt = 0; c.resetAlertState(); c.snoozedUntil = 0;
                        c.title = r.title + " (cópia)";
                        long now = System.currentTimeMillis();
                        if (c.dueAt < now) c.dueAt = now + 3600000L;
                        Engine.save(MainActivity.this, c);
                        openEditor(c.id);
                        break;
                    }
                    case 3: {
                        Reminder snap = r.copy();
                        Engine.delete(MainActivity.this, r);
                        toastBar("Lembrete apagado", snap, true);
                        undoIsDelete = false;
                        break;
                    }
                    case 4: Engine.uncomplete(MainActivity.this, r); break;
                    case 5: complete(r); break;
                }
                return true;
            }
        });
        pm.show();
    }

    private void complete(Reminder r) {
        Reminder snap = r.copy();
        Engine.complete(this, r);
        Reminder after = Db.get(this).get(r.id);
        String msg = after != null && !after.done ? "Feito · próximo: " + Fmt.dayTime(after.dueAt, System.currentTimeMillis()).toLowerCase(Locale.ROOT) : "Concluído";
        toastBar(msg, snap, true);
        undoIsDelete = false;
    }

    /** Mostra a barra inferior; se {@code snapshot} != null permite anular. */
    private void toastBar(String msg, Reminder snapshot, boolean undoable) {
        undoSnapshot = undoable ? snapshot : null;
        undoText.setText(msg);
        undoBar.getChildAt(1).setVisibility(undoSnapshot != null ? View.VISIBLE : View.GONE);
        undoBar.setVisibility(View.VISIBLE);
        undoBar.setAlpha(0f);
        undoBar.animate().alpha(1f).setDuration(150).start();
        handler.removeCallbacks(hideUndo);
        handler.postDelayed(hideUndo, 5000);
    }

    private void doUndo() {
        if (undoSnapshot == null) return;
        if (undoIsDelete) Engine.delete(this, undoSnapshot); else Engine.restore(this, undoSnapshot);
        undoSnapshot = null;
        undoBar.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------ Dados
    static String norm(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        subtitle.setText(Fmt.dateLong(now));
        List<Reminder> all = Db.get(this).all();
        allCache = all;
        subProgress = Db.get(this).subtaskProgress();
        attCounts = Db.get(this).attachmentCounts();
        placeNames.clear();
        for (Db.Place p : Db.get(this).places()) placeNames.put(p.id, p.name);
        conflictTol = Prefs.conflictTolerance(this);
        String q = norm(query.trim());

        // Filtros (chips)
        int active = 0, today = 0, imp = 0, rec = 0, done = 0;
        java.util.Set<Integer> usedCats = new java.util.HashSet<Integer>();
        LocalDate td = LocalDate.now(Recurrence.zone);
        for (Reminder r : all) {
            if (r.done) { done++; continue; }
            active++;
            if (!waitingLoc(r) && !LocalDate.from(java.time.Instant.ofEpochMilli(r.effectiveTime()).atZone(Recurrence.zone)).isAfter(td)) today++;
            if (r.important) imp++;
            if (r.isRecurring()) rec++;
            if (r.category > 0) usedCats.add(r.category);
        }
        chips.removeAllViews();
        addChip("Todos " + active, F_ALL, 0);
        addChip("Hoje " + today, F_TODAY, 0);
        if (imp > 0 || filter == F_IMPORTANT) addChip("Importantes", F_IMPORTANT, 0);
        if (rec > 0 || filter == F_RECURRING) addChip("Recorrentes", F_RECURRING, 0);
        for (Categories.Cat ct : Categories.all())
            if (ct.id > 0 && (usedCats.contains(ct.id) || filter == F_CAT + ct.id)) addChip(ct.name, F_CAT + ct.id, ct.color);
        addChip("Concluídos" + (done > 0 ? " " + done : ""), F_DONE, 0);

        // Lista
        List<Reminder> shown = new ArrayList<Reminder>();
        for (Reminder r : all) {
            if (filter == F_DONE ? !r.done : r.done) continue;
            if (!q.isEmpty() && !norm(r.title + " " + r.notes).contains(q)) continue;
            switch (filter) {
                case F_TODAY:
                    if (waitingLoc(r)) continue;
                    if (LocalDate.from(java.time.Instant.ofEpochMilli(r.effectiveTime()).atZone(Recurrence.zone)).isAfter(td)) continue;
                    break;
                case F_IMPORTANT: if (!r.important) continue; break;
                case F_RECURRING: if (!r.isRecurring()) continue; break;
                default: if (filter >= F_CAT && r.category != filter - F_CAT) continue;
            }
            shown.add(r);
        }
        List<Object> items = new ArrayList<Object>();
        if (filter == F_DONE) {
            Collections.sort(shown, new Comparator<Reminder>() {
                public int compare(Reminder a, Reminder b) { return Long.compare(b.doneAt, a.doneAt); }
            });
            items.addAll(shown);
        } else {
            Collections.sort(shown, new Comparator<Reminder>() {
                public int compare(Reminder a, Reminder b) {
                    boolean la = waitingLoc(a), lb = waitingLoc(b);
                    if (la != lb) return la ? 1 : -1; // por localização no fim
                    return Long.compare(a.effectiveTime(), b.effectiveTime());
                }
            });
            String lastHeader = null;
            for (Reminder r : shown) {
                String h = waitingLoc(r) ? "Ao chegar ou sair de um local" : section(r.effectiveTime(), now, td);
                if (!h.equals(lastHeader)) { items.add(h); lastHeader = h; }
                items.add(r);
            }
        }
        adapter.items = items;
        adapter.notifyDataSetChanged();
        boolean empty = items.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            if (!q.isEmpty()) emptyText.setText("Nenhum lembrete encontrado para \"" + query.trim() + "\".");
            else if (filter == F_DONE) emptyText.setText("Ainda não concluiu nenhum lembrete.");
            else if (filter == F_ALL) emptyText.setText("Tudo em dia!\nEscreva um lembrete na caixa acima ou toque em +.");
            else emptyText.setText("Nada por aqui.");
        }
        updateBanner();
    }

    private static String section(long t, long now, LocalDate today) {
        if (t < now - 30000) return "Atrasados";
        LocalDate d = java.time.Instant.ofEpochMilli(t).atZone(Recurrence.zone).toLocalDate();
        if (d.equals(today)) return "Hoje";
        if (d.equals(today.plusDays(1))) return "Amanhã";
        if (!d.isAfter(today.plusDays(7))) return "Próximos 7 dias";
        return "Mais tarde";
    }

    private void addChip(String label, final int f, int dot) {
        TextView c = Ui.chip(this, label, filter == f, dot);
        c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { filter = f; refresh(); if (adapter.getCount() > 0) list.setSelection(0); } });
        chips.addView(c);
    }

    // ------------------------------------------------------------------ Adapter
    private class Adapter extends BaseAdapter {
        List<Object> items = new ArrayList<Object>();
        public int getCount() { return items.size(); }
        public Object getItem(int p) { return p >= 0 && p < items.size() ? items.get(p) : null; }
        public long getItemId(int p) {
            // O ListView pode pedir o id da posição 0 logo após a lista ficar vazia (sincronização de scroll).
            if (p < 0 || p >= items.size()) return -1;
            Object o = items.get(p);
            return o instanceof Reminder ? ((Reminder) o).id : -p - 1;
        }
        public int getViewTypeCount() { return 2; }
        public int getItemViewType(int p) { return getItem(p) instanceof Reminder ? 1 : 0; }
        public boolean isEnabled(int p) { return getItem(p) instanceof Reminder; }

        public View getView(int pos, View cv, ViewGroup parent) {
            Object o = items.get(pos);
            if (!(o instanceof Reminder)) {
                TextView h = cv instanceof TextView ? (TextView) cv : null;
                if (h == null) {
                    h = Ui.text(MainActivity.this, "", 13, R.color.text2, true);
                    h.setLetterSpacing(0.06f);
                    h.setPadding(dp(22), dp(16), dp(16), dp(6));
                }
                String s = (String) o;
                h.setText(s.toUpperCase(Locale.ROOT));
                h.setTextColor(Ui.col(MainActivity.this, s.equals("Atrasados") ? R.color.danger : R.color.text2));
                return h;
            }
            Row row = cv instanceof Row ? (Row) cv : new Row(MainActivity.this);
            row.bind((Reminder) o);
            return row;
        }
    }

    /** Linha de um lembrete. */
    private class Row extends FrameLayout {
        final LinearLayout card;
        final View bar;
        final ImageView check;
        final TextView titleV, meta, notes;
        Reminder r;

        Row(Context c) {
            super(c);
            setPadding(dp(16), dp(4), dp(16), dp(4));
            card = new LinearLayout(c);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(Ui.ripple(c, Ui.stroke(Ui.col(c, R.color.surface), Ui.col(c, R.color.cardStroke), Math.max(1, dp(1)), dp(16)), dp(16)));
            card.setPadding(0, dp(12), dp(14), dp(12));
            card.setDuplicateParentStateEnabled(true);
            bar = new View(c);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(4), dp(36));
            barLp.setMarginEnd(dp(8));
            card.addView(bar, barLp);
            check = new ImageView(c);
            check.setPadding(dp(5), dp(5), dp(5), dp(5));
            check.setContentDescription("Marcar como feito");
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(30), dp(30));
            clp.setMarginEnd(dp(12));
            card.addView(check, clp);
            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(LinearLayout.VERTICAL);
            titleV = Ui.text(c, "", 16, R.color.text, false);
            Ui.ellipsize(titleV, 2);
            meta = Ui.text(c, "", 13, R.color.text2, false);
            meta.setPadding(0, dp(2), 0, 0);
            Ui.ellipsize(meta, 3);
            notes = Ui.text(c, "", 13, R.color.text2, false);
            Ui.ellipsize(notes, 1);
            notes.setAlpha(0.8f);
            texts.addView(titleV);
            texts.addView(meta);
            texts.addView(notes);
            card.addView(texts, Ui.lpWeight(1));
            addView(card, new FrameLayout.LayoutParams(-1, -2));
            check.setOnClickListener(new OnClickListener() { public void onClick(View v) { onCheck(); } });
        }

        void bind(Reminder rem) {
            r = rem;
            Context c = getContext();
            long now = System.currentTimeMillis();
            int catColor = Categories.color(r.category);
            bar.setBackground(Ui.round(r.category > 0 ? catColor : 0, dp(2)));
            int ring = r.important ? Ui.col(c, R.color.danger) : Ui.col(c, R.color.text2);
            if (r.done) {
                check.setImageResource(R.drawable.ic_check);
                check.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.col(c, R.color.onAccent)));
                GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(Ui.col(c, R.color.accent));
                check.setBackground(g);
            } else {
                check.setImageDrawable(null);
                GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setStroke(dp(2), ring);
                check.setBackground(g);
            }
            titleV.setText((r.important ? "★ " : "") + r.title);
            titleV.setAlpha(r.done ? 0.55f : 1f);
            titleV.setPaintFlags(r.done ? titleV.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    : titleV.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            StringBuilder m = new StringBuilder();
            if (r.done) {
                m.append("Concluído ").append(Fmt.dayTime(r.doneAt, now).toLowerCase(Locale.ROOT));
            } else if (waitingLoc(r)) {
                String pn = placeNames.get(r.locPlace);
                m.append("📍 ").append(r.locTrigger == Reminder.LOC_LEAVE ? "Ao sair de " : "Ao chegar a ").append(pn != null ? pn : "local");
                if (r.locRepeat) m.append(" · sempre");
                if (r.alarm) m.append(" · ⏰");
                meta.setTextColor(Ui.col(c, R.color.text2));
            } else {
                long eff = r.effectiveTime();
                boolean late = eff < now - 30000;
                m.append(Fmt.dayTime(eff, now));
                if (late) m.append(" (").append(Fmt.relative(eff, now)).append(")");
                if (r.isSnoozed()) m.append(" · adiado");
                if (r.alarm) m.append(" · ⏰");
                if (r.isRecurring()) m.append(" · ↻ ").append(Recurrence.describe(r));
                if (r.preAlertMin > 0) m.append(" · aviso ").append(Fmt.duration(r.preAlertMin)).append(" antes");
                meta.setTextColor(Ui.col(c, late ? R.color.danger : R.color.text2));
            }
            if (r.done) meta.setTextColor(Ui.col(c, R.color.text2));
            if (!r.done && conflictTol >= 0) {
                pt.lembra.core.Occurrences.Conflict cf = pt.lembra.core.Occurrences.nextConflict(r, allCache, conflictTol);
                if (cf != null) {
                    m.append("\n⚠ Coincide com «").append(cf.other.title).append("»");
                    meta.setTextColor(Ui.col(c, R.color.danger));
                }
            }
            if (r.category > 0) m.append(" · ").append(Categories.name(r.category));
            int[] sp = subProgress.get(r.id);
            if (sp != null && sp[1] > 0) m.append(" · ☑ ").append(sp[0]).append("/").append(sp[1]);
            Integer ac = attCounts.get(r.id);
            if (ac != null && ac > 0) m.append(" · 📎 ").append(ac);
            meta.setText(m.toString());
            boolean hasNotes = r.notes != null && !r.notes.trim().isEmpty();
            notes.setVisibility(hasNotes ? View.VISIBLE : View.GONE);
            if (hasNotes) notes.setText(r.notes.trim().replace('\n', ' '));
        }

        void onCheck() {
            final Reminder target = r;
            if (target.done) { Engine.uncomplete(MainActivity.this, target); return; }
            check.setImageResource(R.drawable.ic_check);
            check.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.col(getContext(), R.color.onAccent)));
            GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(Ui.col(getContext(), R.color.accent));
            check.setBackground(g);
            check.setScaleX(0.6f); check.setScaleY(0.6f);
            check.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
            card.animate().alpha(0.4f).setStartDelay(150).setDuration(250).withEndAction(new Runnable() {
                public void run() { card.setAlpha(1f); complete(target); }
            }).start();
        }
    }

    abstract static class SimpleWatcher implements TextWatcher {
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        public void onTextChanged(CharSequence s, int a, int b, int c) {}
    }
}
