package pt.lembra.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import pt.lembra.core.Categories;
import pt.lembra.core.Reminder;

/** Persistência local em SQLite. */
public class Db extends SQLiteOpenHelper {
    private static Db instance;

    public static synchronized Db get(Context ctx) {
        if (instance == null) {
            instance = new Db(ctx.getApplicationContext());
            instance.loadCategories();
        }
        return instance;
    }

    private Db(Context ctx) { super(ctx, "lembra.db", null, 5); }

    private static final String[] COLS = {"id", "title", "notes", "due_at", "snoozed_until", "repeat_type", "interval_n",
            "interval_unit", "week_mask", "month_day", "end_type", "end_date", "end_count", "cnt", "category",
            "important", "pre_alert", "done", "done_at", "created_at", "notified", "pre_notified", "alarm",
            "loc_place", "loc_trigger", "loc_repeat", "done_auth"};

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE reminders (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, notes TEXT, " +
                "due_at INTEGER, snoozed_until INTEGER DEFAULT 0, repeat_type INTEGER DEFAULT 0, interval_n INTEGER DEFAULT 1, " +
                "interval_unit INTEGER DEFAULT 2, week_mask INTEGER DEFAULT 0, month_day INTEGER DEFAULT 0, end_type INTEGER DEFAULT 0, " +
                "end_date INTEGER DEFAULT 0, end_count INTEGER DEFAULT 0, cnt INTEGER DEFAULT 0, category INTEGER DEFAULT 0, " +
                "important INTEGER DEFAULT 0, pre_alert INTEGER DEFAULT 0, done INTEGER DEFAULT 0, done_at INTEGER DEFAULT 0, " +
                "created_at INTEGER DEFAULT 0, notified INTEGER DEFAULT 0, pre_notified INTEGER DEFAULT 0, alarm INTEGER DEFAULT 1, " +
                "loc_place INTEGER DEFAULT 0, loc_trigger INTEGER DEFAULT 1, loc_repeat INTEGER DEFAULT 0, done_auth INTEGER DEFAULT 0)");
        createCategories(db);
        createV4(db);
    }

    /** v4: subtarefas, histórico, locais e anexos. */
    private static void createV4(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS subtasks (id INTEGER PRIMARY KEY AUTOINCREMENT, reminder_id INTEGER, text TEXT, done INTEGER DEFAULT 0, sort INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS history (id INTEGER PRIMARY KEY AUTOINCREMENT, reminder_id INTEGER, title TEXT, category INTEGER, action INTEGER, at INTEGER, scheduled_at INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS places (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, lat REAL, lng REAL, radius INTEGER DEFAULT 150)");
        db.execSQL("CREATE TABLE IF NOT EXISTS attachments (id INTEGER PRIMARY KEY AUTOINCREMENT, reminder_id INTEGER, file TEXT, name TEXT, mime TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sub ON subtasks(reminder_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_hist ON history(at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_att ON attachments(reminder_id)");
    }

    private static void createCategories(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS categories (id INTEGER PRIMARY KEY, name TEXT NOT NULL, color INTEGER, sort INTEGER)");
        for (Categories.Cat c : Categories.defaults()) {
            ContentValues v = new ContentValues();
            v.put("id", c.id); v.put("name", c.name); v.put("color", c.color); v.put("sort", c.sort);
            db.insertWithOnConflict("categories", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    /** Migrações: preservam sempre os dados existentes ao atualizar a app. */
    @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 2) db.execSQL("ALTER TABLE reminders ADD COLUMN alarm INTEGER DEFAULT 1");
        if (o < 3) createCategories(db); // categorias editáveis; mantém as 7 originais com os mesmos ids
        if (o < 4) {
            db.execSQL("ALTER TABLE reminders ADD COLUMN loc_place INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE reminders ADD COLUMN loc_trigger INTEGER DEFAULT 1");
            db.execSQL("ALTER TABLE reminders ADD COLUMN loc_repeat INTEGER DEFAULT 0");
            createV4(db);
        }
        if (o < 5) db.execSQL("ALTER TABLE reminders ADD COLUMN done_auth INTEGER DEFAULT 0");
    }

    private static Reminder read(Cursor c) {
        Reminder r = new Reminder();
        r.id = c.getLong(0); r.title = c.getString(1); r.notes = c.isNull(2) ? "" : c.getString(2);
        r.dueAt = c.getLong(3); r.snoozedUntil = c.getLong(4); r.repeatType = c.getInt(5); r.interval = c.getInt(6);
        r.intervalUnit = c.getInt(7); r.weekMask = c.getInt(8); r.monthDay = c.getInt(9); r.endType = c.getInt(10);
        r.endDate = c.getLong(11); r.endCount = c.getInt(12); r.count = c.getInt(13); r.category = c.getInt(14);
        r.important = c.getInt(15) != 0; r.preAlertMin = c.getInt(16); r.done = c.getInt(17) != 0; r.doneAt = c.getLong(18);
        r.createdAt = c.getLong(19); r.notified = c.getInt(20) != 0; r.preNotified = c.getInt(21) != 0; r.alarm = c.getInt(22) != 0;
        r.locPlace = c.getLong(23); r.locTrigger = c.getInt(24); r.locRepeat = c.getInt(25) != 0;
        r.doneAuth = Math.max(0, Math.min(2, c.getInt(26)));
        return r;
    }

    private static ContentValues values(Reminder r) {
        ContentValues v = new ContentValues();
        v.put("title", r.title); v.put("notes", r.notes); v.put("due_at", r.dueAt); v.put("snoozed_until", r.snoozedUntil);
        v.put("repeat_type", r.repeatType); v.put("interval_n", r.interval); v.put("interval_unit", r.intervalUnit);
        v.put("week_mask", r.weekMask); v.put("month_day", r.monthDay); v.put("end_type", r.endType); v.put("end_date", r.endDate);
        v.put("end_count", r.endCount); v.put("cnt", r.count); v.put("category", r.category); v.put("important", r.important ? 1 : 0);
        v.put("pre_alert", r.preAlertMin); v.put("done", r.done ? 1 : 0); v.put("done_at", r.doneAt); v.put("created_at", r.createdAt);
        v.put("notified", r.notified ? 1 : 0); v.put("pre_notified", r.preNotified ? 1 : 0); v.put("alarm", r.alarm ? 1 : 0);
        v.put("loc_place", r.locPlace); v.put("loc_trigger", r.locTrigger); v.put("loc_repeat", r.locRepeat ? 1 : 0);
        v.put("done_auth", r.doneAuth);
        return v;
    }

    public synchronized List<Reminder> all() {
        List<Reminder> out = new ArrayList<Reminder>();
        Cursor c = getReadableDatabase().query("reminders", COLS, null, null, null, null, "done ASC, due_at ASC");
        try { while (c.moveToNext()) out.add(read(c)); } finally { c.close(); }
        return out;
    }

    public synchronized Reminder get(long id) {
        Cursor c = getReadableDatabase().query("reminders", COLS, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        try { return c.moveToFirst() ? read(c) : null; } finally { c.close(); }
    }

    /** Insere (id == 0) ou atualiza. Devolve o id. */
    public synchronized long save(Reminder r) {
        SQLiteDatabase db = getWritableDatabase();
        if (r.createdAt == 0) r.createdAt = System.currentTimeMillis();
        ContentValues v = values(r);
        if (r.id > 0) {
            int n = db.update("reminders", v, "id=?", new String[]{String.valueOf(r.id)});
            if (n == 0) { v.put("id", r.id); db.insert("reminders", null, v); }
        } else {
            r.id = db.insert("reminders", null, v);
        }
        return r.id;
    }

    public synchronized void delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        String[] a = {String.valueOf(id)};
        db.delete("reminders", "id=?", a);
        db.delete("subtasks", "reminder_id=?", a);
        db.delete("attachments", "reminder_id=?", a);
    }

    // ---------- Subtarefas ----------

    public static final class Sub { public long id; public long reminderId; public String text; public boolean done; public int sort; }

    public synchronized List<Sub> subtasks(long reminderId) {
        List<Sub> out = new ArrayList<Sub>();
        Cursor c = getReadableDatabase().query("subtasks", new String[]{"id", "reminder_id", "text", "done", "sort"},
                "reminder_id=?", new String[]{String.valueOf(reminderId)}, null, null, "sort, id");
        try {
            while (c.moveToNext()) {
                Sub s = new Sub(); s.id = c.getLong(0); s.reminderId = c.getLong(1); s.text = c.getString(2); s.done = c.getInt(3) != 0; s.sort = c.getInt(4);
                out.add(s);
            }
        } finally { c.close(); }
        return out;
    }

    /** Substitui a checklist de um lembrete. */
    public synchronized void saveSubtasks(long reminderId, List<Sub> subs) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("subtasks", "reminder_id=?", new String[]{String.valueOf(reminderId)});
            for (int i = 0; i < subs.size(); i++) {
                ContentValues v = new ContentValues();
                v.put("reminder_id", reminderId); v.put("text", subs.get(i).text); v.put("done", subs.get(i).done ? 1 : 0); v.put("sort", i);
                db.insert("subtasks", null, v);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized void setSubDone(long subId, boolean done) {
        ContentValues v = new ContentValues(); v.put("done", done ? 1 : 0);
        getWritableDatabase().update("subtasks", v, "id=?", new String[]{String.valueOf(subId)});
    }

    /** Desmarca todos os itens (nova ocorrência de um recorrente). */
    public synchronized void resetSubtasks(long reminderId) {
        ContentValues v = new ContentValues(); v.put("done", 0);
        getWritableDatabase().update("subtasks", v, "reminder_id=?", new String[]{String.valueOf(reminderId)});
    }

    /** [feitos, total] por lembrete. */
    public synchronized java.util.Map<Long, int[]> subtaskProgress() {
        java.util.Map<Long, int[]> m = new java.util.HashMap<Long, int[]>();
        Cursor c = getReadableDatabase().rawQuery("SELECT reminder_id, SUM(done), COUNT(*) FROM subtasks GROUP BY reminder_id", null);
        try { while (c.moveToNext()) m.put(c.getLong(0), new int[]{c.getInt(1), c.getInt(2)}); } finally { c.close(); }
        return m;
    }

    // ---------- Histórico ----------

    public static final int H_DONE = 1, H_SNOOZE = 2, H_MISSED = 3;

    public static final class Hist { public long reminderId; public String title; public int category; public int action; public long at; public long scheduledAt; }

    public synchronized void log(Reminder r, int action) {
        ContentValues v = new ContentValues();
        v.put("reminder_id", r.id); v.put("title", r.title); v.put("category", r.category); v.put("action", action);
        v.put("at", System.currentTimeMillis()); v.put("scheduled_at", r.effectiveTime());
        getWritableDatabase().insert("history", null, v);
    }

    public synchronized List<Hist> history(long since) {
        List<Hist> out = new ArrayList<Hist>();
        Cursor c = getReadableDatabase().query("history", new String[]{"reminder_id", "title", "category", "action", "at", "scheduled_at"},
                "at>=?", new String[]{String.valueOf(since)}, null, null, "at DESC");
        try {
            while (c.moveToNext()) {
                Hist h = new Hist(); h.reminderId = c.getLong(0); h.title = c.getString(1); h.category = c.getInt(2);
                h.action = c.getInt(3); h.at = c.getLong(4); h.scheduledAt = c.getLong(5);
                out.add(h);
            }
        } finally { c.close(); }
        return out;
    }

    public synchronized void clearHistory() { getWritableDatabase().delete("history", null, null); }

    // ---------- Locais ----------

    public static final class Place { public long id; public String name; public double lat, lng; public int radius = 150; }

    public synchronized List<Place> places() {
        List<Place> out = new ArrayList<Place>();
        Cursor c = getReadableDatabase().query("places", new String[]{"id", "name", "lat", "lng", "radius"}, null, null, null, null, "name");
        try {
            while (c.moveToNext()) {
                Place p = new Place(); p.id = c.getLong(0); p.name = c.getString(1); p.lat = c.getDouble(2); p.lng = c.getDouble(3); p.radius = c.getInt(4);
                out.add(p);
            }
        } finally { c.close(); }
        return out;
    }

    public synchronized Place place(long id) {
        for (Place p : places()) if (p.id == id) return p;
        return null;
    }

    public synchronized long savePlace(Place p) {
        ContentValues v = new ContentValues();
        v.put("name", p.name); v.put("lat", p.lat); v.put("lng", p.lng); v.put("radius", p.radius);
        if (p.id > 0) getWritableDatabase().update("places", v, "id=?", new String[]{String.valueOf(p.id)});
        else p.id = getWritableDatabase().insert("places", null, v);
        return p.id;
    }

    /** Apaga o local; os lembretes que o usavam passam a lembretes por hora (daqui a 1 h). */
    public synchronized void deletePlace(long id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("loc_place", 0);
        v.put("due_at", System.currentTimeMillis() + 3600000L);
        db.update("reminders", v, "loc_place=?", new String[]{String.valueOf(id)});
        db.delete("places", "id=?", new String[]{String.valueOf(id)});
    }

    // ---------- Anexos ----------

    public static final class Att { public long id; public long reminderId; public String file; public String name; public String mime; }

    public synchronized List<Att> attachments(long reminderId) {
        List<Att> out = new ArrayList<Att>();
        Cursor c = getReadableDatabase().query("attachments", new String[]{"id", "reminder_id", "file", "name", "mime"},
                "reminder_id=?", new String[]{String.valueOf(reminderId)}, null, null, "id");
        try {
            while (c.moveToNext()) {
                Att a = new Att(); a.id = c.getLong(0); a.reminderId = c.getLong(1); a.file = c.getString(2); a.name = c.getString(3); a.mime = c.getString(4);
                out.add(a);
            }
        } finally { c.close(); }
        return out;
    }

    public synchronized void saveAttachments(long reminderId, List<Att> atts) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("attachments", "reminder_id=?", new String[]{String.valueOf(reminderId)});
            for (Att a : atts) {
                ContentValues v = new ContentValues();
                v.put("reminder_id", reminderId); v.put("file", a.file); v.put("name", a.name); v.put("mime", a.mime);
                db.insert("attachments", null, v);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized java.util.Map<Long, Integer> attachmentCounts() {
        java.util.Map<Long, Integer> m = new java.util.HashMap<Long, Integer>();
        Cursor c = getReadableDatabase().rawQuery("SELECT reminder_id, COUNT(*) FROM attachments GROUP BY reminder_id", null);
        try { while (c.moveToNext()) m.put(c.getLong(0), c.getInt(1)); } finally { c.close(); }
        return m;
    }

    /** Todos os ficheiros referenciados (para limpar ficheiros órfãos). */
    public synchronized java.util.Set<String> allAttachmentFiles() {
        java.util.Set<String> s = new java.util.HashSet<String>();
        Cursor c = getReadableDatabase().rawQuery("SELECT file FROM attachments", null);
        try { while (c.moveToNext()) s.add(c.getString(0)); } finally { c.close(); }
        return s;
    }

    public synchronized List<Reminder> doneList() {
        List<Reminder> out = new ArrayList<Reminder>();
        for (Reminder r : all()) if (r.done) out.add(r);
        return out;
    }

    // ---------- Backup ----------

    // ---------- Categorias ----------

    public synchronized void loadCategories() {
        List<Categories.Cat> out = new ArrayList<Categories.Cat>();
        Cursor c = getReadableDatabase().query("categories", new String[]{"id", "name", "color", "sort"}, null, null, null, null, "sort, id");
        try { while (c.moveToNext()) out.add(new Categories.Cat(c.getInt(0), c.getString(1), c.getInt(2), c.getInt(3))); } finally { c.close(); }
        if (out.isEmpty()) out = Categories.defaults();
        Categories.set(out);
    }

    /** Cria (id &lt; 0) ou atualiza uma categoria. */
    public synchronized int saveCategory(Categories.Cat cat) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("name", cat.name); v.put("color", cat.color); v.put("sort", cat.sort);
        if (cat.id < 0) {
            Cursor c = db.rawQuery("SELECT COALESCE(MAX(id), 0) + 1, COALESCE(MAX(sort), 0) + 1 FROM categories", null);
            try { c.moveToFirst(); cat.id = c.getInt(0); if (cat.sort <= 0) cat.sort = c.getInt(1); } finally { c.close(); }
            v.put("id", cat.id); v.put("sort", cat.sort);
            db.insert("categories", null, v);
        } else {
            db.update("categories", v, "id=?", new String[]{String.valueOf(cat.id)});
        }
        loadCategories();
        return cat.id;
    }

    /** Apaga a categoria; os lembretes dela passam para "Geral". */
    public synchronized void deleteCategory(int id) {
        if (id == Categories.GENERAL) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("category", Categories.GENERAL);
            db.update("reminders", v, "category=?", new String[]{String.valueOf(id)});
            db.delete("categories", "id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        loadCategories();
    }

    public synchronized void saveCategoryOrder(List<Categories.Cat> ordered) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < ordered.size(); i++) {
                ContentValues v = new ContentValues();
                v.put("sort", i);
                db.update("categories", v, "id=?", new String[]{String.valueOf(ordered.get(i).id)});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        loadCategories();
    }

    public synchronized int countInCategory(int id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM reminders WHERE category=?", new String[]{String.valueOf(id)});
        try { c.moveToFirst(); return c.getInt(0); } finally { c.close(); }
    }

    public synchronized String exportJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "Lembra");
        root.put("version", 1);
        root.put("exportedAt", System.currentTimeMillis());
        JSONArray arr = new JSONArray();
        Cursor c = getReadableDatabase().query("reminders", COLS, null, null, null, null, "id");
        try {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                for (int i = 0; i < COLS.length; i++) {
                    if (c.getType(i) == Cursor.FIELD_TYPE_STRING) o.put(COLS[i], c.getString(i));
                    else if (!c.isNull(i)) o.put(COLS[i], c.getLong(i));
                }
                arr.put(o);
            }
        } finally { c.close(); }
        root.put("reminders", arr);
        JSONArray cats = new JSONArray();
        for (Categories.Cat ct : Categories.all()) {
            JSONObject o = new JSONObject();
            o.put("id", ct.id); o.put("name", ct.name); o.put("color", ct.color); o.put("sort", ct.sort);
            cats.put(o);
        }
        root.put("categories", cats);
        JSONArray subs = new JSONArray();
        Cursor sc = getReadableDatabase().rawQuery("SELECT reminder_id, text, done, sort FROM subtasks", null);
        try { while (sc.moveToNext()) { JSONObject o = new JSONObject(); o.put("r", sc.getLong(0)); o.put("t", sc.getString(1)); o.put("d", sc.getInt(2)); o.put("s", sc.getInt(3)); subs.put(o); } } finally { sc.close(); }
        root.put("subtasks", subs);
        JSONArray pls = new JSONArray();
        for (Place p : places()) { JSONObject o = new JSONObject(); o.put("id", p.id); o.put("name", p.name); o.put("lat", p.lat); o.put("lng", p.lng); o.put("radius", p.radius); pls.put(o); }
        root.put("places", pls);
        return root.toString(2);
    }

    /** Importa um backup, substituindo os lembretes existentes. Devolve o número importado. */
    public synchronized int importJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray arr = root.getJSONArray("reminders");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("reminders", null, null);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                ContentValues v = new ContentValues();
                for (String col : COLS) {
                    if (!o.has(col)) continue;
                    if (col.equals("title") || col.equals("notes")) v.put(col, o.getString(col));
                    else v.put(col, o.getLong(col));
                }
                if (!v.containsKey("title")) continue;
                v.put("notified", 0);
                db.insert("reminders", null, v);
            }
            JSONArray subs = root.optJSONArray("subtasks");
            db.delete("subtasks", null, null);
            if (subs != null) for (int i = 0; i < subs.length(); i++) {
                JSONObject o = subs.getJSONObject(i);
                ContentValues v = new ContentValues();
                v.put("reminder_id", o.getLong("r")); v.put("text", o.getString("t")); v.put("done", o.optInt("d")); v.put("sort", o.optInt("s"));
                db.insert("subtasks", null, v);
            }
            JSONArray pls = root.optJSONArray("places");
            if (pls != null) {
                db.delete("places", null, null);
                for (int i = 0; i < pls.length(); i++) {
                    JSONObject o = pls.getJSONObject(i);
                    ContentValues v = new ContentValues();
                    v.put("id", o.getLong("id")); v.put("name", o.getString("name")); v.put("lat", o.getDouble("lat"));
                    v.put("lng", o.getDouble("lng")); v.put("radius", o.optInt("radius", 150));
                    db.insert("places", null, v);
                }
            }
            JSONArray cats = root.optJSONArray("categories");
            if (cats != null && cats.length() > 0) {
                db.delete("categories", null, null);
                for (int i = 0; i < cats.length(); i++) {
                    JSONObject o = cats.getJSONObject(i);
                    ContentValues v = new ContentValues();
                    v.put("id", o.getInt("id")); v.put("name", o.getString("name"));
                    v.put("color", o.getInt("color")); v.put("sort", o.optInt("sort", i));
                    db.insertWithOnConflict("categories", null, v, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        loadCategories();
        return arr.length();
    }
}
