package pt.lembra.core;

/** Regras da pausa dos lembretes (janela com início e fim). Classe pura, sem Android. */
public final class PauseWindow {
    private PauseWindow() {}

    public static final int NONE = 0, SCHEDULED = 1, ACTIVE = 2;

    /** Atalhos do ecrã de pausa, em horas a contar de agora. */
    public static final int[] HOURS = {1, 2, 4, 8, 12, 24};

    public static long floorMinute(long ms) { return ms - Math.floorMod(ms, 60000L); }

    /** Janela de {@code hours} horas a começar no minuto atual: {início, fim}. */
    public static long[] forHours(long now, int hours) {
        long start = floorMinute(now);
        return new long[]{start, start + hours * 3600000L};
    }

    /** Estado de uma janela guardada. */
    public static int state(long start, long end, long now) {
        if (start <= 0 || end <= start || end <= now) return NONE;
        return now >= start ? ACTIVE : SCHEDULED;
    }

    /** Mensagem de erro, ou null se a janela puder ser guardada. */
    public static String validate(long start, long end, long now) {
        if (end <= start) return "O fim tem de ser depois do início.";
        if (end <= now) return "O fim da pausa já passou.";
        return null;
    }

    /** Ao mudar o início, mantém a duração escolhida. */
    public static long shiftEnd(long oldStart, long oldEnd, long newStart) {
        long dur = oldEnd - oldStart;
        return newStart + (dur > 0 ? dur : 3600000L);
    }

    /** Duração legível da janela, arredondada ao minuto. */
    public static String length(long start, long end) {
        return Fmt.duration(Math.max(0, Math.round((end - start) / 60000.0)));
    }
}
