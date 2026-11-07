package pt.lembra.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.util.ArrayList;
import java.util.List;

import pt.lembra.R;
import pt.lembra.core.Categories;
import pt.lembra.core.Fmt;
import pt.lembra.core.Reminder;
import pt.lembra.data.Db;
import pt.lembra.data.Prefs;
import pt.lembra.ui.AlarmActivity;
import pt.lembra.ui.PendingActivity;

/**
 * Serviço em primeiro plano com dois papéis:
 * <ul>
 *   <li><b>Despertador</b>: toca no canal de ALARME (ouve-se mesmo em silêncio) até o utilizador agir
 *       (Feito ou Adiar). Abre o ecrã do alarme com o telemóvel bloqueado ou desbloqueado e volta a abri-lo
 *       sempre que o ecrã acende ou o telemóvel é desbloqueado.</li>
 *   <li><b>Vigilante de pendentes</b>: enquanto houver lembretes notificados por tratar, ao desbloquear o
 *       telemóvel abre o ecrã "Por tratar", para nada ficar esquecido nas notificações.</li>
 * </ul>
 */
public class AlarmService extends Service {
    static final String CH_ALARM = "despertador", CH_PENDING = "pendentes";
    static final int NOTIF_ID = 777_000;
    public static final long TEST_ID = -42;
    static final long NONE = Long.MIN_VALUE;
    static final String ACT_RING = "ring", ACT_WATCH = "watch", ACT_REPOST = "repost";

    private static AlarmService instance;
    private long currentId = NONE;
    private boolean paused;
    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wake;
    private int restoreVolume = -1;
    private float vol;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean receiverOn;

    // ------------------------------------------------------------------ API estática

    /** Começa a tocar para um lembrete (ou TEST_ID). */
    public static void start(Context ctx, long id) {
        Intent i = new Intent(ctx, AlarmService.class).setAction(ACT_RING).putExtra("id", id);
        try {
            ctx.startForegroundService(i);
        } catch (Exception e) {
            diag(ctx, "Não foi possível abrir o despertador (" + e.getClass().getSimpleName() + "); tocou pela notificação de reserva.");
            Reminder r = Db.get(ctx).get(id);
            if (r != null) Notifier.showMain(ctx, r, false);
        }
    }

    /** Pára o toque (ação do utilizador: Feito, Adiar…). id == null pára qualquer toque. */
    public static void stop(Long id) {
        AlarmService s = instance;
        if (s != null && s.currentId != NONE && (id == null || s.currentId == id)) s.stopRinging();
    }

    /** Silencia temporariamente enquanto o utilizador escolhe a nova data (o alarme continua ativo). */
    public static void pause() { AlarmService s = instance; if (s != null && s.currentId != NONE) s.pausePlayback(); }

    /** Retoma o toque se o utilizador cancelou a escolha da nova data. */
    public static void resume() { AlarmService s = instance; if (s != null && s.currentId != NONE && s.paused) s.resumePlayback(); }

    public static boolean isRinging(long id) { AlarmService s = instance; return s != null && s.currentId == id; }

    /** Chamado após cada alteração: liga/desliga o vigilante de pendentes. */
    public static void sync(Context ctx) {
        AlarmService s = instance;
        if (s != null && s.currentId != NONE) return; // a tocar: nada a fazer
        int pending = pending(ctx).size();
        if (pending > 0) {
            try {
                ctx.startForegroundService(new Intent(ctx, AlarmService.class).setAction(ACT_WATCH));
            } catch (Exception ignored) { /* sem autorização para arrancar agora; tenta na próxima alteração */ }
        } else if (s != null) {
            s.stopForeground(STOP_FOREGROUND_REMOVE);
            s.stopSelf();
        }
    }

    /** Lembretes já notificados e ainda por tratar. */
    public static List<Reminder> pending(Context ctx) {
        List<Reminder> out = new ArrayList<Reminder>();
        for (Reminder r : Db.get(ctx).all()) if (!r.done && r.notified) out.add(r);
        return out;
    }

    // ------------------------------------------------------------------ Ciclo de vida

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, f);
        receiverOn = true;
    }

    /** Ecrã ligado / telemóvel desbloqueado. */
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (currentId != NONE) {
                if (!paused) openAlarmScreen();
            } else if (Intent.ACTION_USER_PRESENT.equals(i.getAction()) && !pending(c).isEmpty()) {
                openActivity(new Intent(c, PendingActivity.class));
            }
        }
    };

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String act = intent == null ? ACT_WATCH : intent.getAction();
        if (ACT_RING.equals(act)) {
            long id = intent.getLongExtra("id", NONE);
            Reminder r = id == TEST_ID ? testReminder() : Db.get(this).get(id);
            if (r == null || (id != TEST_ID && r.done)) {
                foregroundWatcherOrStop();
                return START_NOT_STICKY;
            }
            if (id == currentId) {
                // Já está a tocar (p.ex. repetição do modo insistente): não reinicia o som.
                foreground(buildAlarmNotification(r, id), true);
                if (id != TEST_ID) Notifier.showWatchAlarm(this, r);
                if (!paused) openAlarmScreen();
                return START_STICKY;
            }
            if (currentId != NONE && currentId != TEST_ID) {
                // Outro lembrete estava a tocar: fica pendente (notificação + vigilante).
                Reminder prev = Db.get(this).get(currentId);
                if (prev != null && !prev.done) Notifier.showMain(this, prev, false);
                Notifier.cancelWatchAlarm(this, currentId);
            }
            releasePlayback();
            currentId = id;
            paused = false;
            foreground(buildAlarmNotification(r, id), true);
            if (id != TEST_ID) Notifier.showWatchAlarm(this, r);
            startPlayback();
            openAlarmScreen();
            return START_STICKY;
        }
        if (ACT_REPOST.equals(act)) {
            // O utilizador deslizou a notificação do alarme: volta a mostrá-la (o alarme só pára com uma ação).
            if (currentId != NONE) {
                Reminder r = currentId == TEST_ID ? testReminder() : Db.get(this).get(currentId);
                if (r != null) {
                    foreground(buildAlarmNotification(r, currentId), true);
                    if (currentId != TEST_ID) Notifier.showWatchAlarm(this, r);
                }
            } else foregroundWatcherOrStop();
            return START_STICKY;
        }
        // ACT_WATCH
        if (currentId == NONE) foregroundWatcherOrStop();
        return START_STICKY;
    }

    private void foreground(Notification n, boolean ringing) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ringing ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    : ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else startForeground(NOTIF_ID, n);
    }

    private void foregroundWatcherOrStop() {
        List<Reminder> p = pending(this);
        if (p.isEmpty()) {
            // startForeground é obrigatório depois de startForegroundService, mesmo que vá parar logo.
            foreground(buildWatcherNotification(p), false);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }
        foreground(buildWatcherNotification(p), false);
    }

    @Override public void onDestroy() {
        releasePlayback();
        handler.removeCallbacksAndMessages(null);
        if (receiverOn) { try { unregisterReceiver(screen); } catch (Exception ignored) { } receiverOn = false; }
        if (instance == this) instance = null;
        super.onDestroy();
    }

    // ------------------------------------------------------------------ Ecrãs

    private void openAlarmScreen() {
        openActivity(new Intent(this, AlarmActivity.class).putExtra("id", currentId));
    }

    /** Abre um ecrã a partir do serviço (requer "Mostrar sobre outras apps" com o telemóvel desbloqueado). */
    private void openActivity(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Exception ignored) { /* o full-screen intent da notificação continua a funcionar */ }
    }

    // ------------------------------------------------------------------ Notificações

    static Reminder testReminder() {
        Reminder t = new Reminder();
        t.id = TEST_ID;
        t.title = "Teste do despertador";
        t.notes = "Se ouviu o som com o telemóvel em silêncio, está tudo a funcionar.";
        t.dueAt = System.currentTimeMillis();
        return t;
    }

    private void channels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CH_ALARM, "Despertador", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Lembretes que tocam como despertador");
        ch.setSound(null, null); // o som é tocado pelo serviço no canal de alarme
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
        NotificationChannel pc = new NotificationChannel(CH_PENDING, "Lembretes por tratar", NotificationManager.IMPORTANCE_LOW);
        pc.setDescription("Resumo dos lembretes que ainda não marcou como feitos nem adiou");
        nm.createNotificationChannel(pc);
    }

    private PendingIntent repostIntent() {
        return PendingIntent.getService(this, 777_002, new Intent(this, AlarmService.class).setAction(ACT_REPOST),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification buildAlarmNotification(Reminder r, long id) {
        channels();
        Intent full = new Intent(this, AlarmActivity.class).putExtra("id", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent fullPi = PendingIntent.getActivity(this, 777_001, full, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(this, CH_ALARM)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(Categories.color(r.category))
                .setContentTitle("⏰ " + r.title)
                .setContentText(Fmt.time(r.effectiveTime()) + (r.notes != null && !r.notes.isEmpty() ? " · " + r.notes : ""))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setFullScreenIntent(fullPi, true)
                .setContentIntent(fullPi)
                .setDeleteIntent(repostIntent());
        if (id != TEST_ID) {
            int quick = Prefs.quickSnooze(this);
            b.addAction(new Notification.Action.Builder(null, "Feito", Notifier.action(this, Engine.A_DONE, id, 0, 0)).build());
            b.addAction(new Notification.Action.Builder(null, "+" + Fmt.duration(quick), Notifier.action(this, Engine.A_SNOOZE, id, 1, quick)).build());
        } else {
            b.addAction(new Notification.Action.Builder(null, "Parar", Notifier.action(this, Engine.A_STOP, id, 2, 0)).build());
        }
        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT | Notification.FLAG_NO_CLEAR;
        return n;
    }

    private Notification buildWatcherNotification(List<Reminder> p) {
        channels();
        PendingIntent open = PendingIntent.getActivity(this, 777_003,
                new Intent(this, PendingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String title = p.size() == 1 ? "1 lembrete por tratar" : p.size() + " lembretes por tratar";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, p.size()); i++) { if (i > 0) sb.append(" · "); sb.append(p.get(i).title); }
        return new Notification.Builder(this, CH_PENDING)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(p.isEmpty() ? "" : sb.toString())
                .setContentIntent(open)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    // ------------------------------------------------------------------ Som

    private void startPlayback() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (wake == null) {
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lembra:alarm");
            wake.setReferenceCounted(false);
        }
        wake.acquire(60 * 60000L); // renovado ao retomar; o alarme não tem limite de tempo

        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        try {
            int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int cur = am.getStreamVolume(AudioManager.STREAM_ALARM);
            // Volume próprio do Lembra: aplica-o ao canal de alarme enquanto toca e repõe o do sistema no fim.
            int target = alarmStreamLevel(this, max);
            if (cur != target) { if (restoreVolume < 0) restoreVolume = cur; am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0); }
        } catch (Exception ignored) { }

        AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        // Cadeia de reserva: som escolhido → alarme do sistema → toque do sistema → som incluído na app.
        List<Uri> candidates = new ArrayList<Uri>();
        String custom = Prefs.alarmSound(this);
        if (custom != null) candidates.add(Uri.parse(custom));
        try { Uri a = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM); if (a != null) candidates.add(a); } catch (Exception ignored) { }
        Uri da = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (da != null) candidates.add(da);
        candidates.add(Notifier.bundledTone(this));
        String used = null, errors = "";
        for (Uri u : candidates) {
            MediaPlayer mp = new MediaPlayer();
            try {
                mp.setAudioAttributes(attrs);
                if (u.getScheme() != null && u.getScheme().equals("android.resource")) {
                    android.content.res.AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.alarm_tone);
                    mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    afd.close();
                } else {
                    mp.setDataSource(this, u);
                }
                mp.setLooping(true);
                vol = Prefs.crescendo(this) ? 0.25f : 1f;
                mp.setVolume(vol, vol);
                mp.prepare();
                mp.start();
                player = mp;
                used = u.toString();
                break;
            } catch (Exception e) {
                errors += e.getClass().getSimpleName() + " ";
                try { mp.release(); } catch (Exception ignored) { }
            }
        }
        if (player != null) handler.post(crescendo);
        AudioManager am2 = (AudioManager) getSystemService(AUDIO_SERVICE);
        int av = am2.getStreamVolume(AudioManager.STREAM_ALARM), amax = am2.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        diag(this, player == null ? "Falhou a reproduzir som (" + errors.trim() + "). Volume de alarme " + av + "/" + amax + "."
                : "Último alarme tocou (" + (used.contains("android.resource") ? "som da app" : "som do sistema") + ", volume de alarme " + av + "/" + amax + ")"
                + (errors.isEmpty() ? "." : "; falhas antes: " + errors.trim() + "."));

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 700, 500}, 0), attrs);
        }
        handler.removeCallbacks(keepAwake);
        handler.postDelayed(keepAwake, 50 * 60000L);
    }

    /** Renova o wake lock enquanto o alarme tocar. */
    private final Runnable keepAwake = new Runnable() {
        public void run() {
            if (currentId == NONE || wake == null) return;
            wake.acquire(60 * 60000L);
            handler.postDelayed(this, 50 * 60000L);
        }
    };

    /** Volume crescente durante ~20 s. */
    private final Runnable crescendo = new Runnable() {
        public void run() {
            if (player == null) return;
            vol = Math.min(1f, vol + 0.085f);
            try { player.setVolume(vol, vol); } catch (Exception ignored) { }
            if (vol < 1f) handler.postDelayed(this, 2000);
        }
    };

    /** Nível do canal de alarme correspondente ao volume escolhido no Lembra (mínimo 1). */
    public static int alarmStreamLevel(Context c, int max) {
        return Math.max(1, Math.round(max * Prefs.alarmVolume(c) / 100f));
    }

    static void diag(Context c, String msg) {
        Prefs.setStr(c, "alarm_diag", Fmt.dayTime(System.currentTimeMillis(), System.currentTimeMillis()) + " — " + msg);
    }

    private void pausePlayback() {
        paused = true;
        handler.removeCallbacks(crescendo);
        if (player != null) { try { player.stop(); } catch (Exception ignored) { } player.release(); player = null; }
        if (vibrator != null) { vibrator.cancel(); vibrator = null; }
    }

    private void resumePlayback() {
        paused = false;
        startPlayback();
        openAlarmScreen();
    }

    private void releasePlayback() {
        pausePlayback();
        paused = false;
        handler.removeCallbacks(keepAwake);
        if (restoreVolume >= 0) {
            try { ((AudioManager) getSystemService(AUDIO_SERVICE)).setStreamVolume(AudioManager.STREAM_ALARM, restoreVolume, 0); } catch (Exception ignored) { }
            restoreVolume = -1;
        }
        if (wake != null && wake.isHeld()) wake.release();
    }

    private void stopRinging() {
        if (currentId != NONE && currentId != TEST_ID) Notifier.cancelWatchAlarm(this, currentId);
        releasePlayback();
        currentId = NONE;
        AlarmActivity.closeAll();
        foregroundWatcherOrStop();
    }
}
