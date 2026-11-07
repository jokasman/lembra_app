package pt.lembra.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import pt.lembra.R;
import pt.lembra.alarm.Engine;
import pt.lembra.core.Categories;
import pt.lembra.data.Db;

/** Manutenção de categorias: criar, renomear, mudar a cor, reordenar e apagar. */
public class CategoriesActivity extends BaseActivity {
    private LinearLayout list;

    private int dp(float v) { return Ui.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(8), dp(12), dp(4));
        top.addView(Ui.iconButton(this, R.drawable.ic_back, "Voltar", new View.OnClickListener() { public void onClick(View v) { finish(); } }));
        TextView t = Ui.text(this, "Categorias", 20, R.color.text, true);
        t.setPadding(dp(6), 0, 0, 0);
        top.addView(t, Ui.lpWeight(1));
        col.addView(top);

        TextView hint = Ui.text(this, "Toque numa categoria para mudar o nome ou a cor. Na escrita rápida use #nome (ex.: #ginásio).", 13, R.color.text2, false);
        hint.setPadding(dp(20), dp(4), dp(20), dp(8));
        col.addView(hint);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, dp(110));
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView add = Ui.button(this, "+  Nova categoria", true);
        add.setElevation(dp(6));
        add.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { edit(null); } });
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        alp.bottomMargin = dp(28);
        root.addView(add, alp);
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); load(); }

    private void load() {
        list.removeAllViews();
        final List<Categories.Cat> cats = Categories.all();
        LinearLayout card = Ui.card(this);
        card.setPadding(dp(6), dp(6), dp(6), dp(6));
        for (int i = 0; i < cats.size(); i++) {
            if (i > 0) {
                View div = new View(this);
                div.setBackgroundColor(Ui.col(this, R.color.divider));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
                dlp.setMargins(dp(54), 0, dp(10), 0);
                card.addView(div, dlp);
            }
            card.addView(row(cats, i));
        }
        list.addView(card);
    }

    private View row(final List<Categories.Cat> cats, final int index) {
        final Categories.Cat c = cats.get(index);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(4), 0);
        row.setMinimumHeight(dp(60));
        row.setBackground(Ui.ripple(this, null, dp(14)));

        View dot = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(c.color);
        dot.setBackground(g);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(26), dp(26));
        dlp.setMarginEnd(dp(16));
        row.addView(dot, dlp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(this, c.name, 16, R.color.text, true));
        int n = Db.get(this).countInCategory(c.id);
        String sub = (n == 0 ? "Sem lembretes" : n == 1 ? "1 lembrete" : n + " lembretes") + (c.id == Categories.GENERAL ? " · categoria por defeito" : "");
        texts.addView(Ui.text(this, sub, 13, R.color.text2, false));
        row.addView(texts, Ui.lpWeight(1));

        ImageView up = Ui.iconButton(this, R.drawable.ic_up, "Subir", new View.OnClickListener() { public void onClick(View v) { move(cats, index, -1); } });
        up.setAlpha(index == 0 ? 0.25f : 1f);
        up.setEnabled(index > 0);
        row.addView(up);
        ImageView down = Ui.iconButton(this, R.drawable.ic_down, "Descer", new View.OnClickListener() { public void onClick(View v) { move(cats, index, 1); } });
        down.setAlpha(index == cats.size() - 1 ? 0.25f : 1f);
        down.setEnabled(index < cats.size() - 1);
        row.addView(down);

        row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { edit(c); } });
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private void move(List<Categories.Cat> cats, int i, int d) {
        int j = i + d;
        if (j < 0 || j >= cats.size()) return;
        Categories.Cat tmp = cats.get(i);
        cats.set(i, cats.get(j));
        cats.set(j, tmp);
        Db.get(this).saveCategoryOrder(cats);
        Engine.changed(this);
        load();
    }

    /** Diálogo de criação/edição: nome + paleta de cores (+ apagar). */
    private void edit(final Categories.Cat existing) {
        final boolean isNew = existing == null;
        final int[] color = {isNew ? Categories.PALETTE[(Categories.all().size()) % Categories.PALETTE.length] : existing.color};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        final EditText name = new EditText(this);
        name.setHint("Nome da categoria");
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (!isNew) { name.setText(existing.name); name.setSelection(existing.name.length()); }
        box.addView(name, Ui.lpMW());
        TextView cl = Ui.subLabel(this, "Cor");
        cl.setPadding(dp(2), dp(16), 0, dp(10));
        box.addView(cl);

        final LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        box.addView(grid, Ui.lpMW());
        final Runnable[] render = new Runnable[1];
        render[0] = new Runnable() { public void run() {
            grid.removeAllViews();
            LinearLayout line = null;
            for (int i = 0; i < Categories.PALETTE.length; i++) {
                if (i % 6 == 0) { line = new LinearLayout(this_()); grid.addView(line, Ui.lpMW()); }
                final int col = Categories.PALETTE[i];
                FrameLayout cell = new FrameLayout(this_());
                View sw = new View(this_());
                GradientDrawable gd = new GradientDrawable();
                gd.setShape(GradientDrawable.OVAL);
                gd.setColor(col);
                if (col == color[0]) gd.setStroke(dp(3), Ui.col(this_(), R.color.text));
                sw.setBackground(gd);
                cell.addView(sw, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
                if (col == color[0]) {
                    ImageView chk = Ui.icon(this_(), R.drawable.ic_check, R.color.onAccent, 36, 8);
                    chk.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                    cell.addView(chk, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
                }
                cell.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { color[0] = col; render[0].run(); } });
                line.addView(cell, new LinearLayout.LayoutParams(0, dp(48), 1));
            }
        } };
        render[0].run();

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(isNew ? "Nova categoria" : "Editar categoria")
                .setView(box)
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar", null);
        if (!isNew && existing.id != Categories.GENERAL) {
            b.setNeutralButton("Apagar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { confirmDelete(existing); }
            });
        }
        final AlertDialog dlg = b.create();
        dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
        // Validação sem fechar o diálogo
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) { name.setError("Escreva um nome"); return; }
            for (Categories.Cat o : Categories.all()) {
                if (o.name.equalsIgnoreCase(n) && (isNew || o.id != existing.id)) { name.setError("Já existe uma categoria com este nome"); return; }
            }
            Categories.Cat c = isNew ? new Categories.Cat(-1, n, color[0], 0) : new Categories.Cat(existing.id, n, color[0], existing.sort);
            Db.get(CategoriesActivity.this).saveCategory(c);
            Engine.changed(CategoriesActivity.this);
            dlg.dismiss();
            load();
        } });
    }

    private CategoriesActivity this_() { return this; }

    private void confirmDelete(final Categories.Cat c) {
        int n = Db.get(this).countInCategory(c.id);
        String msg = n == 0 ? "Apagar a categoria «" + c.name + "»?"
                : "Apagar a categoria «" + c.name + "»?\n\n" + (n == 1 ? "O lembrete dela passa" : "Os " + n + " lembretes dela passam") + " para «Geral». Os lembretes não são apagados.";
        new AlertDialog.Builder(this).setTitle("Apagar categoria").setMessage(msg)
                .setPositiveButton("Apagar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        Db.get(CategoriesActivity.this).deleteCategory(c.id);
                        Engine.changed(CategoriesActivity.this);
                        Toast.makeText(CategoriesActivity.this, "Categoria apagada", Toast.LENGTH_SHORT).show();
                        load();
                    }
                })
                .setNegativeButton("Cancelar", null).show();
    }
}
