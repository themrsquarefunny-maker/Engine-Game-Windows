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
