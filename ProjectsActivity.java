package com.mini.engine;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ProjectsActivity extends Activity {
    File root;
    LinearLayout list;
    File zipSource;

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF121212));
        root = new File(getFilesDir(), "projects");
        root.mkdirs();
        migrate();
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(28), dp(16), dp(24));
        sv.addView(col);
        TextView t = new TextView(this);
        t.setText("Mis proyectos");
        t.setTextSize(24);
        t.setPadding(0, 0, 0, dp(8));
        col.addView(t);
        Button nb = new Button(this);
        nb.setText("＋ Nuevo proyecto");
        nb.setOnClickListener(x -> newProject());
        col.addView(nb);
        Button tb = new Button(this);
        tb.setText("＋ Juego de prueba (2 escenas)");
        tb.setOnClickListener(x -> createSampleProject());
        col.addView(tb);
        Button ib = new Button(this);
        ib.setText("Importar proyecto (.zip)");
        ib.setOnClickListener(x -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, 2);
        });
        col.addView(ib);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // Convierte el proyecto de la versión 1 en el primer proyecto de la lista
    void migrate() {
        File old = new File(getFilesDir(), "project");
        File[] cur = root.listFiles();
        if (old.exists() && new File(old, "game.json").exists() && (cur == null || cur.length == 0)) {
            File dst = new File(root, "p_v1");
            if (old.renameTo(dst)) writeText(new File(dst, "name.txt"), "Mi proyecto");
        }
    }

    String readText(File f) {
        try (InputStream i = new FileInputStream(f)) {
            return new String(readAll(i), "UTF-8").trim();
        } catch (Exception e) {
            return "";
        }
    }

    void writeText(File f, String s) {
        try (OutputStream o = new FileOutputStream(f)) {
            o.write(s.getBytes("UTF-8"));
        } catch (Exception e) {
        }
    }

    static byte[] readAll(InputStream i) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = i.read(b)) > 0) o.write(b, 0, n);
        return o.toByteArray();
    }

    String nameOf(File d) {
        String s = readText(new File(d, "name.txt"));
        return s.isEmpty() ? d.getName() : s;
    }

    void refresh() {
        list.removeAllViews();
        File[] ps = root.listFiles();
        if (ps == null || ps.length == 0) {
            TextView e = new TextView(this);
            e.setText("\nAún no tienes proyectos. Toca ＋ Nuevo proyecto.");
            list.addView(e);
            return;
        }
        Arrays.sort(ps, new Comparator<File>() {
            public int compare(File a, File c) {
                return nameOf(a).compareToIgnoreCase(nameOf(c));
            }
        });
        for (final File p : ps) {
            if (!p.isDirectory()) continue;
            LinearLayout row = new LinearLayout(this);
            TextView t = new TextView(this);
            t.setText(nameOf(p));
            t.setTextSize(18);
            t.setPadding(dp(8), dp(16), dp(8), dp(16));
            t.setOnClickListener(x -> open(p));
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button m = new Button(this);
            m.setText("⋮");
            m.setOnClickListener(x -> menu(p));
            row.addView(m);
            list.addView(row);
        }
    }

    void open(File p) {
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra("pid", p.getName());
        startActivity(i);
    }

    void ask(String title, String def, final java.util.function.Consumer<String> cb) {
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setText(def);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(10), dp(20), 0);
        box.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setPositiveButton("Aceptar", (d, w) -> {
                    String s = e.getText().toString().trim();
                    if (!s.isEmpty()) cb.accept(s);
                }).setNegativeButton("Cancelar", null).show();
    }

    File create(String name) {
        File d = new File(root, "p" + System.currentTimeMillis());
        new File(d, "assets").mkdirs();
        writeText(new File(d, "name.txt"), name);
        return d;
    }

    void newProject() {
        ask("Nombre del proyecto", "Mi juego", s -> {
            File d = create(s);
            open(d);
        });
    }

    void writePngSquare(File f, int argb) throws Exception {
        android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888);
        bm.eraseColor(argb);
        try (OutputStream o = new FileOutputStream(f)) {
            bm.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o);
        }
        bm.recycle();
    }

    void createSampleProject() {
        try {
            File d = create("MiJuegoPrueba");
            File ad = new File(d, "assets");
            writePngSquare(new File(ad, "frame1.png"), 0xFFE53935);
            writePngSquare(new File(ad, "frame2.png"), 0xFF43A047);
            writePngSquare(new File(ad, "frame3.png"), 0xFF1E88E5);
            writePngSquare(new File(ad, "caja.png"), 0xFFFFB300);

            Game g = new Game(null);
            g.vwL = 640; g.vhL = 360; g.vwP = 360; g.vhP = 640;
            g.applyOrientation(true);
            g.leftClickTouch = true;

            Game.Scene s1 = g.scenes.get(0);
            s1.name = "Escena 1";

            Game.Node title = g.add("Label");
            title.name = "Titulo1";
            title.text = "Escena 1 - Toca el botón";
            title.x = 120; title.y = 30; title.w = 400; title.h = 60; title.tsize = 26;

            Game.Node anim1 = g.add("AnimatedSprite");
            anim1.name = "Sprite1";
            anim1.x = 272; anim1.y = 110; anim1.w = 96; anim1.h = 96;
            anim1.fps = 4f; anim1.loop = true; anim1.autoplay = true;
            anim1.frames.add("frame1.png");
            anim1.frames.add("frame2.png");
            anim1.frames.add("frame3.png");

            Game.Node btn1 = g.add("Button");
            btn1.name = "BotonIr";
            btn1.text = "Ir a Escena 2";
            btn1.x = 200; btn1.y = 240; btn1.w = 240; btn1.h = 70; btn1.tsize = 22;
            Game.Blk press1 = new Game.Blk("press");
            Game.Blk go2 = new Game.Blk("scene");
            go2.f.put("scene", "Escena 2");
            press1.body.add(go2);
            btn1.script.add(press1);

            Game.Scene s2 = g.addScene("Escena 2");
            g.openScene(s2);

            Game.Node title2 = g.add("Label");
            title2.name = "Titulo2";
            title2.text = "Escena 2 - Arrastra la caja";
            title2.x = 120; title2.y = 20; title2.w = 400; title2.h = 50; title2.tsize = 24;

            Game.Node anim2 = g.add("AnimatedSprite");
            anim2.name = "Sprite2";
            anim2.x = 60; anim2.y = 110; anim2.w = 96; anim2.h = 96;
            anim2.fps = 6f; anim2.loop = true; anim2.autoplay = true;
            anim2.frames.add("frame1.png");
            anim2.frames.add("frame2.png");
            anim2.frames.add("frame3.png");

            Game.Node box = g.add("Image");
            box.name = "CajaMovil";
            box.res = "caja.png";
            box.x = 300; box.y = 110; box.w = 100; box.h = 100;

            Game.Node grab = g.add("Grab");
            grab.name = "AgarreCaja";
            grab.parent = box.id;
            grab.x = 0; grab.y = 0; grab.w = 100; grab.h = 100;
            grab.gmode = "arrastrar";

            Game.Node btn2 = g.add("Button");
            btn2.name = "BotonVolver";
            btn2.text = "Volver a Escena 1";
            btn2.x = 200; btn2.y = 250; btn2.w = 240; btn2.h = 70; btn2.tsize = 20;
            Game.Blk press2 = new Game.Blk("press");
            Game.Blk go1 = new Game.Blk("scene");
            go1.f.put("scene", "Escena 1");
            press2.body.add(go1);
            btn2.script.add(press2);

            g.openScene(s1);
            writeText(new File(d, "game.json"), g.toJson());
            open(d);
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    void menu(final File p) {
        String[] items = {"Abrir", "Renombrar", "Duplicar", "Exportar (.zip)", "Eliminar"};
        new AlertDialog.Builder(this).setTitle(nameOf(p)).setItems(items, (d, w) -> {
            if (w == 0) {
                open(p);
            } else if (w == 1) {
                ask("Nuevo nombre", nameOf(p), s -> {
                    writeText(new File(p, "name.txt"), s);
                    refresh();
                });
            } else if (w == 2) {
                try {
                    File c = create(nameOf(p) + " (copia)");
                    copyDir(p, c);
                    writeText(new File(c, "name.txt"), nameOf(p) + " (copia)");
                    refresh();
                } catch (Exception e) {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else if (w == 3) {
                zipSource = p;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/zip");
                i.putExtra(Intent.EXTRA_TITLE, nameOf(p).replaceAll("[^A-Za-z0-9._-]", "_") + ".zip");
                startActivityForResult(i, 1);
            } else {
                new AlertDialog.Builder(this).setTitle("¿Eliminar " + nameOf(p) + "?")
                        .setMessage("Se borran su juego y sus assets.")
                        .setPositiveButton("Eliminar", (d2, w2) -> {
                            deleteDir(p);
                            deleteSharedPreferences("test_" + p.getName());
                            refresh();
                        }).setNegativeButton("Cancelar", null).show();
            }
        }).show();
    }

    void copyDir(File a, File b) throws Exception {
        b.mkdirs();
        File[] fs = a.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            File t = new File(b, f.getName());
            if (f.isDirectory()) {
                copyDir(f, t);
            } else {
                try (InputStream i = new FileInputStream(f); OutputStream o = new FileOutputStream(t)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
                }
            }
        }
    }

    void deleteDir(File d) {
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) {
            if (f.isDirectory()) deleteDir(f);
            else f.delete();
        }
        d.delete();
    }

    void zipDir(File d, String prefix, ZipOutputStream zo) throws Exception {
        File[] fs = d.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) {
                zipDir(f, prefix + f.getName() + "/", zo);
            } else {
                zo.putNextEntry(new ZipEntry(prefix + f.getName()));
                try (InputStream i = new FileInputStream(f)) {
                    zo.write(readAll(i));
                }
                zo.closeEntry();
            }
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (res != RESULT_OK || d == null || d.getData() == null) return;
        Uri u = d.getData();
        try {
            if (req == 1 && zipSource != null) {
                try (ZipOutputStream zo = new ZipOutputStream(getContentResolver().openOutputStream(u))) {
                    zipDir(zipSource, "", zo);
                }
                Toast.makeText(this, "Proyecto exportado ✔", Toast.LENGTH_SHORT).show();
            } else if (req == 2) {
                File dst = create("Proyecto importado");
                boolean ok = false;
                try (ZipInputStream zin = new ZipInputStream(getContentResolver().openInputStream(u))) {
                    ZipEntry e;
                    while ((e = zin.getNextEntry()) != null) {
                        String n = e.getName();
                        if (e.isDirectory()) continue;
                        File out = null;
                        if (n.equals("game.json")) {
                            out = new File(dst, "game.json");
                            ok = true;
                        } else if (n.equals("name.txt") || n.equals("icon.png")) {
                            out = new File(dst, n);
                        } else if (n.startsWith("assets/") && n.indexOf('/', 7) < 0
                                && n.substring(7).matches("[A-Za-z0-9._-]+")) {
                            out = new File(new File(dst, "assets"), n.substring(7));
                        }
                        if (out == null) continue;
                        try (OutputStream o = new FileOutputStream(out)) {
                            o.write(readAll(zin));
                        }
                    }
                }
                if (!ok) {
                    deleteDir(dst);
                    Toast.makeText(this, "El zip no es un proyecto del motor", Toast.LENGTH_LONG).show();
                } else {
                    refresh();
                    Toast.makeText(this, "Proyecto importado ✔", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
