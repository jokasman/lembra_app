package pt.lembra.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Volta a agendar os alarmes após reiniciar, atualizar a app ou mudar a hora/fuso horário. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        pt.lembra.core.Recurrence.zone = java.time.ZoneId.systemDefault();
        Notifier.ensureChannels(ctx);
        Engine.rescheduleAll(ctx);
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(intent.getAction())) {
            // As notificações perdem-se ao reiniciar: volta a mostrar as pendentes.
            for (pt.lembra.core.Reminder r : pt.lembra.data.Db.get(ctx).all())
                if (!r.done && r.notified) Notifier.showMain(ctx, r, false);
        }
    }
}
