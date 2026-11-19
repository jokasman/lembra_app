package pt.lembra.core;

/** Regras da pergunta "Agendar lembrete?" depois de uma chamada. Classe pura (sem Android). */
public final class CallFollowUp {
    private CallFollowUp() {}

    /** Tipos de chamada terminada (bits da máscara das definições). */
    public static final int NONE = 0, ANSWERED = 1, REJECTED = 2, MISSED = 4;
    public static final int DEFAULT_MASK = ANSWERED | REJECTED;

    /** Tipos do registo de chamadas do Android (CallLog.Calls.TYPE); -1 = desconhecido. */
    public static final int LOG_UNKNOWN = -1, LOG_INCOMING = 1, LOG_OUTGOING = 2, LOG_MISSED = 3, LOG_VOICEMAIL = 4,
            LOG_REJECTED = 5, LOG_BLOCKED = 6, LOG_ANSWERED_EXTERNALLY = 7;

    /** Opções rápidas da pergunta, em minutos. */
    public static final int[] MINUTES = {10, 30, 60, 240};

    /**
     * Classifica a chamada que acabou de terminar.
     * @param rang    o telemóvel tocou (chamada recebida)
     * @param offhook a chamada esteve em linha (atendida, ou feita por nós)
     * @param logType tipo no registo de chamadas, ou LOG_UNKNOWN se ainda não estiver disponível
     */
    public static int classify(boolean rang, boolean offhook, int logType) {
        if (logType == LOG_BLOCKED || logType == LOG_VOICEMAIL || logType == LOG_ANSWERED_EXTERNALLY) return NONE;
        if (offhook) return ANSWERED;
        if (!rang) return NONE;
        // Tocou e não foi atendida: perdida se o registo o disser; caso contrário foi rejeitada
        // (botão vermelho ou resposta com mensagem rápida).
        return logType == LOG_MISSED ? MISSED : REJECTED;
    }

    public static boolean shouldAsk(int kind, int mask) { return kind != NONE && (kind & mask) != 0; }

    /** Título proposto para o lembrete. */
    public static String title(String name, String number, String time) {
        if (name != null && !name.trim().isEmpty()) return "Ligar a " + name.trim();
        if (number != null && !number.trim().isEmpty()) return "Ligar a " + number.trim();
        return "Seguimento da chamada das " + time;
    }

    /** Descrição curta do tipo de chamada, para o cabeçalho da pergunta. */
    public static String describe(int kind) {
        switch (kind) {
            case REJECTED: return "Chamada rejeitada";
            case MISSED: return "Chamada perdida";
            default: return "Chamada terminada";
        }
    }
}
