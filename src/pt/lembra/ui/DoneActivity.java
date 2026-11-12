package pt.lembra.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import pt.lembra.alarm.Engine;
import pt.lembra.core.Reminder;
import pt.lembra.data.Db;

/** Ecrã invisível aberto pelo botão "Feito" das notificações quando é exigida autenticação. */
public class DoneActivity extends Activity {
    public static Intent intent(Context ctx, long id) {
        return new Intent(ctx, DoneActivity.class).putExtra("id", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true);
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        if (b == null) ask();
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); ask(); }

    private void ask() {
        final long id = getIntent().getLongExtra("id", -1);
        Reminder r = Db.get(this).get(id);
        if (r == null || r.done) { finish(); return; }
        Runnable done = new Runnable() { public void run() {
            Reminder fresh = Db.get(DoneActivity.this).get(id);
            if (fresh != null && !fresh.done) Engine.complete(DoneActivity.this, fresh);
            finish();
        } };
        if (!Auth.needed(this, r)) { done.run(); return; }
        Auth.require(this, r.title, done, new Runnable() { public void run() { finish(); } });
    }
}
