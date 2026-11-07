package pt.lembra.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import pt.lembra.R;

/** Pequenos utilitários para construir a interface em código com um estilo consistente. */
public final class Ui {
    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static int col(Context c, int res) { return c.getColor(res); }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable stroke(int fill, int strokeColor, int strokePx, float radiusPx) {
        GradientDrawable g = round(fill, radiusPx);
        g.setStroke(strokePx, strokeColor);
        return g;
    }

    public static Drawable ripple(Context c, Drawable content, float radiusPx) {
        int rc = (col(c, R.color.text) & 0x00FFFFFF) | 0x22000000;
        return new RippleDrawable(ColorStateList.valueOf(rc), content, content == null ? round(0xFFFFFFFF, radiusPx) : null);
    }

    public static TextView text(Context c, String s, float sp, int colorRes, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(col(c, colorRes));
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    public static ImageView icon(Context c, int res, int colorRes, int sizeDp, int padDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(col(c, colorRes)));
        int p = dp(c, padDp);
        iv.setPadding(p, p, p, p);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return iv;
    }

    public static ImageView iconButton(Context c, int res, String desc, View.OnClickListener l) {
        ImageView iv = icon(c, res, R.color.text, 44, 10);
        iv.setBackground(ripple(c, null, dp(c, 22)));
        iv.setContentDescription(desc);
        iv.setOnClickListener(l);
        return iv;
    }

    /** Chip arredondado selecionável. */
    public static TextView chip(Context c, String label, boolean selected, int dotColor) {
        TextView t = text(c, label, 14, selected ? R.color.onAccent : R.color.text, selected);
        int bg = selected ? col(c, R.color.accent) : col(c, R.color.chip);
        t.setBackground(ripple(c, round(bg, dp(c, 18)), dp(c, 18)));
        t.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        if (dotColor != 0 && !selected) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(dotColor);
            dot.setSize(dp(c, 8), dp(c, 8));
            t.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null);
            t.setCompoundDrawablePadding(dp(c, 6));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(c, 8));
        t.setLayoutParams(lp);
        return t;
    }

    /** Botão "pílula" principal ou secundário. */
    public static TextView button(Context c, String label, boolean primary) {
        TextView t = text(c, label, 15, primary ? R.color.onAccent : R.color.accent, true);
        int bg = primary ? col(c, R.color.accent) : col(c, R.color.accentSoft);
        t.setBackground(ripple(c, round(bg, dp(c, 24)), dp(c, 24)));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 20), dp(c, 12), dp(c, 20), dp(c, 12));
        return t;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(stroke(col(c, R.color.surface), col(c, R.color.cardStroke), Math.max(1, dp(c, 1)), dp(c, 20)));
        l.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 18));
        l.setElevation(dp(c, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        l.setLayoutParams(lp);
        return l;
    }

    public static TextView sectionLabel(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 13, R.color.accent, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(c, 2), dp(c, 2), 0, dp(c, 12));
        return t;
    }

    /** Cabeçalho de cartão: ícone num círculo suave + título. Separa claramente cada bloco. */
    public static LinearLayout cardHeader(Context c, int iconRes, String title) {
        LinearLayout h = new LinearLayout(c);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(0, 0, 0, dp(c, 14));
        ImageView ic = icon(c, iconRes, R.color.accent, 34, 7);
        ic.setBackground(round(col(c, R.color.accentSoft), dp(c, 12)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 34), dp(c, 34));
        lp.setMarginEnd(dp(c, 12));
        h.addView(ic, lp);
        TextView t = text(c, title, 17, R.color.text, true);
        h.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return h;
    }

    /** Linha divisória dentro de um cartão, com espaço acima e abaixo. */
    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(col(c, R.color.divider));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1)));
        lp.setMargins(0, dp(c, 14), 0, dp(c, 14));
        v.setLayoutParams(lp);
        return v;
    }

    /** Sub-título dentro de um cartão (p.ex. "Termina"). */
    public static TextView subLabel(Context c, String s) {
        TextView t = text(c, s, 14, R.color.text2, true);
        t.setPadding(dp(c, 2), 0, 0, dp(c, 8));
        return t;
    }

    /** Linha "título + subtítulo" clicável usada em listas de opções. */
    public static LinearLayout optionRow(Context c, String title, String sub, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(c, 4), dp(c, 12), dp(c, 4), dp(c, 12));
        row.setBackground(ripple(c, null, dp(c, 10)));
        TextView t = text(c, title, 16, R.color.text, false);
        row.addView(t);
        if (sub != null) {
            TextView s = text(c, sub, 13, R.color.text2, false);
            s.setTag("sub");
            row.addView(s);
        }
        if (l != null) row.setOnClickListener(l);
        return row;
    }

    public static void ellipsize(TextView t, int lines) {
        t.setMaxLines(lines);
        t.setEllipsize(TextUtils.TruncateAt.END);
    }

    public static View spacer(Context c, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, hDp)));
        return v;
    }

    public static LinearLayout.LayoutParams lpMW() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams lpWeight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }
}
