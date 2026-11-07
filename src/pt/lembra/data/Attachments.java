package pt.lembra.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

/** Ficheiros anexos: copiados para a área privada da app (ficam mesmo que o original seja apagado). */
public final class Attachments {
    private Attachments() {}

    public static final String AUTHORITY = "pt.lembra.files";

    public static File dir(Context ctx) {
        File d = new File(ctx.getFilesDir(), "att");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File file(Context ctx, String name) { return new File(dir(ctx), name); }

    /** URI partilhável (via FilesProvider) para abrir o anexo noutra app. */
    public static Uri uri(String name) { return Uri.parse("content://" + AUTHORITY + "/" + Uri.encode(name)); }

    public static boolean isImage(Db.Att a) { return a.mime != null && a.mime.startsWith("image/"); }

    /** Copia um ficheiro escolhido pelo utilizador. */
    public static Db.Att importUri(Context ctx, Uri src) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        String display = "anexo";
        Cursor c = null;
        try {
            c = cr.query(src, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst() && !c.isNull(0)) display = c.getString(0);
        } catch (Exception ignored) { } finally { if (c != null) c.close(); }
        String mime = cr.getType(src);
        if (mime == null) mime = guessMime(display);
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        String name = System.currentTimeMillis() + "_" + Math.abs(display.hashCode()) + (ext != null ? "." + ext : "");
        InputStream in = cr.openInputStream(src);
        OutputStream out = new FileOutputStream(file(ctx, name));
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally { in.close(); out.close(); }
        Db.Att a = new Db.Att();
        a.file = name; a.name = display; a.mime = mime;
        return a;
    }

    /** Nome de ficheiro para uma foto tirada com a câmara. */
    public static String newPhotoName() { return "foto_" + System.currentTimeMillis() + ".jpg"; }

    public static String guessMime(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return m != null ? m : "application/octet-stream";
    }

    /** Miniatura reduzida (evita carregar fotos enormes em memória). */
    public static Bitmap thumbnail(Context ctx, String name, int sizePx) {
        File f = file(ctx, name);
        if (!f.exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= sizePx && o.outHeight / (sample * 2) >= sizePx) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
    }

    public static void deleteFilesFor(Context ctx, long reminderId) {
        List<Db.Att> list = Db.get(ctx).attachments(reminderId);
        for (Db.Att a : list) file(ctx, a.file).delete();
    }

    /** Apaga ficheiros que já não pertencem a nenhum lembrete. */
    public static void cleanOrphans(Context ctx) {
        Set<String> used = Db.get(ctx).allAttachmentFiles();
        File[] files = dir(ctx).listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - 24 * 3600000L; // dá tempo a edições em curso
        for (File f : files) if (!used.contains(f.getName()) && f.lastModified() < cutoff) f.delete();
    }
}
