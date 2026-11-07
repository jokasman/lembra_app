package pt.lembra.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

import java.util.List;

import pt.lembra.core.Fmt;
import pt.lembra.core.Occurrences;
import pt.lembra.core.Reminder;
import pt.lembra.data.Db;
import pt.lembra.data.Prefs;

/** Aviso de lembretes coincidentes (próximos 90 dias). */
final class ConflictDialog {
    private ConflictDialog() {}

    /**
     * Mostra o aviso se {@code r} coincidir com outros lembretes.
     * Antes de guardar: {@code onKeep} = "Guardar mesmo assim". Depois de guardar: {@code onEdit} = "Mudar hora".
     * @return true se o aviso foi mostrado.
     */
    static boolean check(Activity a, Reminder r, final Runnable onKeep, final Runnable onEdit) {
        int tol = Prefs.conflictTolerance(a);
        if (tol < 0) return false;
        long now = System.currentTimeMillis();
        List<Occurrences.Conflict> cs = Occurrences.conflicts(r, Db.get(a).all(), now, 90L * 86400000L, tol, 4);
        if (cs.isEmpty()) return false;
        StringBuilder sb = new StringBuilder();
        sb.append(tol == 0 ? "Este lembrete tem a mesma hora que:\n" : "Este lembrete fica a menos de " + tol + " min de:\n");
        for (int i = 0; i < Math.min(3, cs.size()); i++) {
            Occurrences.Conflict c = cs.get(i);
            sb.append("\n• «").append(c.other.title).append("» — ").append(Fmt.dayTime(c.at, now).toLowerCase());
        }
        if (cs.size() > 3) sb.append("\n• e outros…");
        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle("Lembretes coincidentes").setMessage(sb.toString());
        if (onKeep != null) {
            b.setPositiveButton("Guardar mesmo assim", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { onKeep.run(); } });
            b.setNegativeButton("Mudar hora", null);
        } else {
            b.setPositiveButton("Manter", null);
            if (onEdit != null) b.setNegativeButton("Mudar hora", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { onEdit.run(); } });
        }
        b.show();
        return true;
    }
}
