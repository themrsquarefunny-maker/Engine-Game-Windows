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
    static final String PKG_PH = "com.gx.placeholder00000000000000000000000000000000000000000000000000000000000000";
    // plantilla antigua (26 letras): sigue funcionando, pero con paquetes de hasta 26 letras
    static final String PKG_OLD = "com.gx.placeholder00000000";
    static final String LABEL_PH = "LABELPLACEHOLDERXXXXXXXXXXXXXXXX";
    static final String VER_PH = "VPLACEHOLDERXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
    static final String VER_OLD = "VPLACEHOLDER";

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

    static boolean has(byte[] d, String s, String enc) throws Exception {
        byte[] a = s.getBytes(enc);
        for (int i = 0; i + a.length <= d.length; i++) {
            int j = 0;
            while (j < a.length && d[i + j] == a[j]) j++;
            if (j == a.length) return true;
        }
        return false;
    }

    // El texto puede ser más corto que el relleno: se acorta la cadena dentro del manifiesto
    // (longitud + texto + fin de cadena) y el resto queda en ceros; nada más cambia de lugar.
    static byte[] setStr(byte[] d, String phNew, String phOld, String v, boolean strict) throws Exception {
        String ph = has(d, phNew, "UTF-8") || has(d, phNew, "UTF-16LE") ? phNew : phOld;
        if (v.length() > ph.length()) {
            if (strict) throw new Exception("Paquete demasiado largo (máximo " + ph.length() + " letras)");
            v = v.substring(0, ph.length());
        }
        if (v.length() == ph.length()) {
            d = rep(d, ph, v, "UTF-8");
            return rep(d, ph, v, "UTF-16LE");
        }
        d = shrink(d, ph, v, false);
        return shrink(d, ph, v, true);
    }

    static byte[] shrink(byte[] d, String ph, String pkg, boolean u16) throws Exception {
        String enc = u16 ? "UTF-16LE" : "UTF-8";
        byte[] a = ph.getBytes(enc), b = pkg.getBytes(enc);
        for (int i = 2; i + a.length <= d.length; i++) {
            int j = 0;
            while (j < a.length && d[i + j] == a[j]) j++;
            if (j < a.length) continue;
            if (u16) {
                if (((d[i - 2] & 0xff) | ((d[i - 1] & 0xff) << 8)) != ph.length()) throw new Exception("Manifiesto inesperado");
                d[i - 2] = (byte) pkg.length();
                d[i - 1] = 0;
            } else {
                if ((d[i - 2] & 0xff) != ph.length() || (d[i - 1] & 0xff) != ph.length()) throw new Exception("Manifiesto inesperado");
                d[i - 2] = (byte) pkg.length();
                d[i - 1] = (byte) pkg.length();
            }
            System.arraycopy(b, 0, d, i, b.length);
            for (int k = i + b.length; k < i + a.length + (u16 ? 2 : 1); k++) d[k] = 0;
            i += a.length - 1;
        }
        return d;
    }

    static byte[] patch(byte[] d, String pkg, String label, String ver) throws Exception {
        String lab = pad(label, LABEL_PH.length());
        String vv = pad(ver, ver.length()).trim().isEmpty() ? "1.0" : pad(ver, ver.length()).trim();
        d = setStr(d, PKG_PH, PKG_OLD, pkg, true);
        d = setStr(d, VER_PH, VER_OLD, vv, false);
        d = rep(d, LABEL_PH, lab, "UTF-8");
        d = rep(d, LABEL_PH, lab, "UTF-16LE");
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
                             byte[] icon, String name, String ver, String pkgIn, File out) throws Exception {
        byte[] tpl;
        try (InputStream i = c.getAssets().open("template.apk")) {
            tpl = readAll(i);
        }
        byte[] md = MessageDigest.getInstance("MD5").digest(name.getBytes("UTF-8"));
        StringBuilder hex = new StringBuilder();
        for (byte b : md) hex.append(String.format("%02x", b));
        String pkg = pkgIn != null && !pkgIn.isEmpty() ? pkgIn : "com.gx.a" + hex.substring(0, PKG_OLD.length() - 8);

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
                if (icon != null && n.startsWith("res/") && n.endsWith("/ic.png")) data = icon;
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
