package pt.lembra.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import pt.lembra.data.Prefs;

/** Base de todos os ecrãs: aplica o tema escolhido (automático, claro ou escuro). */
public abstract class BaseActivity extends Activity {
    public static final int THEME_AUTO = 0, THEME_LIGHT = 1, THEME_DARK = 2;
    private int appliedTheme = -1;

    /** Contexto com o modo noturno forçado conforme a preferência. */
    static Context themed(Context base) {
        int t = Prefs.theme(base);
        if (t == THEME_AUTO) return base;
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.uiMode = (c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (t == THEME_DARK ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return base.createConfigurationContext(c);
    }

    @Override protected void attachBaseContext(Context base) {
        appliedTheme = Prefs.theme(base);
        super.attachBaseContext(themed(base));
    }

    @Override protected void onResume() {
        super.onResume();
        // O tema mudou noutro ecrã: redesenha este.
        if (appliedTheme != Prefs.theme(this)) recreate();
    }
}
