package pt.lembra.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Formatação de datas em português (pt-PT), independente do idioma do sistema. */
public final class Fmt {
    private Fmt() {}

    public static final String[] MONTHS = {"janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho",
            "agosto", "setembro", "outubro", "novembro", "dezembro"};
    public static final String[] MONTHS_SHORT = {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set",
            "out", "nov", "dez"};
    public static final String[] DAYS = {"segunda-feira", "terça-feira", "quarta-feira", "quinta-feira",
            "sexta-feira", "sábado", "domingo"};
    public static final String[] DAYS_SHORT = {"seg", "ter", "qua", "qui", "sex", "sáb", "dom"};

    public static String time(long ms) {
        LocalDateTime t = Recurrence.ldt(ms);
        return pad(t.getHour()) + ":" + pad(t.getMinute());
    }

    static String pad(int v) { return v < 10 ? "0" + v : String.valueOf(v); }

    public static String cap(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** "Hoje", "Amanhã", "Ontem", "sex, 6 nov" ou "6 nov 2027". */
    public static String day(long ms, long now) {
        LocalDate d = Recurrence.ldt(ms).toLocalDate();
        LocalDate today = Recurrence.ldt(now).toLocalDate();
        long diff = ChronoUnit.DAYS.between(today, d);
        if (diff == 0) return "Hoje";
        if (diff == 1) return "Amanhã";
        if (diff == -1) return "Ontem";
        String base = DAYS_SHORT[d.getDayOfWeek().getValue() - 1] + ", " + d.getDayOfMonth() + " " + MONTHS_SHORT[d.getMonthValue() - 1];
        if (d.getYear() != today.getYear()) base += " " + d.getYear();
        return base;
    }

    public static String dayTime(long ms, long now) { return day(ms, now) + ", " + time(ms); }

    public static String dateShort(long ms) {
        LocalDate d = Recurrence.ldt(ms).toLocalDate();
        return d.getDayOfMonth() + " " + MONTHS_SHORT[d.getMonthValue() - 1] + " " + d.getYear();
    }

    public static String dateLong(long ms) {
        LocalDate d = Recurrence.ldt(ms).toLocalDate();
        return cap(DAYS[d.getDayOfWeek().getValue() - 1]) + ", " + d.getDayOfMonth() + " de " + MONTHS[d.getMonthValue() - 1];
    }

    /** Duração legível: "5 min", "1 h", "2 h 30 min", "1 dia". */
    public static String duration(long minutes) {
        if (minutes < 60) return minutes + " min";
        if (minutes % 1440 == 0) { long d = minutes / 1440; return d + (d == 1 ? " dia" : " dias"); }
        long h = minutes / 60, m = minutes % 60;
        return h + " h" + (m > 0 ? " " + m + " min" : "");
    }

    /** "daqui a 5 min", "há 2 h" etc. */
    public static String relative(long ms, long now) {
        long diffMin = Math.round((ms - now) / 60000.0);
        if (Math.abs(diffMin) < 1) return "agora";
        if (diffMin > 0) return "daqui a " + duration(diffMin);
        return "há " + duration(-diffMin);
    }
}
