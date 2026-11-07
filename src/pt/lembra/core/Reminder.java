package pt.lembra.core;

/** Modelo de um lembrete. Classe pura (sem Android) para poder ser testada na JVM. */
public class Reminder {
    public static final int REPEAT_NONE = 0, REPEAT_DAILY = 1, REPEAT_WEEKDAYS = 2, REPEAT_WEEKLY = 3,
            REPEAT_MONTHLY = 4, REPEAT_YEARLY = 5, REPEAT_CUSTOM = 6;
    public static final int UNIT_HOUR = 1, UNIT_DAY = 2, UNIT_WEEK = 3, UNIT_MONTH = 4;
    public static final int END_NEVER = 0, END_DATE = 1, END_COUNT = 2;

    public long id;
    public String title = "";
    public String notes = "";
    /** Data/hora da ocorrência atual (base, sem adiamento). */
    public long dueAt;
    /** Se > 0, a ocorrência atual foi adiada para este instante. */
    public long snoozedUntil;

    public int repeatType = REPEAT_NONE;
    public int interval = 1;
    public int intervalUnit = UNIT_DAY;
    /** Dias da semana para REPEAT_WEEKLY: bit0 = segunda ... bit6 = domingo. */
    public int weekMask;
    /** Dia do mês de referência para REPEAT_MONTHLY (1..31), 0 = usar o de dueAt. */
    public int monthDay;

    public int endType = END_NEVER;
    public long endDate;
    public int endCount;
    /** Número de ocorrências já concluídas/ultrapassadas. */
    public int count;

    public int category;
    public boolean important;
    public int preAlertMin;
    /** Toca como despertador (som de alarme, ecrã inteiro, mesmo com o telemóvel em silêncio). */
    public boolean alarm = true;

    /** Lembrete por localização: local guardado (0 = lembrete por hora). */
    public long locPlace;
    public static final int LOC_ARRIVE = 1, LOC_LEAVE = 2;
    public int locTrigger = LOC_ARRIVE;
    /** Repetir sempre que chegar/sair (em vez de uma só vez). */
    public boolean locRepeat;

    public boolean isLocation() { return locPlace > 0; }

    public boolean done;
    public long doneAt;
    public long createdAt;

    /** Estado interno de notificação da ocorrência atual. */
    public boolean notified;
    public boolean preNotified;

    public boolean isRecurring() { return repeatType != REPEAT_NONE; }
    public boolean isSnoozed() { return snoozedUntil > 0; }
    public long effectiveTime() { return snoozedUntil > 0 ? snoozedUntil : dueAt; }

    public Reminder copy() {
        Reminder r = new Reminder();
        r.id = id; r.title = title; r.notes = notes; r.dueAt = dueAt; r.snoozedUntil = snoozedUntil;
        r.repeatType = repeatType; r.interval = interval; r.intervalUnit = intervalUnit; r.weekMask = weekMask;
        r.monthDay = monthDay; r.endType = endType; r.endDate = endDate; r.endCount = endCount; r.count = count;
        r.category = category; r.important = important; r.preAlertMin = preAlertMin; r.done = done;
        r.doneAt = doneAt; r.createdAt = createdAt; r.notified = notified; r.preNotified = preNotified; r.alarm = alarm; r.locPlace = locPlace; r.locTrigger = locTrigger; r.locRepeat = locRepeat;
        return r;
    }

    /** Reinicia o estado de notificação (usar sempre que a data muda). */
    public void resetAlertState() { notified = false; preNotified = false; }
}
