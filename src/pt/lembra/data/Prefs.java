package pt.lembra.data;

import android.content.Context;
import android.content.SharedPreferences;

/** Definições do utilizador. */
public final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("lembra", Context.MODE_PRIVATE); }

    public static int quickSnooze(Context c) { return sp(c).getInt("quick_snooze", 10); }
    public static void setQuickSnooze(Context c, int v) { sp(c).edit().putInt("quick_snooze", v).apply(); }

    public static int nagInterval(Context c) { return sp(c).getInt("nag_interval", 5); }
    public static void setNagInterval(Context c, int v) { sp(c).edit().putInt("nag_interval", v).apply(); }

    /** Hora por defeito quando só é indicada a data (minutos desde a meia-noite). */
    public static int defaultTime(Context c) { return sp(c).getInt("default_time", 9 * 60); }
    public static void setDefaultTime(Context c, int v) { sp(c).edit().putInt("default_time", v).apply(); }

    public static int defaultPreAlert(Context c) { return sp(c).getInt("default_pre", 0); }
    public static void setDefaultPreAlert(Context c, int v) { sp(c).edit().putInt("default_pre", v).apply(); }

    /** Margem (min) para considerar dois lembretes coincidentes; -1 = não avisar. */
    public static int conflictTolerance(Context c) { return sp(c).getInt("conflict_tol", 0); }
    public static void setConflictTolerance(Context c, int v) { sp(c).edit().putInt("conflict_tol", v).apply(); }

    public static boolean alarmDefault(Context c) { return sp(c).getBoolean("alarm_default", true); }
    public static void setAlarmDefault(Context c, boolean v) { sp(c).edit().putBoolean("alarm_default", v).apply(); }

    /** Duração máxima do toque do despertador (min). */
    public static int ringMinutes(Context c) { return sp(c).getInt("ring_min", 5); }
    public static void setRingMinutes(Context c, int v) { sp(c).edit().putInt("ring_min", v).apply(); }

    /** Som do despertador (URI) ou null para o alarme por defeito do sistema. */
    public static String alarmSound(Context c) { return sp(c).getString("alarm_sound", null); }
    public static void setAlarmSound(Context c, String v) { sp(c).edit().putString("alarm_sound", v).apply(); }

    /** Volume próprio do despertador, em % (10–100), independente do volume de alarme do sistema. */
    public static int alarmVolume(Context c) { return sp(c).getInt("alarm_volume", 80); }
    public static void setAlarmVolume(Context c, int v) { sp(c).edit().putInt("alarm_volume", Math.max(10, Math.min(100, v))).apply(); }

    /** Começar baixo e aumentar até ao volume escolhido. */
    public static boolean crescendo(Context c) { return sp(c).getBoolean("crescendo", true); }
    public static void setCrescendo(Context c, boolean v) { sp(c).edit().putBoolean("crescendo", v).apply(); }

    /** Pausa única dos despertadores, guardada como instantes epoch em milissegundos. */
    public static long quietStart(Context c) { return sp(c).getLong("alarm_quiet_start", 0L); }
    public static long quietEnd(Context c) { return sp(c).getLong("alarm_quiet_end", 0L); }
    public static void setQuietPeriod(Context c, long start, long end) {
        sp(c).edit().putLong("alarm_quiet_start", start).putLong("alarm_quiet_end", end).apply();
    }
    public static void clearQuietPeriod(Context c) {
        sp(c).edit().remove("alarm_quiet_start").remove("alarm_quiet_end").apply();
    }

    /** 0 = automático (segue o sistema), 1 = claro, 2 = escuro. */
    public static int theme(Context c) { return sp(c).getInt("theme", 0); }
    public static void setTheme(Context c, int v) { sp(c).edit().putInt("theme", v).apply(); }

    /** Marcar como feito exige PIN ou impressão digital (cada lembrete pode contrariar). */
    public static boolean doneAuth(Context c) { return sp(c).getBoolean("done_auth", false); }
    public static void setDoneAuth(Context c, boolean v) { sp(c).edit().putBoolean("done_auth", v).apply(); }

    /** Perguntar por um lembrete quando uma chamada termina. */
    public static boolean callPrompt(Context c) { return sp(c).getBoolean("call_prompt", false); }
    public static void setCallPrompt(Context c, boolean v) { sp(c).edit().putBoolean("call_prompt", v).apply(); }

    /** Tipos de chamada que originam a pergunta (bits de CallFollowUp). */
    public static int callPromptMask(Context c) { return sp(c).getInt("call_prompt_mask", pt.lembra.core.CallFollowUp.DEFAULT_MASK); }
    public static void setCallPromptMask(Context c, int v) { sp(c).edit().putInt("call_prompt_mask", v).apply(); }

    /** Estado da chamada em curso: 0 = nenhuma, bit0 = tocou, bit1 = em linha. */
    public static int callState(Context c) { return sp(c).getInt("call_state", 0); }
    public static void setCallState(Context c, int v) { sp(c).edit().putInt("call_state", v).commit(); }

    public static String str(Context c, String key) { return sp(c).getString(key, null); }
    public static void setStr(Context c, String key, String v) { sp(c).edit().putString(key, v).apply(); }

    public static boolean flag(Context c, String key) { return sp(c).getBoolean(key, false); }
    public static void setFlag(Context c, String key) { sp(c).edit().putBoolean(key, true).apply(); }
}
