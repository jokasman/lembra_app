package pt.lembra.alarm;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import pt.lembra.core.CallFollowUp;
import pt.lembra.core.Fmt;
import pt.lembra.data.Prefs;
import pt.lembra.ui.CallPromptActivity;

/** Deteta o fim de uma chamada e pergunta se é para agendar um lembrete. */
public class CallReceiver extends BroadcastReceiver {
    private static final int RANG = 1, OFFHOOK = 2;

    /** Permissões indispensáveis para a função (contactos é opcional). */
    public static boolean hasPermissions(Context ctx) {
        return granted(ctx, Manifest.permission.READ_PHONE_STATE) && granted(ctx, Manifest.permission.READ_CALL_LOG);
    }

    private static boolean granted(Context ctx, String p) {
        return ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void onReceive(final Context ctx, Intent intent) {
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;
        if (!Prefs.callPrompt(ctx)) { if (Prefs.callState(ctx) != 0) Prefs.setCallState(ctx, 0); return; }
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String incoming = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
        int s = Prefs.callState(ctx);
        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state) || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            if (s == 0) {
                Prefs.setStr(ctx, "call_start", String.valueOf(System.currentTimeMillis()));
                Prefs.setStr(ctx, "call_number", null);
            }
            if (incoming != null && !incoming.isEmpty()) Prefs.setStr(ctx, "call_number", incoming);
            Prefs.setCallState(ctx, s | (TelephonyManager.EXTRA_STATE_RINGING.equals(state) ? RANG : OFFHOOK));
            return;
        }
        if (!TelephonyManager.EXTRA_STATE_IDLE.equals(state) || s == 0) return; // o sistema envia este aviso em duplicado
        Prefs.setCallState(ctx, 0);
        final boolean rang = (s & RANG) != 0, offhook = (s & OFFHOOK) != 0;
        long st = 0;
        try { st = Long.parseLong(Prefs.str(ctx, "call_start")); } catch (Exception e) { /* sem início registado */ }
        final long start = st;
        final String fallbackNumber = Prefs.str(ctx, "call_number");
        final PendingResult pr = goAsync();
        new Thread(new Runnable() { public void run() {
            try { finish(ctx.getApplicationContext(), rang, offhook, start, fallbackNumber); }
            catch (Exception e) { /* nunca deixar cair o recetor */ }
            finally { pr.finish(); }
        } }, "lembra-call").start();
    }

    private static void finish(Context ctx, boolean rang, boolean offhook, long start, String fallbackNumber) throws Exception {
        // O registo de chamadas só é escrito um pouco depois de a chamada terminar.
        String[] row = null;
        for (int attempt = 0; attempt < 3 && row == null; attempt++) {
            Thread.sleep(1200);
            row = lastCall(ctx, start);
        }
        int logType = CallFollowUp.LOG_UNKNOWN;
        String number = fallbackNumber, name = null;
        if (row != null) {
            try { logType = Integer.parseInt(row[0]); } catch (Exception e) { /* tipo desconhecido */ }
            if (row[1] != null && !row[1].isEmpty()) number = row[1];
            name = row[2];
        }
        int kind = CallFollowUp.classify(rang, offhook, logType);
        if (!CallFollowUp.shouldAsk(kind, Prefs.callPromptMask(ctx))) return;
        // Números privados aparecem no registo como valores negativos ou vazios.
        if (number != null && (number.isEmpty() || number.startsWith("-"))) number = null;
        if ((name == null || name.isEmpty()) && number != null) name = contactName(ctx, number);
        long now = System.currentTimeMillis();
        String title = CallFollowUp.title(name, number, Fmt.time(now));
        Intent i = new Intent(ctx, CallPromptActivity.class).putExtra("title", title).putExtra("kind", kind)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Settings.canDrawOverlays(ctx)) {
            try { ctx.startActivity(i); return; } catch (Exception e) { /* recorre à notificação */ }
        }
        PendingIntent pi = PendingIntent.getActivity(ctx, 888_001, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notifier.showCallPrompt(ctx, pi, CallFollowUp.describe(kind), title);
    }

    /** Última chamada do registo, se for a que acabou agora: {tipo, número, nome}. */
    private static String[] lastCall(Context ctx, long start) {
        if (!granted(ctx, Manifest.permission.READ_CALL_LOG)) return null;
        Cursor c = null;
        try {
            Uri uri = CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "1").build();
            c = ctx.getContentResolver().query(uri, new String[]{CallLog.Calls.TYPE, CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE}, null, null, CallLog.Calls.DATE + " DESC");
            if (c == null || !c.moveToFirst()) return null;
            if (start > 0 && c.getLong(3) < start - 15000) return null; // ainda é a chamada anterior
            return new String[]{String.valueOf(c.getInt(0)), c.getString(1), c.getString(2)};
        } catch (Exception e) {
            return null;
        } finally { if (c != null) c.close(); }
    }

    private static String contactName(Context ctx, String number) {
        if (!granted(ctx, Manifest.permission.READ_CONTACTS)) return null;
        Cursor c = null;
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            c = ctx.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            return c != null && c.moveToFirst() ? c.getString(0) : null;
        } catch (Exception e) {
            return null;
        } finally { if (c != null) c.close(); }
    }
}
