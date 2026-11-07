package pt.lembra.core;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta frases em português para criar lembretes, p.ex.:
 * "Ligar ao Tiago amanhã às 15h30", "Pagar renda dia 8 todos os meses às 10h",
 * "Beber água a cada 2 horas", "Reunião na próxima sexta às 9", "Tirar pão do forno daqui a 20 min".
 */
public final class QuickParser {

    public static final class Result {
        public String title = "";
        /** null quando não foi indicada nenhuma data/hora. */
        public Long dueAt;
        public int repeatType = Reminder.REPEAT_NONE;
        public int interval = 1;
        public int intervalUnit = Reminder.UNIT_DAY;
        public int weekMask;
        public int monthDay;
        public int category = -1;
        public boolean important;
        public boolean hasTime;

        public Reminder toReminder() {
            Reminder r = new Reminder();
            r.title = title;
            if (dueAt != null) r.dueAt = dueAt;
            r.repeatType = repeatType; r.interval = interval; r.intervalUnit = intervalUnit;
            r.weekMask = weekMask; r.monthDay = monthDay;
            if (category >= 0) r.category = category;
            r.important = important;
            return r;
        }

        /** Resumo para pré-visualização: "Amanhã, 15:30 · Todos os meses (dia 8)". */
        public String summary(long now) {
            StringBuilder sb = new StringBuilder();
            if (dueAt != null) sb.append(Fmt.dayTime(dueAt, now));
            if (repeatType != Reminder.REPEAT_NONE) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(Recurrence.describe(toReminder()));
            }
            if (category >= 0) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(Categories.name(category));
            }
            if (important) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append("Importante");
            }
            return sb.toString();
        }
    }

    private static final String B = "(?<![\\p{L}\\d])";
    private static final String E = "(?![\\p{L}\\d])";
    private static final String NUM = "(\\d+|um|uma|dois|duas|tr[êe]s|quatro|cinco|seis|sete|oito|nove|dez|onze|doze|quinze|vinte|trinta|quarenta|meia)";
    private static final String WD = "(segunda|ter[çc]a|quarta|quinta|sexta|s[áa]bado|domingo)s?(?:[- ]feiras?)?";
    private static final String MONTHS = "(janeiro|fevereiro|mar[çc]o|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)";
    private static final String PART = "(?:\\s(da manh[ãa]|da tarde|da noite|da madrugada))?";

    private static Pattern p(String re) {
        return Pattern.compile(re, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static final Pattern P_IMPORTANT = p("!+|" + B + "(?:importante|urgente)" + E);
    private static final Pattern P_CATEGORY = p("#([\\p{L}]+)");
    private static final Pattern P_REL = p(B + "(?:daqui a|dentro de|em)\\s" + NUM + "\\s?(minutos?|mins?|horas?|h|dias?|semanas?|m[eê]s(?:es)?|meses)" + E);
    private static final Pattern P_REL_HALF = p(B + "(?:daqui a|dentro de|em)\\s(?:meia hora|uma hora e meia)" + E);
    private static final Pattern P_EVERY_N = p(B + "(?:a cada|cada)\\s" + NUM + "\\s(horas?|dias?|semanas?|m[eê]s(?:es)?|meses)" + E);
    private static final Pattern P_EVERY_N2 = p(B + "de\\s(\\d+)\\sem\\s\\1\\s(horas|dias|semanas|meses)" + E);
    private static final Pattern P_EVERY_1 = p(B + "(?:a cada|de)\\s(hora)\\sa\\shora" + E + "|" + B + "a cada\\s(hora|dia|semana|m[eê]s|ano)" + E);
    private static final Pattern P_WEEKDAYS = p(B + "(?:todos os dias [úu]teis|aos dias [úu]teis|nos dias [úu]teis|dias [úu]teis|de segunda a sexta)" + E);
    private static final Pattern P_WEEKENDS = p(B + "(?:todos os fins de semana|aos fins de semana|nos fins de semana)" + E);
    private static final Pattern P_MONTHLY_DAY = p(B + "(?:(?:todos os meses|mensalmente),?\\s(?:no\\s|ao\\s|a\\s)?dia\\s(\\d{1,2})|(?:no\\s|ao\\s|a\\s)?dia\\s(\\d{1,2})\\s(?:de cada m[eê]s|de todos os meses|todos os meses)|todos os dias\\s(\\d{1,2}))" + E);
    private static final Pattern P_DAILY = p(B + "(?:todos os dias|diariamente|cada dia|todas as manh[ãa]s|todas as noites|todas as tardes)" + E);
    private static final Pattern P_WEEKLY_DAYS = p(B + "(?:todas as|todos os|às|aos|as|nas|nos)\\s" + WD + "((?:\\s?(?:,|e)\\s?(?:às\\s|aos\\s|as\\s)?" + WD + ")*)" + E);
    private static final Pattern P_WEEKLY = p(B + "(?:todas as semanas|semanalmente|cada semana)" + E);
    private static final Pattern P_MONTHLY = p(B + "(?:todos os meses|mensalmente|cada m[eê]s)" + E);
    private static final Pattern P_YEARLY = p(B + "(?:todos os anos|anualmente|cada ano)" + E);

    private static final Pattern P_AFTER_TOMORROW = p(B + "depois de amanh[ãa]" + E);
    private static final Pattern P_TOMORROW = p(B + "amanh[ãa]" + E);
    private static final Pattern P_TODAY = p(B + "(?:hoje|esta noite|logo à noite|esta tarde)" + E);
    private static final Pattern P_NEXT_WEEK = p(B + "(?:(?:para a|na)\\spr[óo]xima semana|para a semana|na semana que vem)" + E);
    private static final Pattern P_NEXT_MONTH = p(B + "(?:no\\spr[óo]ximo m[êe]s|no m[êe]s que vem|para o m[êe]s)" + E);
    private static final Pattern P_DMY = p(B + "(?:(?:no\\s|a\\s|em\\s|para\\s)?dia\\s)?(\\d{1,2})[/.-](\\d{1,2})(?:[/.-](\\d{2,4}))?" + E);
    private static final Pattern P_D_MONTH = p(B + "(?:(?:no\\s|a\\s|em\\s|para\\s)?dia\\s)?(\\d{1,2})\\sde\\s" + MONTHS + "(?:\\sde\\s(\\d{4}))?" + E);
    private static final Pattern P_DAY = p(B + "(?:no\\s|a\\s|em\\s|ao\\s|para\\s|para o\\s)?dia\\s(\\d{1,2})" + E);
    private static final Pattern P_WEEKDAY = p(B + "(?:(?:na|no|nesta|neste|esta|este|para|para a|para o)\\s)?(?:(pr[óo]xim[ao])\\s)?" + WD + "(\\sque vem)?" + E);

    private static final Pattern P_TIME_HM = p(B + "(?:(?:às|as|pelas|ao|à|a)\\s)?(\\d{1,2})\\s?(?:h|:)\\s?(\\d{2})(?:\\s?(?:m|min))?" + PART + E);
    private static final Pattern P_TIME_AT = p(B + "(?:às|as|pelas)\\s(\\d{1,2})(?:\\s?(?:h|horas))?(?:\\se\\s(meia|um quarto|quinze))?" + PART + E);
    private static final Pattern P_TIME_H = p(B + "(\\d{1,2})\\s?(?:h|horas)(?:\\se\\s(meia|um quarto|quinze))?" + PART + E);
    private static final Pattern P_NOON = p(B + "(?:ao\\s|à\\s)?meio[- ]dia(\\se meia)?" + E);
    private static final Pattern P_MIDNIGHT = p(B + "(?:à\\s)?meia[- ]noite" + E);
    private static final Pattern P_PART = p(B + "(?:de|pela|à|a|ao|da|na)\\s(manh[ãa]|tarde|noite|fim do dia|fim da tarde|hora de almo[çc]o|hora do almo[çc]o|almo[çc]o|jantar)" + E);

    private static final Pattern P_PREFIX = p("^\\s*(?:lembra[- ]me\\s(?:de|para)|lembrar[- ]me\\s(?:de|para)|lembrar\\s(?:de|para)|lembrete:?|n[ãa]o esquecer\\s(?:de)?|não te esqueças de)\\s*");
    private static final java.util.Set<String> TRAIL = new java.util.HashSet<String>(java.util.Arrays.asList(
            "às", "as", "a", "ao", "à", "de", "do", "da", "em", "no", "na", "para", "pelas", "e", ",", "-", ";", "que", "o"));
    private static final java.util.Set<String> LEAD = new java.util.HashSet<String>(java.util.Arrays.asList(",", "-", ";", "e", "às", "para"));

    private String s;     // texto original (com espaços em vez das partes reconhecidas)
    private String low;   // versão minúscula, mesmo comprimento

    private QuickParser(String in) {
        s = in;
        low = in.toLowerCase(new Locale("pt", "PT"));
        if (low.length() != s.length()) low = s; // salvaguarda (não deve acontecer em português)
    }

    private Matcher find(Pattern pat) {
        Matcher m = pat.matcher(low);
        return m.find() ? m : null;
    }

    private void consume(Matcher m) {
        int a = m.start(), b = m.end();
        StringBuilder sp = new StringBuilder();
        for (int i = a; i < b; i++) sp.append(' ');
        s = s.substring(0, a) + sp + s.substring(b);
        low = low.substring(0, a) + sp + low.substring(b);
    }

    static int num(String w) {
        if (w == null) return 1;
        w = w.toLowerCase(Locale.ROOT);
        if (w.matches("\\d+")) {
            try { return Integer.parseInt(w); } catch (NumberFormatException e) { return 1; }
        }
        switch (w) {
            case "um": case "uma": return 1;
            case "dois": case "duas": return 2;
            case "três": case "tres": return 3;
            case "quatro": return 4; case "cinco": return 5; case "seis": return 6; case "sete": return 7;
            case "oito": return 8; case "nove": return 9; case "dez": return 10; case "onze": return 11;
            case "doze": return 12; case "quinze": return 15; case "vinte": return 20; case "trinta": return 30;
            case "quarenta": return 40;
            default: return 1;
        }
    }

    static int unitOf(String u) {
        u = u.toLowerCase(Locale.ROOT);
        if (u.startsWith("h")) return Reminder.UNIT_HOUR;
        if (u.startsWith("sem")) return Reminder.UNIT_WEEK;
        if (u.startsWith("m")) return Reminder.UNIT_MONTH;
        return Reminder.UNIT_DAY;
    }

    static int weekdayIndex(String w) {
        w = w.toLowerCase(Locale.ROOT);
        if (w.startsWith("seg")) return 0;
        if (w.startsWith("ter")) return 1;
        if (w.startsWith("quar")) return 2;
        if (w.startsWith("quin")) return 3;
        if (w.startsWith("sex")) return 4;
        if (w.startsWith("s")) return 5;
        return 6;
    }

    static int monthIndex(String m) {
        m = m.toLowerCase(Locale.ROOT).replace('ç', 'c');
        for (int i = 0; i < 12; i++) if (Fmt.MONTHS[i].replace('ç', 'c').equals(m)) return i + 1;
        return 1;
    }

    static int applyPart(int h, String part) {
        if (part == null) return h;
        part = part.toLowerCase(Locale.ROOT);
        if ((part.contains("tarde") || part.contains("noite")) && h < 12) return h + 12;
        if (part.contains("manh") && h == 12) return 0;
        return h;
    }

    static int minutesWord(String w) {
        if (w == null) return 0;
        w = w.toLowerCase(Locale.ROOT);
        if (w.startsWith("meia")) return 30;
        return 15;
    }

    public static Result parse(String input, long nowMs) { return parse(input, nowMs, 9, 0); }

    public static Result parse(String input, long nowMs, int defHour, int defMinute) {
        return new QuickParser(" " + input + " ").run(nowMs, defHour, defMinute);
    }

    private Result run(long nowMs, int defHour, int defMinute) {
        Result r = new Result();
        LocalDateTime now = Recurrence.ldt(nowMs).withSecond(0).withNano(0);
        Matcher m;

        // Importância e categoria
        while ((m = find(P_IMPORTANT)) != null) { r.important = true; consume(m); }
        if ((m = find(P_CATEGORY)) != null) {
            int c = Categories.match(m.group(1));
            if (c >= 0) { r.category = c; consume(m); }
        }

        // Recorrência
        if ((m = find(P_EVERY_N)) != null) {
            r.repeatType = Reminder.REPEAT_CUSTOM; r.interval = Math.max(1, num(m.group(1))); r.intervalUnit = unitOf(m.group(2)); consume(m);
        } else if ((m = find(P_EVERY_N2)) != null) {
            r.repeatType = Reminder.REPEAT_CUSTOM; r.interval = Math.max(1, num(m.group(1))); r.intervalUnit = unitOf(m.group(2)); consume(m);
        } else if ((m = find(P_EVERY_1)) != null) {
            String u = m.group(1) != null ? m.group(1) : m.group(2);
            u = u.toLowerCase(Locale.ROOT);
            if (u.startsWith("ano")) r.repeatType = Reminder.REPEAT_YEARLY;
            else { r.repeatType = Reminder.REPEAT_CUSTOM; r.interval = 1; r.intervalUnit = unitOf(u); }
            consume(m);
        } else if ((m = find(P_WEEKDAYS)) != null) {
            r.repeatType = Reminder.REPEAT_WEEKDAYS; consume(m);
        } else if ((m = find(P_WEEKENDS)) != null) {
            r.repeatType = Reminder.REPEAT_WEEKLY; r.weekMask = 0x60; consume(m);
        } else if ((m = find(P_MONTHLY_DAY)) != null) {
            String d = m.group(1) != null ? m.group(1) : (m.group(2) != null ? m.group(2) : m.group(3));
            r.repeatType = Reminder.REPEAT_MONTHLY; r.monthDay = Math.min(31, Math.max(1, num(d))); consume(m);
        } else if ((m = find(P_DAILY)) != null) {
            r.repeatType = Reminder.REPEAT_DAILY; consume(m);
        } else if ((m = find(P_WEEKLY_DAYS)) != null) {
            r.repeatType = Reminder.REPEAT_WEEKLY;
            r.weekMask = 1 << weekdayIndex(m.group(1));
            String rest = m.group(2);
            if (rest != null && !rest.trim().isEmpty()) {
                Matcher w = p(WD).matcher(rest);
                while (w.find()) r.weekMask |= 1 << weekdayIndex(w.group(1));
            }
            consume(m);
        } else if ((m = find(P_WEEKLY)) != null) {
            r.repeatType = Reminder.REPEAT_WEEKLY; consume(m);
        } else if ((m = find(P_MONTHLY)) != null) {
            r.repeatType = Reminder.REPEAT_MONTHLY; consume(m);
        } else if ((m = find(P_YEARLY)) != null) {
            r.repeatType = Reminder.REPEAT_YEARLY; consume(m);
        }

        // Tempo relativo
        LocalDateTime rel = null;
        if ((m = find(P_REL_HALF)) != null) {
            rel = now.plusMinutes(m.group().contains("uma hora") ? 90 : 30);
            consume(m);
        } else if ((m = find(P_REL)) != null) {
            String unit = m.group(2).toLowerCase(Locale.ROOT);
            String n = m.group(1).toLowerCase(Locale.ROOT);
            int k = num(n);
            if (unit.startsWith("min")) rel = now.plusMinutes(k);
            else if (unit.startsWith("h")) rel = n.equals("meia") ? now.plusMinutes(30) : now.plusHours(k);
            else if (unit.startsWith("sem")) rel = now.plusWeeks(k);
            else if (unit.startsWith("m")) rel = now.plusMonths(k);
            else rel = now.plusDays(k);
            consume(m);
        }

        // Data
        LocalDate today = now.toLocalDate();
        LocalDate date = null;
        int dateKind = 0; // 1 = fixa, 2 = dia da semana, 3 = dia do mês, 4 = dia/mês sem ano
        boolean explicitNext = false;
        Integer partHour = null;
        if ((m = find(P_AFTER_TOMORROW)) != null) { date = today.plusDays(2); dateKind = 1; consume(m); }
        else if ((m = find(P_TOMORROW)) != null) { date = today.plusDays(1); dateKind = 1; consume(m); }
        else if ((m = find(P_TODAY)) != null) {
            date = today; dateKind = 1;
            String g = m.group().toLowerCase(Locale.ROOT);
            if (g.contains("noite")) partHour = 20; else if (g.contains("tarde")) partHour = 15;
            consume(m);
        }
        else if ((m = find(P_NEXT_WEEK)) != null) { date = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)); dateKind = 1; consume(m); }
        else if ((m = find(P_NEXT_MONTH)) != null) { date = today.plusMonths(1).withDayOfMonth(1); dateKind = 1; consume(m); }
        else if ((m = find(P_DMY)) != null) {
            int d = num(m.group(1)), mo = num(m.group(2));
            if (mo >= 1 && mo <= 12 && d >= 1 && d <= 31) {
                int y = today.getYear();
                dateKind = 4;
                if (m.group(3) != null) { y = num(m.group(3)); if (y < 100) y += 2000; dateKind = 1; }
                date = safeDate(y, mo, d);
                consume(m);
            }
        }
        if (date == null && (m = find(P_D_MONTH)) != null) {
            int d = num(m.group(1)), mo = monthIndex(m.group(2));
            int y = today.getYear();
            dateKind = 4;
            if (m.group(3) != null) { y = num(m.group(3)); dateKind = 1; }
            date = safeDate(y, mo, d);
            consume(m);
        }
        if (date == null && (m = find(P_DAY)) != null) {
            int d = num(m.group(1));
            if (d >= 1 && d <= 31) {
                if (r.repeatType == Reminder.REPEAT_MONTHLY || (r.repeatType == Reminder.REPEAT_CUSTOM && r.intervalUnit == Reminder.UNIT_MONTH)) r.monthDay = d;
                date = safeDate(today.getYear(), today.getMonthValue(), d);
                dateKind = 3;
                consume(m);
            }
        }
        if (date == null && (m = find(P_WEEKDAY)) != null) {
            int idx = weekdayIndex(m.group(2));
            explicitNext = m.group(1) != null || m.group(3) != null;
            DayOfWeek dow = DayOfWeek.of(idx + 1);
            date = explicitNext ? today.with(TemporalAdjusters.next(dow)) : today.with(TemporalAdjusters.nextOrSame(dow));
            dateKind = 2;
            consume(m);
        }

        // Hora
        LocalTime time = null;
        if ((m = find(P_TIME_HM)) != null) {
            int h = num(m.group(1)), mi = num(m.group(2));
            if (h <= 23 && mi <= 59) { time = LocalTime.of(applyPart(h, m.group(3)), mi); consume(m); }
        }
        if (time == null && (m = find(P_TIME_AT)) != null) {
            int h = num(m.group(1));
            if (h <= 23) { time = LocalTime.of(applyPart(h, m.group(3)), minutesWord(m.group(2))); consume(m); }
        }
        if (time == null && (m = find(P_TIME_H)) != null) {
            int h = num(m.group(1));
            if (h <= 23) { time = LocalTime.of(applyPart(h, m.group(3)), minutesWord(m.group(2))); consume(m); }
        }
        if (time == null && (m = find(P_NOON)) != null) { time = LocalTime.of(12, m.group(1) != null ? 30 : 0); consume(m); }
        if (time == null && (m = find(P_MIDNIGHT)) != null) { time = LocalTime.of(23, 59); consume(m); }
        if ((m = find(P_PART)) != null) {
            String g = m.group(1).toLowerCase(Locale.ROOT);
            int ph = g.startsWith("manh") ? 9 : g.startsWith("tarde") ? 15 : g.startsWith("noite") ? 20
                    : g.startsWith("fim da tarde") ? 18 : g.startsWith("fim") ? 19 : g.startsWith("jantar") ? 20 : 13;
            if (time == null) partHour = ph;
            else if (time.getHour() < 12 && ph >= 15) time = time.plusHours(12);
            consume(m);
        }
        if (time == null && partHour != null) time = LocalTime.of(partHour, 0);
        r.hasTime = time != null || rel != null;

        // Composição
        LocalDateTime dt = null;
        if (rel != null) {
            dt = rel;
            if (date != null) dt = date.atTime(rel.toLocalTime());
            if (time != null) dt = dt.toLocalDate().atTime(time);
        } else if (date != null || time != null || r.repeatType != Reminder.REPEAT_NONE) {
            LocalTime t = time != null ? time : LocalTime.of(defHour, defMinute);
            if (r.repeatType == Reminder.REPEAT_CUSTOM && r.intervalUnit == Reminder.UNIT_HOUR && time == null && date == null) {
                dt = now.plusHours(r.interval);
            } else {
                LocalDate d = date != null ? date : today;
                if (date == null && r.monthDay > 0) d = safeDate(today.getYear(), today.getMonthValue(), r.monthDay);
                dt = d.atTime(t);
                if (r.repeatType == Reminder.REPEAT_MONTHLY && r.monthDay == 0) r.monthDay = d.getDayOfMonth();
                Reminder tmp = r.toReminder();
                if (date == null && r.repeatType != Reminder.REPEAT_NONE) {
                    dt = Recurrence.ldt(Recurrence.alignFirst(tmp, Recurrence.ms(dt)));
                }
                if (!dt.isAfter(now)) {
                    if (r.repeatType != Reminder.REPEAT_NONE) {
                        long x = Recurrence.ms(dt);
                        for (int i = 0; i < 1000 && x <= Recurrence.ms(now); i++) x = Recurrence.rawNext(tmp, x);
                        dt = Recurrence.ldt(x);
                    } else if (date == null) {
                        dt = dt.plusDays(1);
                    } else if (dateKind == 2) {
                        dt = dt.plusWeeks(1);
                    } else if (dateKind == 3) {
                        LocalDate nm = today.withDayOfMonth(1).plusMonths(1);
                        dt = safeDate(nm.getYear(), nm.getMonthValue(), dt.getDayOfMonth()).atTime(dt.toLocalTime());
                    } else if (dateKind == 4 && dt.toLocalDate().isBefore(today)) {
                        dt = dt.plusYears(1);
                    }
                }
            }
        }
        if (dt != null) r.dueAt = Recurrence.ms(dt);

        // Título
        String title = s.replaceAll("\\s+", " ").trim();
        title = P_PREFIX.matcher(title).replaceFirst("");
        java.util.ArrayList<String> tok = new java.util.ArrayList<String>(java.util.Arrays.asList(title.split(" ")));
        while (!tok.isEmpty() && (tok.get(tok.size() - 1).isEmpty() || TRAIL.contains(tok.get(tok.size() - 1).toLowerCase(Locale.ROOT)))) tok.remove(tok.size() - 1);
        while (!tok.isEmpty() && (tok.get(0).isEmpty() || LEAD.contains(tok.get(0).toLowerCase(Locale.ROOT)))) tok.remove(0);
        StringBuilder tb = new StringBuilder();
        for (String t : tok) { if (tb.length() > 0) tb.append(' '); tb.append(t); }
        title = tb.toString();
        if (title.endsWith(",")) title = title.substring(0, title.length() - 1);
        title = title.replaceAll("\\s+([,.;:])", "$1").replaceAll("\\s+", " ").trim();
        r.title = Fmt.cap(title);
        return r;
    }

    private static LocalDate safeDate(int y, int mo, int d) {
        LocalDate first = LocalDate.of(y, mo, 1);
        return first.withDayOfMonth(Math.min(d, first.lengthOfMonth()));
    }
}
