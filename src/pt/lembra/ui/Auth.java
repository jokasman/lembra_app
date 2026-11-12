package pt.lembra.ui;

import android.app.Activity;
import android.app.Fragment;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.widget.Toast;

import pt.lembra.core.Reminder;
import pt.lembra.data.Prefs;

/** Confirmação com PIN, padrão, palavra-passe ou impressão digital do telemóvel. */
public final class Auth {
    private Auth() {}

    private static final int REQ = 9731;
    /** Autenticadores aceites (API 30+): biometria fraca ou forte, ou a credencial do telemóvel. */
    private static final int ALLOWED = 0x00FF | 0x8000;

    /** Indica se marcar este lembrete como feito exige autenticação. */
    public static boolean needed(Context ctx, Reminder r) {
        return r != null && r.needsDoneAuth(Prefs.doneAuth(ctx));
    }

    /** O telemóvel tem bloqueio de ecrã (PIN, padrão ou palavra-passe)? */
    public static boolean deviceSecure(Context ctx) {
        KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isDeviceSecure();
    }

    public static void require(Activity a, String title, Runnable ok) { require(a, title, ok, null); }

    /** Pede a autenticação e corre {@code ok} se for confirmada, ou {@code cancel} se não for. */
    public static void require(final Activity a, String title, final Runnable ok, final Runnable cancel) {
        KeyguardManager km = (KeyguardManager) a.getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) {
            // Sem bloqueio de ecrã não há como confirmar; não impedimos a conclusão.
            Toast.makeText(a, "O telemóvel não tem bloqueio de ecrã; não foi pedida autenticação.", Toast.LENGTH_LONG).show();
            ok.run();
            return;
        }
        if (km.isKeyguardLocked()) {
            // Por cima do ecrã de bloqueio: desbloquear o telemóvel é a autenticação.
            km.requestDismissKeyguard(a, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { post(a, ok); }
                @Override public void onDismissCancelled() { post(a, cancel); }
                @Override public void onDismissError() { post(a, cancel); }
            });
            return;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                BiometricPrompt.Builder b = new BiometricPrompt.Builder(a)
                        .setTitle(title == null || title.isEmpty() ? "Confirmar" : title)
                        .setSubtitle("Confirme com PIN ou impressão digital");
                if (Build.VERSION.SDK_INT >= 30) b.setAllowedAuthenticators(ALLOWED);
                else b.setDeviceCredentialAllowed(true);
                b.build().authenticate(new CancellationSignal(), a.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) { ok.run(); }
                    @Override public void onAuthenticationError(int code, CharSequence msg) {
                        boolean userCancel = code == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED
                                || code == BiometricPrompt.BIOMETRIC_ERROR_CANCELED;
                        if (!userCancel && msg != null) Toast.makeText(a, msg, Toast.LENGTH_SHORT).show();
                        if (cancel != null) cancel.run();
                    }
                });
                return;
            } catch (Exception e) { /* recorre ao ecrã de credenciais do sistema */ }
        }
        Intent i = km.createConfirmDeviceCredentialIntent(title, "Confirme com PIN ou impressão digital");
        if (i == null) { ok.run(); return; }
        try {
            Holder h = new Holder();
            Holder.ok = ok;
            Holder.cancel = cancel;
            a.getFragmentManager().beginTransaction().add(h, "lembra_auth").commitNow();
            h.startActivityForResult(i, REQ);
        } catch (Exception e) {
            Holder.ok = null;
            Holder.cancel = null;
            Toast.makeText(a, "Não foi possível pedir a autenticação.", Toast.LENGTH_LONG).show();
            if (cancel != null) cancel.run();
        }
    }

    private static void post(Activity a, final Runnable r) {
        if (r != null) a.runOnUiThread(r);
    }

    /** Fragmento sem interface que recebe o resultado do ecrã de credenciais do sistema. */
    public static class Holder extends Fragment {
        static Runnable ok, cancel;

        @Override public void onActivityResult(int req, int res, Intent data) {
            if (req != REQ) return;
            Runnable o = ok, c = cancel;
            ok = null;
            cancel = null;
            try { getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss(); } catch (Exception e) { /* ecrã já fechado */ }
            if (res == Activity.RESULT_OK) { if (o != null) o.run(); }
            else if (c != null) c.run();
        }
    }
}
