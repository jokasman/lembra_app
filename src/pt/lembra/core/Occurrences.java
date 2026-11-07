package pt.lembra.core;

import java.util.ArrayList;
import java.util.List;

/** Expansão de ocorrências (para o calendário) e deteção de lembretes coincidentes. */
public final class Occurrences {
    private Occurrences() {}

    /** Instantes em que o lembrete toca dentro de [from, to] (inclusive), no máximo {@code max}. */
    public static List<Long> between(Reminder r, long from, long to, int max) {
        List<Long> out = new ArrayList<Long>();
        if (r.done) return out;
        if (r.isLocation()) {
            // Por localização: só tem hora quando foi adiado.
            if (r.isSnoozed() && r.snoozedUntil >= from && r.snoozedUntil <= to) out.add(r.snoozedUntil);
            return out;
        }
        long eff = r.effectiveTime();
        if (eff >= from && eff <= to) out.add(eff);
        if (!r.isRecurring()) return out;
        long cur = r.dueAt;
        int c = r.count;
        for (int guard = 0; guard < 20000 && out.size() < max; guard++) {
            long n = Recurrence.next(r, cur, c + 1);
            if (n < 0 || n > to) break;
            c++;
            cur = n;
            if (n >= from && n != eff) out.add(n);
        }
        return out;
    }

    /** Um conflito: o outro lembrete e o instante em que coincide. */
    public static final class Conflict {
        public final Reminder other;
        public final long at;
        Conflict(Reminder other, long at) { this.other = other; this.at = at; }
    }

    /**
     * Ocorrências de {@code r} nos próximos {@code horizonMs} que coincidem (± {@code tolMin} minutos)
     * com ocorrências de outros lembretes ativos. Devolve no máximo {@code max} conflitos.
     */
    public static List<Conflict> conflicts(Reminder r, List<Reminder> all, long now, long horizonMs, int tolMin, int max) {
        List<Conflict> out = new ArrayList<Conflict>();
        if (r.done) return out;
        long tol = Math.max(0, tolMin) * 60000L;
        List<Long> mine = between(r, now - 60000, now + horizonMs, 400);
        for (Long t : mine) {
            long a = minute(t) - tol, b = minute(t) + tol + 59999;
            for (Reminder o : all) {
                if (o.done || o.id == r.id) continue;
                List<Long> hits = between(o, a, b, 1);
                if (!hits.isEmpty()) {
                    out.add(new Conflict(o, t));
                    if (out.size() >= max) return out;
                }
            }
        }
        return out;
    }

    /** Conflito apenas da próxima ocorrência de {@code r} (usado na lista). */
    public static Conflict nextConflict(Reminder r, List<Reminder> all, int tolMin) {
        if (r.done || (r.isLocation() && !r.isSnoozed())) return null;
        long t = r.effectiveTime();
        long tol = Math.max(0, tolMin) * 60000L;
        long a = minute(t) - tol, b = minute(t) + tol + 59999;
        for (Reminder o : all) {
            if (o.done || o.id == r.id) continue;
            if (!between(o, a, b, 1).isEmpty()) return new Conflict(o, t);
        }
        return null;
    }

    static long minute(long t) { return t - Math.floorMod(t, 60000L); }
}
