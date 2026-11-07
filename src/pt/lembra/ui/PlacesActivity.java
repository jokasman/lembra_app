package pt.lembra.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

import pt.lembra.R;
import pt.lembra.alarm.Engine;
import pt.lembra.data.Db;

/** Locais guardados (Casa, Escritório…) para lembretes por localização. */
public class PlacesActivity extends BaseActivity {
    private LinearLayout list;
    private boolean pick;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int dp(float v) { return Ui.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        pick = getIntent().getBooleanExtra("pick", false);
        FrameLayout root = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(8), dp(12), dp(4));
        top.addView(Ui.iconButton(this, R.drawable.ic_back, "Voltar", new View.OnClickListener() { public void onClick(View v) { finish(); } }));
        TextView t = Ui.text(this, pick ? "Escolher local" : "Locais guardados", 20, R.color.text, true);
        t.setPadding(dp(6), 0, 0, 0);
        top.addView(t, Ui.lpWeight(1));
        col.addView(top);
        TextView hint = Ui.text(this, "Guarde os sítios onde quer ser lembrado (casa, trabalho, supermercado…). A localização fica só neste telemóvel.", 13, R.color.text2, false);
        hint.setPadding(dp(20), dp(4), dp(20), dp(8));
        col.addView(hint);
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, dp(110));
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView add = Ui.button(this, "+  Novo local", true);
        add.setElevation(dp(6));
        add.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { edit(null); } });
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        alp.bottomMargin = dp(28);
        root.addView(add, alp);
        setContentView(root);
        if (pick && Db.get(this).places().isEmpty()) edit(null);
    }

    @Override protected void onResume() { super.onResume(); load(); }

    private void load() {
        list.removeAllViews();
        List<Db.Place> places = Db.get(this).places();
        if (places.isEmpty()) {
            TextView e = Ui.text(this, "Ainda não tem locais. Toque em \"+ Novo local\".", 14, R.color.text2, false);
            e.setPadding(dp(20), dp(20), dp(20), dp(20));
            list.addView(e);
            return;
        }
        LinearLayout card = Ui.card(this);
        card.setPadding(dp(6), dp(6), dp(6), dp(6));
        boolean first = true;
        for (final Db.Place p : places) {
            if (!first) {
                View d = new View(this);
                d.setBackgroundColor(Ui.col(this, R.color.divider));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
                dlp.setMargins(dp(54), 0, dp(10), 0);
                card.addView(d, dlp);
            }
            first = false;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(4), dp(10));
            row.setBackground(Ui.ripple(this, null, dp(14)));
            android.widget.ImageView ic = Ui.icon(this, R.drawable.ic_place, R.color.accent, 34, 7);
            ic.setBackground(Ui.round(Ui.col(this, R.color.accentSoft), dp(12)));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(34), dp(34));
            ilp.setMarginEnd(dp(14));
            row.addView(ic, ilp);
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(Ui.text(this, p.name, 16, R.color.text, true));
            texts.addView(Ui.text(this, String.format(Locale.ROOT, "%.5f, %.5f · raio %d m", p.lat, p.lng, p.radius), 12, R.color.text2, false));
            row.addView(texts, Ui.lpWeight(1));
            row.addView(Ui.iconButton(this, R.drawable.ic_more, "Opções", new View.OnClickListener() { public void onClick(View v) { options(p); } }));
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (pick) { setResult(RESULT_OK, new Intent().putExtra("placeId", p.id)); finish(); }
                else edit(p);
            } });
            card.addView(row, Ui.lpMW());
        }
        list.addView(card);
    }

    private void options(final Db.Place p) {
        String[] items = {"Editar", "Ver no mapa", "Apagar"};
        new AlertDialog.Builder(this).setTitle(p.name).setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                if (w == 0) edit(p);
                else if (w == 1) showOnMap(p.lat, p.lng, p.name);
                else confirmDelete(p);
            }
        }).show();
    }

    private void showOnMap(double lat, double lng, String name) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + lat + "," + lng + "?q=" + lat + "," + lng + "(" + Uri.encode(name) + ")")));
        } catch (Exception e) { Toast.makeText(this, "Não há nenhuma app de mapas", Toast.LENGTH_SHORT).show(); }
    }

    private void confirmDelete(final Db.Place p) {
        int n = 0;
        for (pt.lembra.core.Reminder r : Db.get(this).all()) if (r.locPlace == p.id && !r.done) n++;
        String msg = "Apagar o local «" + p.name + "»?" + (n > 0 ? "\n\n" + n + (n == 1 ? " lembrete usa" : " lembretes usam") + " este local e passa" + (n == 1 ? "" : "m") + " a lembrete por hora (daqui a 1 hora)." : "");
        new AlertDialog.Builder(this).setMessage(msg)
                .setPositiveButton("Apagar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                    Db.get(PlacesActivity.this).deletePlace(p.id);
                    Engine.rescheduleAll(PlacesActivity.this);
                    load();
                } })
                .setNegativeButton("Cancelar", null).show();
    }

    // ------------------------------------------------------------------ Criar / editar

    private void edit(final Db.Place existing) {
        final Db.Place p = existing != null ? existing : new Db.Place();
        final double[] coords = {existing != null ? p.lat : Double.NaN, existing != null ? p.lng : Double.NaN};
        final int[] radius = {existing != null ? p.radius : 150};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        final EditText name = new EditText(this);
        name.setHint("Nome (ex.: Casa, Trabalho)");
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (existing != null) name.setText(p.name);
        box.addView(name, Ui.lpMW());

        final TextView where = Ui.text(this, "", 13, R.color.text2, false);
        where.setPadding(dp(2), dp(12), 0, dp(8));
        box.addView(where, Ui.lpMW());
        final Runnable updateWhere = new Runnable() { public void run() {
            where.setText(Double.isNaN(coords[0]) ? "Ainda sem localização definida." :
                    String.format(Locale.ROOT, "Localização: %.5f, %.5f", coords[0], coords[1]));
        } };
        updateWhere.run();

        TextView here = Ui.button(this, "📍  Usar a minha localização atual", false);
        here.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            where.setText("A obter localização…");
            currentLocation(new LocCallback() { public void on(Location l) {
                if (l == null) { where.setText("Não foi possível obter a localização. Ative o GPS e tente de novo."); return; }
                coords[0] = l.getLatitude(); coords[1] = l.getLongitude(); updateWhere.run();
            } });
        } });
        box.addView(here, Ui.lpMW());

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(0, dp(8), 0, 0);
        final EditText addr = new EditText(this);
        addr.setHint("ou procurar morada");
        addr.setSingleLine(true);
        searchRow.addView(addr, Ui.lpWeight(1));
        searchRow.addView(Ui.iconButton(this, R.drawable.ic_search, "Procurar morada", new View.OnClickListener() { public void onClick(View v) {
            final String q = addr.getText().toString().trim();
            if (q.isEmpty()) return;
            where.setText("A procurar…");
            searchAddress(q, new AddrCallback() { public void on(List<Address> res) {
                if (res == null || res.isEmpty()) { where.setText("Morada não encontrada. Tente ser mais específico ou use a localização atual."); return; }
                Address a = res.get(0);
                coords[0] = a.getLatitude(); coords[1] = a.getLongitude();
                updateWhere.run();
                where.append("\n" + a.getAddressLine(0));
                if (name.getText().toString().trim().isEmpty()) name.setText(q);
            } });
        } }));
        box.addView(searchRow, Ui.lpMW());

        TextView rl = Ui.subLabel(this, "Raio");
        rl.setPadding(dp(2), dp(14), 0, dp(8));
        box.addView(rl);
        final LinearLayout radii = new LinearLayout(this);
        final int[] opts = {100, 200, 500, 1000};
        final Runnable renderR = new Runnable() { public void run() {
            radii.removeAllViews();
            for (final int o : opts) {
                TextView c = Ui.chip(PlacesActivity.this, o < 1000 ? o + " m" : "1 km", radius[0] == o, 0);
                c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { radius[0] = o; run(); } });
                radii.addView(c);
            }
        } };
        if (radius[0] != 100 && radius[0] != 200 && radius[0] != 500 && radius[0] != 1000) radius[0] = 200;
        renderR.run();
        box.addView(radii);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        final AlertDialog dlg = new AlertDialog.Builder(this).setTitle(existing == null ? "Novo local" : "Editar local")
                .setView(sv).setPositiveButton("Guardar", null).setNegativeButton("Cancelar", null).create();
        dlg.show();
        if (existing == null && hasLocation()) here.performClick();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) { name.setError("Escreva um nome"); return; }
            if (Double.isNaN(coords[0])) { Toast.makeText(PlacesActivity.this, "Defina a localização (atual ou morada)", Toast.LENGTH_SHORT).show(); return; }
            p.name = n; p.lat = coords[0]; p.lng = coords[1]; p.radius = radius[0];
            long id = Db.get(PlacesActivity.this).savePlace(p);
            Engine.rescheduleAll(PlacesActivity.this);
            dlg.dismiss();
            if (pick) { setResult(RESULT_OK, new Intent().putExtra("placeId", id)); finish(); }
            else load();
        } });
    }

    // ------------------------------------------------------------------ Localização

    interface LocCallback { void on(Location l); }
    interface AddrCallback { void on(List<Address> res); }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("deprecation")
    private void currentLocation(final LocCallback cb) {
        if (!hasLocation()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 41);
            cb.on(null);
            return;
        }
        final LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        final boolean[] done = {false};
        final LocationListener listener = new LocationListener() {
            public void onLocationChanged(Location l) {
                if (done[0]) return;
                done[0] = true;
                try { lm.removeUpdates(this); } catch (Exception ignored) { }
                cb.on(l);
            }
            public void onStatusChanged(String p, int s, Bundle e) {}
            public void onProviderEnabled(String p) {}
            public void onProviderDisabled(String p) {}
        };
        try {
            for (String prov : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (lm.isProviderEnabled(prov)) lm.requestLocationUpdates(prov, 0, 0, listener, Looper.getMainLooper());
            }
        } catch (SecurityException e) { cb.on(null); return; }
        // Plano B ao fim de 12 s: última localização conhecida.
        handler.postDelayed(new Runnable() { public void run() {
            if (done[0]) return;
            done[0] = true;
            try { lm.removeUpdates(listener); } catch (Exception ignored) { }
            Location best = null;
            try {
                for (String prov : lm.getProviders(true)) {
                    Location l = lm.getLastKnownLocation(prov);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                }
            } catch (SecurityException ignored) { }
            cb.on(best);
        } }, 12000);
    }

    @SuppressWarnings("deprecation")
    private void searchAddress(final String q, final AddrCallback cb) {
        if (!Geocoder.isPresent()) { cb.on(null); return; }
        new Thread(new Runnable() { public void run() {
            List<Address> res = null;
            try { res = new Geocoder(PlacesActivity.this, new Locale("pt", "PT")).getFromLocationName(q, 3); } catch (Exception ignored) { }
            final List<Address> r = res;
            runOnUiThread(new Runnable() { public void run() { cb.on(r); } });
        } }).start();
    }
}
