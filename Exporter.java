package com.mini.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Exporter {
    static final String WIN_TEMPLATE_ASSET = "win_player.zip";

    static byte[] readAll(InputStream i) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = i.read(b)) > 0) o.write(b, 0, n);
        return o.toByteArray();
    }

    static boolean isImg(String n) {
        n = n.toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".webp") || n.endsWith(".bmp") || n.endsWith(".gif");
    }

    static boolean isWebp(String n) {
        return n.toLowerCase(Locale.ROOT).endsWith(".webp");
    }

    public static String safeExeBase(String name) {
        if (name == null) return "MiJuego";
        String clean = name.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "");
        if (clean.isEmpty()) clean = name.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (clean.isEmpty()) return "MiJuego";
        return clean;
    }

    // mode: 0 = Optimizar (PNG/JPEG más liviano), 1 = Sin pérdida (PNG), 2 = No tocar (convierte WebP a PNG)
    static byte[] imageBytesForWindows(File f, float[] sz, boolean noDown, int mode) throws Exception {
        byte[] raw;
        try (FileInputStream fi = new FileInputStream(f)) {
            raw = readAll(fi);
        }
        String name = f.getName();
        if (!isImg(name)) return raw;

        boolean webp = isWebp(name);
        if (mode == 2 && !webp) return raw;

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return raw;

        Bitmap b = BitmapFactory.decodeByteArray(raw, 0, raw.length);
        if (b == null) return raw;

        boolean resized = false;
        if (mode != 2 && !noDown && sz != null && sz[0] > 0 && sz[1] > 0) {
            float k = Math.min(1f, Math.max(sz[0] / o.outWidth, sz[1] / o.outHeight));
            if (k < 0.95f) {
                b = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(o.outWidth * k)),
                        Math.max(1, Math.round(o.outHeight * k)), true);
                resized = true;
            }
        }

        ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, pngOut);
        byte[] best = pngOut.toByteArray();

        if (mode == 0 && !b.hasAlpha()) {
            ByteArrayOutputStream jpgOut = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 88, jpgOut);
            byte[] jb = jpgOut.toByteArray();
            if (jb.length > 0 && (best.length == 0 || jb.length < best.length)) {
                best = jb;
            }
        }

        if (webp || resized) return best.length > 0 ? best : raw;
        return best.length > 0 && best.length < raw.length ? best : raw;
    }

    static class PakItem {
        final String path;
        final byte[] data;

        PakItem(String path, byte[] data) {
            this.path = path;
            this.data = data;
        }
    }

    static void writePak(List<PakItem> items, File out) throws Exception {
        int count = items.size();
        byte[][] nameBytes = new byte[count][];
        int tocSize = 12; // "GEPK" + version(4) + count(4)
        for (int i = 0; i < count; i++) {
            nameBytes[i] = items.get(i).path.getBytes("UTF-8");
            tocSize += 2 + nameBytes[i].length + 8 + 8;
        }

        ByteBuffer header = ByteBuffer.allocate(tocSize).order(ByteOrder.LITTLE_ENDIAN);
        header.put((byte) 'G').put((byte) 'E').put((byte) 'P').put((byte) 'K');
        header.putInt(1);
        header.putInt(count);

        long offset = tocSize;
        for (int i = 0; i < count; i++) {
            byte[] nb = nameBytes[i];
            byte[] data = items.get(i).data;
            header.putShort((short) nb.length);
            header.put(nb);
            header.putLong(offset);
            header.putLong(data.length);
            offset += data.length;
        }

        try (FileOutputStream fo = new FileOutputStream(out)) {
            fo.write(header.array());
            for (int i = 0; i < count; i++) {
                fo.write(items.get(i).data);
            }
        }
    }

    public static void buildWindows(Context c, String json, File adir, Set<String> used,
                                    HashMap<String, float[]> sizes, boolean noDown, int imgMode,
                                    byte[] icon, String name, File outZip) throws Exception {
        ArrayList<PakItem> pakItems = new ArrayList<PakItem>();
        pakItems.add(new PakItem("game.json", json.getBytes("UTF-8")));
        if (icon != null && icon.length > 0) {
            pakItems.add(new PakItem("icon.png", icon));
        }
        for (String a : used) {
            File f = new File(adir, a);
            if (!f.exists()) continue;
            pakItems.add(new PakItem("a/" + a, imageBytesForWindows(f, sizes.get(a), noDown, imgMode)));
        }

        File pakFile = new File(c.getCacheDir(), "juego.pak");
        if (pakFile.exists()) pakFile.delete();
        writePak(pakItems, pakFile);

        String exeName = safeExeBase(name) + ".exe";
        if (outZip.exists()) outZip.delete();

        boolean hasExe = false;
        try (InputStream tplStream = c.getAssets().open(WIN_TEMPLATE_ASSET);
             ZipInputStream zin = new ZipInputStream(tplStream);
             ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(outZip))) {
            zo.setLevel(6);
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String entryName = e.getName();
                int slash = entryName.lastIndexOf('/');
                String base = slash >= 0 ? entryName.substring(slash + 1) : entryName;
                if (base.isEmpty()) continue;

                String outName = base.equalsIgnoreCase("player.exe") ? exeName : base;
                if (base.equalsIgnoreCase("player.exe")) hasExe = true;

                zo.putNextEntry(new ZipEntry(outName));
                byte[] buf = new byte[16384];
                int n;
                while ((n = zin.read(buf)) > 0) zo.write(buf, 0, n);
                zo.closeEntry();
            }
            if (!hasExe) {
                throw new Exception("La plantilla de Windows (win_player.zip) no contiene player.exe");
            }

            zo.putNextEntry(new ZipEntry("juego.pak"));
            try (InputStream pi = new FileInputStream(pakFile)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = pi.read(buf)) > 0) zo.write(buf, 0, n);
            }
            zo.closeEntry();
        } finally {
            pakFile.delete();
        }
    }
}
