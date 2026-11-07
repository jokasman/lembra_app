package pt.lembra.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.Locale;

import pt.lembra.R;
import pt.lembra.alarm.Engine;
import pt.lembra.alarm.AlarmReceiver;
import pt.lembra.core.Categories;
import pt.lembra.core.Fmt;
import pt.lembra.data.Db;
import pt.lembra.data.Prefs;

/** Definições, verificação de fiabilidade e backup. */
public class SettingsActivity extends BaseActivity {
    static final int REQ_EXPORT = 10, REQ_IMPORT = 11, REQ_SOUND = 12;
    private LinearLayout body;

    private int dp(float v) { return Ui.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(8), dp(12), dp(4));
        top.addView(Ui.iconButton(this, R.drawable.ic_back, "Voltar", new View.OnClickListener() { public void onClick(View v) { finish(); } }));
        TextView t = Ui.text(this, "Definições", 20, R.color.text, true);
        t.setPadding(dp(6), 0, 0, 0);
        top.addView(t);
        root.addView(top);
        ScrollView sv = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(32));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); build(); }

    private void build() {
        body.removeAllViews();

        // ---- Fiabilidade
        LinearLayout rel = Ui.card(this);
        rel.addView(Ui.sectionLabel(this, "Verificação de fiabilidade"));
        NotificationManager nm = getSystemService(NotificationManager.class);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean notif = nm.areNotificationsEnabled();
        boolean exact = Engine.canExact(this);
        boolean battery = pm.isIgnoringBatteryOptimizations(getPackageName());
        int ok = (notif ? 1 : 0) + (exact ? 1 : 0) + (battery ? 1 : 0);
        TextView score = Ui.text(this, ok == 3 ? "Tudo pronto: os lembretes vão tocar a horas." :
                (3 - ok) + (3 - ok == 1 ? " ponto precisa" : " pontos precisam") + " de atenção.", 15, ok == 3 ? R.color.accent : R.color.warn, true);
        score.setPadding(dp(4), 0, 0, dp(6));
        rel.addView(score);
        rel.addView(check("Notificações", notif, "Permitidas", "Bloqueadas — não verá os lembretes", new Runnable() { public void run() {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } }));
        rel.addView(check("Alarmes exatos", exact, "Permitidos — toca ao minuto", "Não permitidos — pode atrasar vários minutos", new Runnable() { public void run() {
            if (Build.VERSION.SDK_INT >= 31)
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
        } }));
        rel.addView(check("Otimização de bateria", battery, "Sem restrições", "Restrita — o sistema pode adiar ou bloquear lembretes", new Runnable() { public void run() {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        } }));
        if (Build.VERSION.SDK_INT >= 34) {
            boolean fs = nm.canUseFullScreenIntent();
            rel.addView(check("Ecrã inteiro (despertador)", fs, "Permitido", "Não permitido — o despertador aparece só como notificação", new Runnable() { public void run() {
                startActivity(new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", Uri.parse("package:" + getPackageName())));
            } }));
        }
        if (pt.lembra.alarm.Geo.anyLocationReminders(this)) {
            boolean geoOk = pt.lembra.alarm.Geo.hasForeground(this) && pt.lembra.alarm.Geo.hasBackground(this);
            rel.addView(check("Localização (lembretes por local)", geoOk, "Permitida sempre",
                    "Sem \"Permitir sempre\" — os avisos por local podem não disparar", new Runnable() { public void run() {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            } }));
        }
        boolean overlay = Settings.canDrawOverlays(this);
        rel.addView(check("Abrir por cima de outras apps", overlay, "Permitido — o alarme abre o ecrã com o telemóvel desbloqueado",
                "Não permitido — com o telemóvel desbloqueado só aparece a notificação", new Runnable() { public void run() {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } }));
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        rel.addView(check("Volume do despertador", true, "Definido no Lembra: " + Prefs.alarmVolume(this) + "% (independente do sistema)", "", null));
        String diag = Prefs.str(this, "alarm_diag");
        if (diag != null) {
            TextView dv = Ui.text(this, "Diagnóstico: " + diag, 12, R.color.text2, false);
            dv.setPadding(dp(4), dp(8), dp(4), 0);
            rel.addView(dv);
        }
        final String mf = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        if (mf.contains("xiaomi") || mf.contains("redmi") || mf.contains("poco")) {
            TextView xb = Ui.button(this, "Abrir permissões Xiaomi", false);
            xb.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                try {
                    startActivity(new Intent("miui.intent.action.APP_PERM_EDITOR")
                            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                            .putExtra("extra_pkgname", getPackageName()));
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                }
            } });
            LinearLayout.LayoutParams xlp = Ui.lpMW(); xlp.topMargin = dp(10);
            rel.addView(xb, xlp);
        }
        String tip = vendorTip();
        if (tip != null) {
            TextView tv = Ui.text(this, tip, 13, R.color.text2, false);
            tv.setPadding(dp(4), dp(8), dp(4), dp(4));
            tv.setLineSpacing(0, 1.15f);
            rel.addView(tv);
        }
        TextView test = Ui.button(this, "Testar despertador (10 s)", false);
        test.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            AlarmReceiver.scheduleTest(SettingsActivity.this, 10000);
            Toast.makeText(SettingsActivity.this, "Ponha o telemóvel em silêncio, bloqueie o ecrã e aguarde 10 segundos", Toast.LENGTH_LONG).show();
        } });
        LinearLayout.LayoutParams tlp = Ui.lpMW(); tlp.topMargin = dp(10);
        rel.addView(test, tlp);
        body.addView(rel);

        // ---- Preferências
        LinearLayout pref = Ui.card(this);
        pref.addView(Ui.sectionLabel(this, "Preferências"));
        pref.addView(Ui.optionRow(this, "Adiamento rápido na notificação", "+" + Fmt.duration(Prefs.quickSnooze(this)), new View.OnClickListener() {
            public void onClick(View v) {
                final int[] vals = {5, 10, 15, 30, 60};
                choose("Adiamento rápido", vals, Prefs.quickSnooze(SettingsActivity.this), new Chosen() { public void on(int v) { Prefs.setQuickSnooze(SettingsActivity.this, v); } });
            }
        }));
        pref.addView(Ui.optionRow(this, "Intervalo do modo insistente", "Repete a cada " + Prefs.nagInterval(this) + " min", new View.OnClickListener() {
            public void onClick(View v) {
                final int[] vals = {2, 5, 10, 15, 30};
                choose("Repetir a cada", vals, Prefs.nagInterval(SettingsActivity.this), new Chosen() { public void on(int v) { Prefs.setNagInterval(SettingsActivity.this, v); } });
            }
        }));
        final int dt = Prefs.defaultTime(this);
        pref.addView(Ui.optionRow(this, "Hora por defeito", "Usada quando só indica o dia (" + String.format(Locale.ROOT, "%02d:%02d", dt / 60, dt % 60) + ")", new View.OnClickListener() {
            public void onClick(View v) {
                new TimePickerDialog(SettingsActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    public void onTimeSet(TimePicker tp, int h, int m) { Prefs.setDefaultTime(SettingsActivity.this, h * 60 + m); build(); }
                }, dt / 60, dt % 60, true).show();
            }
        }));
        int pre = Prefs.defaultPreAlert(this);
        pref.addView(Ui.optionRow(this, "Aviso antecipado por defeito", pre == 0 ? "Nenhum" : Fmt.duration(pre) + " antes", new View.OnClickListener() {
            public void onClick(View v) {
                choose("Aviso antecipado", EditActivity.PRE_VALUES, Prefs.defaultPreAlert(SettingsActivity.this), new Chosen() { public void on(int v) { Prefs.setDefaultPreAlert(SettingsActivity.this, v); } });
            }
        }));
        final boolean ad = Prefs.alarmDefault(this);
        pref.addView(Ui.optionRow(this, "Novos lembretes tocam como despertador", ad ? "Sim — som de alarme mesmo em silêncio" : "Não — notificação normal", new View.OnClickListener() {
            public void onClick(View v) { Prefs.setAlarmDefault(SettingsActivity.this, !ad); build(); }
        }));
        pref.addView(Ui.optionRow(this, "Categorias", Categories.all().size() + " categorias · criar, mudar nome e cor, reordenar", new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, CategoriesActivity.class)); }
        }));
        final int th = Prefs.theme(this);
        final String[] themes = {"Automático (segue o telemóvel)", "Claro", "Escuro"};
        pref.addView(Ui.optionRow(this, "Tema", themes[th], new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this).setTitle("Tema").setSingleChoiceItems(themes, th, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        d.dismiss();
                        if (w != th) { Prefs.setTheme(SettingsActivity.this, w); recreate(); }
                    }
                }).show();
            }
        }));
        pref.addView(volumeControl());
        final boolean cr = Prefs.crescendo(this);
        pref.addView(Ui.optionRow(this, "Aumentar o volume gradualmente", cr ? "Sim — começa baixo e sobe em ~20 s" : "Não — toca logo no volume escolhido", new View.OnClickListener() {
            public void onClick(View v) { Prefs.setCrescendo(SettingsActivity.this, !cr); build(); }
        }));
        String snd = "Alarme do sistema";
        try {
            String u = Prefs.alarmSound(this);
            if (u != null) { android.media.Ringtone rt = android.media.RingtoneManager.getRingtone(this, Uri.parse(u)); if (rt != null) snd = rt.getTitle(this); }
        } catch (Exception ignored) { }
        pref.addView(Ui.optionRow(this, "Som do despertador", snd, new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALARM)
                        .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Som do despertador")
                        .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                String cur = Prefs.alarmSound(SettingsActivity.this);
                if (cur != null) i.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(cur));
                startActivityForResult(i, REQ_SOUND);
            }
        }));
        final int tol = Prefs.conflictTolerance(this);
        pref.addView(Ui.optionRow(this, "Avisar lembretes coincidentes", tol < 0 ? "Não avisar" : tol == 0 ? "Quando têm a mesma hora" : "Quando estão a menos de " + tol + " min", new View.OnClickListener() {
            public void onClick(View v) {
                final int[] vals = {0, 5, 15, 30, -1};
                final String[] labels = {"Mesma hora", "Até 5 min de diferença", "Até 15 min de diferença", "Até 30 min de diferença", "Não avisar"};
                int sel = 0;
                for (int i = 0; i < vals.length; i++) if (vals[i] == tol) sel = i;
                new AlertDialog.Builder(SettingsActivity.this).setTitle("Lembretes coincidentes").setSingleChoiceItems(labels, sel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { Prefs.setConflictTolerance(SettingsActivity.this, vals[w]); d.dismiss(); build(); }
                }).show();
            }
        }));
        pref.addView(Ui.optionRow(this, "Som e vibração", "Configurar nas definições do sistema", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            }
        }));
        body.addView(pref);

        // ---- Backup
        LinearLayout bk = Ui.card(this);
        bk.addView(Ui.sectionLabel(this, "Cópia de segurança"));
        bk.addView(Ui.optionRow(this, "Exportar lembretes", "Guarda um ficheiro .json (ex.: no Google Drive)", new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                        .putExtra(Intent.EXTRA_TITLE, "lembra-backup-" + LocalDate.now() + ".json");
                startActivityForResult(i, REQ_EXPORT);
            }
        }));
        bk.addView(Ui.optionRow(this, "Importar lembretes", "Substitui os lembretes atuais pelos do ficheiro", new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                startActivityForResult(i, REQ_IMPORT);
            }
        }));
        bk.addView(Ui.optionRow(this, "Desinstalar o Lembra", "Avisa que os dados serão apagados e propõe uma cópia antes", new View.OnClickListener() {
            public void onClick(View v) { confirmUninstall(); }
        }));
        body.addView(bk);

        String ver = "1.0";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        TextView about = Ui.text(this, "Lembra " + ver + " · Os seus dados ficam apenas neste telemóvel.", 12, R.color.text2, false);
        about.setGravity(Gravity.CENTER);
        about.setPadding(dp(16), dp(16), dp(16), dp(8));
        body.addView(about);
    }

    private boolean uninstallAfterExport;

    // ------------------------------------------------------------------ Volume do despertador
    private android.media.MediaPlayer preview;
    private int previewRestore = -1;
    private final android.os.Handler previewHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable stopPreviewR = new Runnable() { public void run() { stopPreview(); } };

    /** Barra de volume (10–100%) com botão para ouvir uma amostra. */
    private View volumeControl() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(4), dp(12), dp(4), dp(8));
        final TextView title = Ui.text(this, "", 16, R.color.text, false);
        box.addView(title, Ui.lpMW());
        TextView sub = Ui.text(this, "Usado só pelo Lembra; o volume de alarme do sistema volta ao normal quando o despertador pára.", 13, R.color.text2, false);
        box.addView(sub, Ui.lpMW());
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, 0);
        final android.widget.SeekBar bar = new android.widget.SeekBar(this);
        bar.setMax(9); // 10% … 100% em passos de 10
        bar.setProgress(Prefs.alarmVolume(this) / 10 - 1);
        title.setText("Volume do despertador: " + Prefs.alarmVolume(this) + "%");
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean user) {
                int v = (p + 1) * 10;
                title.setText("Volume do despertador: " + v + "%");
                if (user) Prefs.setAlarmVolume(SettingsActivity.this, v);
                if (preview != null) applyPreviewVolume();
            }
            public void onStartTrackingTouch(android.widget.SeekBar s) {}
            public void onStopTrackingTouch(android.widget.SeekBar s) { playPreview(); }
        });
        row.addView(bar, Ui.lpWeight(1));
        TextView listen = Ui.button(this, "Ouvir", false);
        listen.setPadding(dp(18), dp(8), dp(18), dp(8));
        listen.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { playPreview(); } });
        row.addView(listen, new LinearLayout.LayoutParams(-2, -2));
        box.addView(row, Ui.lpMW());
        return box;
    }

    private void applyPreviewVolume() {
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        try {
            int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM);
            if (previewRestore < 0) previewRestore = am.getStreamVolume(android.media.AudioManager.STREAM_ALARM);
            am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, pt.lembra.alarm.AlarmService.alarmStreamLevel(this, max), 0);
        } catch (Exception ignored) { }
    }

    /** Toca 3 s do som do despertador no volume escolhido. */
    private void playPreview() {
        stopPreview();
        applyPreviewVolume();
        android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        java.util.List<Uri> cands = new java.util.ArrayList<Uri>();
        String custom = Prefs.alarmSound(this);
        if (custom != null) cands.add(Uri.parse(custom));
        Uri da = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);
        if (da != null) cands.add(da);
        cands.add(null); // som incluído na app
        for (Uri u : cands) {
            android.media.MediaPlayer mp = new android.media.MediaPlayer();
            try {
                mp.setAudioAttributes(attrs);
                if (u == null) {
                    android.content.res.AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.alarm_tone);
                    mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    afd.close();
                } else mp.setDataSource(this, u);
                mp.setLooping(true);
                mp.prepare();
                mp.start();
                preview = mp;
                break;
            } catch (Exception e) {
                try { mp.release(); } catch (Exception ignored) { }
            }
        }
        if (preview == null) {
            Toast.makeText(this, "Não foi possível tocar o som", Toast.LENGTH_SHORT).show();
            stopPreview();
            return;
        }
        previewHandler.postDelayed(stopPreviewR, 3000);
    }

    private void stopPreview() {
        previewHandler.removeCallbacks(stopPreviewR);
        if (preview != null) { try { preview.stop(); } catch (Exception ignored) { } preview.release(); preview = null; }
        if (previewRestore >= 0) {
            try { ((android.media.AudioManager) getSystemService(AUDIO_SERVICE))
                    .setStreamVolume(android.media.AudioManager.STREAM_ALARM, previewRestore, 0); } catch (Exception ignored) { }
            previewRestore = -1;
        }
    }

    @Override protected void onPause() { stopPreview(); super.onPause(); }

    /** Aviso antes de desinstalar: os dados existem só neste telemóvel e perdem-se. */
    private void confirmUninstall() {
        int n = Db.get(this).all().size();
        String what = n == 0 ? "as suas definições" : n == 1 ? "o seu lembrete e as definições" : "os seus " + n + " lembretes e as definições";
        new AlertDialog.Builder(this)
                .setTitle("Vai perder a informação")
                .setMessage("Ao desinstalar o Lembra, " + what + " serão apagados deste telemóvel e não podem ser recuperados.\n\n"
                        + "Guarde primeiro uma cópia de segurança (por exemplo no Google Drive) para os poder importar mais tarde.")
                .setPositiveButton("Guardar cópia e desinstalar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        uninstallAfterExport = true;
                        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                                .putExtra(Intent.EXTRA_TITLE, "lembra-backup-" + LocalDate.now() + ".json");
                        startActivityForResult(i, REQ_EXPORT);
                    }
                })
                .setNeutralButton("Desinstalar sem cópia", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { uninstall(); }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void uninstall() {
        try {
            startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }

    private View check(String title, boolean ok, String okText, String badText, final Runnable fix) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));
        TextView dot = Ui.text(this, ok ? "✓" : "!", 16, ok ? R.color.onAccent : R.color.onAccent, true);
        dot.setGravity(Gravity.CENTER);
        dot.setBackground(Ui.round(Ui.col(this, ok ? R.color.accent : R.color.danger), dp(14)));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(28), dp(28));
        dlp.setMarginEnd(dp(12));
        row.addView(dot, dlp);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(this, title, 16, R.color.text, false));
        texts.addView(Ui.text(this, ok ? okText : badText, 13, ok ? R.color.text2 : R.color.danger, false));
        row.addView(texts, Ui.lpWeight(1));
        if (!ok) {
            TextView b = Ui.text(this, "CORRIGIR", 13, R.color.accent, true);
            b.setPadding(dp(10), dp(8), dp(4), dp(8));
            row.addView(b);
            row.setBackground(Ui.ripple(this, null, dp(10)));
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                try { fix.run(); } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                }
            } });
        }
        return row;
    }

    private String vendorTip() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        if (m.contains("samsung"))
            return "Dica Samsung: em Definições › Bateria › Limites de utilização em segundo plano, adicione o Lembra a \"Aplicações que nunca entram em suspensão\".";
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco"))
            return "Xiaomi: em \"Abrir permissões Xiaomi\" (acima) ative \"Mostrar no ecrã de bloqueio\", \"Abrir novas janelas em segundo plano\" e \"Janelas pop-up\". Em Definições › Aplicações › Lembra ative também \"Arranque automático\" e escolha \"Sem restrições\" na Poupança de bateria. Sem isto, o despertador pode só vibrar.";
        if (m.contains("huawei") || m.contains("honor"))
            return "Dica Huawei: em Bateria › Arranque de aplicações, desative a gestão automática do Lembra e permita todas as opções.";
        if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus") || m.contains("vivo"))
            return "Dica: nas definições de bateria da aplicação, permita a atividade em segundo plano e o arranque automático do Lembra.";
        return null;
    }

    interface Chosen { void on(int v); }

    private void choose(String title, final int[] vals, int current, final Chosen cb) {
        String[] labels = new String[vals.length];
        int sel = 0;
        for (int i = 0; i < vals.length; i++) {
            labels[i] = vals[i] == 0 ? "Nenhum" : Fmt.duration(vals[i]);
            if (vals[i] == current) sel = i;
        }
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, sel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) { cb.on(vals[which]); d.dismiss(); build(); }
        }).show();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SOUND && res == RESULT_OK && data != null) {
            Uri picked = data.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            Prefs.setAlarmSound(this, picked == null || android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI.equals(picked) ? null : picked.toString());
            build();
            return;
        }
        if (res != RESULT_OK || data == null || data.getData() == null) { uninstallAfterExport = false; return; }
        final Uri uri = data.getData();
        if (req == REQ_EXPORT) {
            try {
                OutputStream os = getContentResolver().openOutputStream(uri, "wt");
                os.write(Db.get(this).exportJson().getBytes("UTF-8"));
                os.close();
                Toast.makeText(this, "Cópia de segurança guardada", Toast.LENGTH_SHORT).show();
                if (uninstallAfterExport) { uninstallAfterExport = false; uninstall(); }
            } catch (Exception e) {
                Toast.makeText(this, "Erro ao exportar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else if (req == REQ_IMPORT) {
            new AlertDialog.Builder(this).setTitle("Importar cópia de segurança?")
                    .setMessage("Os lembretes atuais serão substituídos pelos do ficheiro.")
                    .setPositiveButton("Importar", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { doImport(uri); }
                    }).setNegativeButton("Cancelar", null).show();
        }
    }

    private void doImport(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            for (pt.lembra.core.Reminder r : Db.get(this).all()) Engine.cancelAll(this, r.id);
            int n = Db.get(this).importJson(sb.toString());
            Engine.rescheduleAll(this);
            Toast.makeText(this, n + " lembretes importados", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ficheiro inválido: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
