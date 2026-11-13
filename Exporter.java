package com.mini.engine;

import android.content.Context;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
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

    public static void build(Context c, String json, File adir, Set<String> used,
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
                try (FileInputStream fi = new FileInputStream(f)) {
                    zo.write(readAll(fi));
                }
                zo.closeEntry();
            }
        }
        Sign.sign(c, unsigned, out);
        unsigned.delete();
    }
}
