package pt.lembra.core;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/** Cálculo de ocorrências de lembretes recorrentes e respetiva descrição em português. */
public final class Recurrence {
    private Recurrence() {}

    public static ZoneId zone = ZoneId.systemDefault();

    static LocalDateTime ldt(long ms) { return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone); }
    static long ms(LocalDateTime t) { return t.atZone(zone).toInstant().toEpochMilli(); }

    /** Ocorrência seguinte estritamente depois de {@code from} (que deve ser uma ocorrência válida),
     *  sem considerar o fim da recorrência. -1 se não for recorrente. */
    public static long rawNext(Reminder r, long from) {
        LocalDateTime t = ldt(from);
        int n = Math.max(1, r.interval);
        switch (r.repeatType) {
            case Reminder.REPEAT_DAILY:
                return ms(t.plusDays(1));
            case Reminder.REPEAT_WEEKDAYS: {
                LocalDateTime c = t.plusDays(1);
                while (c.getDayOfWeek() == DayOfWeek.SATURDAY || c.getDayOfWeek() == DayOfWeek.SUNDAY) c = c.plusDays(1);
                return ms(c);
            }
            case Reminder.REPEAT_WEEKLY: {
                int mask = r.weekMask != 0 ? r.weekMask : bit(t.getDayOfWeek());
                LocalDate weekStart = t.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDateTime c = t.plusDays(1);
                for (int i = 0; i < 7 * n + 8; i++, c = c.plusDays(1)) {
                    if ((mask & bit(c.getDayOfWeek())) == 0) continue;
                    LocalDate ws = c.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    long weeks = ChronoUnit.WEEKS.between(weekStart, ws);
                    if (weeks % n == 0) return ms(c);
                }
                return ms(t.plusWeeks(n));
            }
            case Reminder.REPEAT_MONTHLY: {
                int day = r.monthDay > 0 ? r.monthDay : t.getDayOfMonth();
                LocalDateTime c = t.withDayOfMonth(1).plusMonths(1);
                int len = c.toLocalDate().lengthOfMonth();
                return ms(c.withDayOfMonth(Math.min(day, len)));
            }
            case Reminder.REPEAT_YEARLY:
                return ms(t.plusYears(1));
            case Reminder.REPEAT_CUSTOM:
                switch (r.intervalUnit) {
                    case Reminder.UNIT_HOUR: return ms(t.plusHours(n));
                    case Reminder.UNIT_WEEK: return ms(t.plusWeeks(n));
                    case Reminder.UNIT_MONTH: {
                        int day = r.monthDay > 0 ? r.monthDay : t.getDayOfMonth();
                        LocalDateTime c = t.withDayOfMonth(1).plusMonths(n);
                        return ms(c.withDayOfMonth(Math.min(day, c.toLocalDate().lengthOfMonth())));
                    }
                    default: return ms(t.plusDays(n));
                }
            default:
                return -1;
        }
    }

    /** Ocorrência a seguir à atual (índice count+1) respeitando o fim; -1 se terminou. */
    public static long next(Reminder r, long from, int countAfter) {
        long n = rawNext(r, from);
        if (n < 0) return -1;
        if (r.endType == Reminder.END_COUNT && r.endCount > 0 && countAfter >= r.endCount) return -1;
        if (r.endType == Reminder.END_DATE && r.endDate > 0) {
            long limit = ms(ldt(r.endDate).toLocalDate().atTime(23, 59, 59));
            if (n > limit) return -1;
        }
        return n;
    }

    /**
     * Avança o lembrete para a próxima ocorrência posterior a {@code now}.
     * Devolve false se a recorrência terminou (o lembrete deve ser marcado como feito).
     */
    public static boolean advance(Reminder r, long now) {
        long cur = r.dueAt;
        int c = r.count;
        for (int guard = 0; guard < 100000; guard++) {
            long n = next(r, cur, c + 1);
            if (n < 0) return false;
            c++;
            cur = n;
            if (cur > now) break;
        }
        r.dueAt = cur;
        r.count = c;
        r.snoozedUntil = 0;
        r.resetAlertState();
        return true;
    }

    /** Primeira ocorrência válida >= start (útil para alinhar recorrências semanais com dias escolhidos). */
    public static long alignFirst(Reminder r, long start) {
        if (r.repeatType == Reminder.REPEAT_WEEKLY && r.weekMask != 0) {
            LocalDateTime t = ldt(start);
            for (int i = 0; i < 8; i++) {
                if ((r.weekMask & bit(t.getDayOfWeek())) != 0) return ms(t);
                t = t.plusDays(1);
            }
        }
        if (r.repeatType == Reminder.REPEAT_WEEKDAYS) {
            LocalDateTime t = ldt(start);
            while (t.getDayOfWeek() == DayOfWeek.SATURDAY || t.getDayOfWeek() == DayOfWeek.SUNDAY) t = t.plusDays(1);
            return ms(t);
        }
        return start;
    }

    public static int bit(DayOfWeek d) { return 1 << (d.getValue() - 1); }

    static final String[] DAYS_SHORT = {"seg", "ter", "qua", "qui", "sex", "sáb", "dom"};

    public static String unitName(int unit, int n) {
        switch (unit) {
            case Reminder.UNIT_HOUR: return n == 1 ? "hora" : "horas";
            case Reminder.UNIT_WEEK: return n == 1 ? "semana" : "semanas";
            case Reminder.UNIT_MONTH: return n == 1 ? "mês" : "meses";
            default: return n == 1 ? "dia" : "dias";
        }
    }

    public static String describe(Reminder r) {
        switch (r.repeatType) {
            case Reminder.REPEAT_DAILY: return "Todos os dias";
            case Reminder.REPEAT_WEEKDAYS: return "Dias úteis";
            case Reminder.REPEAT_WEEKLY: {
                int mask = r.weekMask;
                if (mask == 0) return "Todas as semanas";
                if (mask == 0x7F) return "Todos os dias";
                if (mask == 0x1F) return "Dias úteis";
                if (mask == 0x60) return "Fins de semana";
                StringBuilder sb = new StringBuilder("Semanal: ");
                boolean first = true;
                for (int i = 0; i < 7; i++) if ((mask & (1 << i)) != 0) {
                    if (!first) sb.append(", ");
                    sb.append(DAYS_SHORT[i]);
                    first = false;
                }
                return sb.toString();
            }
            case Reminder.REPEAT_MONTHLY: {
                int d = r.monthDay > 0 ? r.monthDay : ldt(r.dueAt).getDayOfMonth();
                return "Todos os meses (dia " + d + ")";
            }
            case Reminder.REPEAT_YEARLY: return "Todos os anos";
            case Reminder.REPEAT_CUSTOM: {
                int n = Math.max(1, r.interval);
                if (n == 1) {
                    switch (r.intervalUnit) {
                        case Reminder.UNIT_HOUR: return "De hora a hora";
                        case Reminder.UNIT_WEEK: return "Todas as semanas";
                        case Reminder.UNIT_MONTH: return "Todos os meses";
                        default: return "Todos os dias";
                    }
                }
                return "A cada " + n + " " + unitName(r.intervalUnit, n);
            }
            default: return "";
        }
    }

    public static String describeEnd(Reminder r) {
        if (r.endType == Reminder.END_COUNT && r.endCount > 0)
            return "termina após " + r.endCount + (r.endCount == 1 ? " vez" : " vezes");
        if (r.endType == Reminder.END_DATE && r.endDate > 0)
            return "até " + Fmt.dateShort(r.endDate);
        return "";
    }
}
