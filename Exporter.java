package com.mini.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Exporter {
    // Textos de relleno que están dentro de la plantilla (PlayerManifest.xml).
    // Se reemplazan por los valores reales con la MISMA longitud.
    static final String PKG_PH = "com.gx.placeholder00000000";
    static final String LABEL_PH = "LABELPLACEHOLDERXXXXXXXXXXXXXXXX";
    static final String VER_PH = "VPLACEHOLDER";

    static byte[] readAll(InputStream i) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = i.read(b)) > 0) o.write(b, 0, n);
        return o.toByteArray();
    }

    static String pad(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < n; i++) {
            char ch = s.charAt(i);
            sb.append(ch >= 32 && ch < 127 ? ch : '?');
        }
        while (sb.length() < n) sb.append(' ');
        return sb.toString();
    }

    static byte[] rep(byte[] d, String from, String to, String enc) throws Exception {
        byte[] a = from.getBytes(enc);
        byte[] b = to.getBytes(enc);
        if (a.length != b.length) throw new Exception("Longitud inválida al reemplazar");
        for (int i = 0; i + a.length <= d.length; i++) {
            boolean ok = true;
            for (int j = 0; j < a.length; j++) {
                if (d[i + j] != a[j]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                System.arraycopy(b, 0, d, i, b.length);
                i += a.length - 1;
            }
        }
        return d;
    }

    static byte[] patch(byte[] d, String pkg, String label, String ver) throws Exception {
        String lab = pad(label, LABEL_PH.length());
        String vv = pad(ver, VER_PH.length());
        d = rep(d, PKG_PH, pkg, "UTF-8");
        d = rep(d, LABEL_PH, lab, "UTF-8");
        d = rep(d, VER_PH, vv, "UTF-8");
        d = rep(d, PKG_PH, pkg, "UTF-16LE");
        d = rep(d, LABEL_PH, lab, "UTF-16LE");
        d = rep(d, VER_PH, vv, "UTF-16LE");
        return d;
    }

    static boolean isImg(String n) {
        n = n.toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp")
                || n.endsWith(".bmp");
    }

    // mode: 0 = WebP con pérdida mínima (85%), 1 = WebP sin pérdida, 2 = no tocar
    // Reduce la imagen si es más grande de lo que se usa en pantalla y la guarda solo si pesa menos.
    static byte[] imageBytes(File f, float[] sz, boolean noDown, int mode) throws Exception {
        byte[] raw;
        try (FileInputStream fi = new FileInputStream(f)) {
            raw = readAll(fi);
        }
        if (mode == 2 || !isImg(f.getName())) return raw;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return raw;
        Bitmap b = BitmapFactory.decodeByteArray(raw, 0, raw.length);
        if (b == null) return raw;
        if (!noDown && sz != null && sz[0] > 0 && sz[1] > 0) {
            float k = Math.min(1f, Math.max(sz[0] / o.outWidth, sz[1] / o.outHeight));
            if (k < 0.95f) {
                b = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(o.outWidth * k)),
                        Math.max(1, Math.round(o.outHeight * k)), true);
            }
        }
        Bitmap.CompressFormat fmt;
        int q;
        boolean api30 = Build.VERSION.SDK_INT >= 30;
        if (mode == 1) {
            fmt = api30 ? Bitmap.CompressFormat.WEBP_LOSSLESS : Bitmap.CompressFormat.WEBP;
            q = 100;
        } else {
            fmt = api30 ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
            q = 85;
        }
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        b.compress(fmt, q, bo);
        byte[] out = bo.toByteArray();
        return out.length > 0 && out.length < raw.length ? out : raw;
    }

    public static void build(Context c, String json, File adir, Set<String> used,
                             HashMap<String, float[]> sizes, boolean noDown, int imgMode,
                             String name, String ver, File out) throws Exception {
        byte[] tpl;
        try (InputStream i = c.getAssets().open("template.apk")) {
            tpl = readAll(i);
        }
        byte[] md = MessageDigest.getInstance("MD5").digest(name.getBytes("UTF-8"));
        StringBuilder hex = new StringBuilder();
        for (byte b : md) hex.append(String.format("%02x", b));
        String pkg = "com.gx.a" + hex.substring(0, PKG_PH.length() - 8);

        File unsigned = new File(c.getCacheDir(), "unsigned.apk");
        if (unsigned.exists()) unsigned.delete();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(tpl));
             ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(unsigned))) {
            zo.setLevel(9);
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String n = e.getName();
                if (e.isDirectory() || n.startsWith("META-INF/")) continue;
                byte[] data = readAll(zin);
                if (n.equals("AndroidManifest.xml")) data = patch(data, pkg, name, ver);
                zo.putNextEntry(new ZipEntry(n));
                zo.write(data);
                zo.closeEntry();
            }
            zo.putNextEntry(new ZipEntry("assets/game.json"));
            zo.write(json.getBytes("UTF-8"));
            zo.closeEntry();
            for (String a : used) {
                File f = new File(adir, a);
                if (!f.exists()) continue;
                zo.putNextEntry(new ZipEntry("assets/a/" + a));
                zo.write(imageBytes(f, sizes.get(a), noDown, imgMode));
                zo.closeEntry();
            }
        }
        Sign.sign(c, unsigned, out);
        unsigned.delete();
    }
}
