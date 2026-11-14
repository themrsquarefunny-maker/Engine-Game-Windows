package com.mini.engine;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class EditorActivity extends Activity {
    interface Str {
        void set(String s);
    }

    interface Pick {
        void got(String s);
    }

    static final int NUM = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
            | InputType.TYPE_NUMBER_FLAG_SIGNED;
    static final int TXT = InputType.TYPE_CLASS_TEXT;
    static final int MP = ViewGroup.LayoutParams.MATCH_PARENT;
    static final String[] PROPS = {"x", "y", "w", "h", "modulate", "visible", "text", "fps", "frame"};

    // tipo, etiqueta, E (evento) / S (bloque), campos "clave:etiqueta:tipo[:opciones]"
    // tipos: T texto, N número, V valor (con selector), R variable, M mensaje, B sí/no,
    //        G nodo, I imagen, A audio, O opciones
    static final String[][] SPEC = {
        {"start", "Al iniciar", "E"},
        {"press", "Al presionar", "E"},
        {"hold", "Al mantener presionado", "E"},
        {"recv", "Recibir mensaje", "E", "msg:Mensaje:M"},
        {"frame", "Cada fotograma", "E"},
        {"every", "Cada X segundos", "E", "secs:Segundos:N"},
        {"change", "Al cambiar una propiedad", "E", "node:Nodo:G", "prop:Propiedad:O:visible|modulate|x|y|w|h|text"},
        {"send", "Transmitir mensaje", "S", "msg:Mensaje:M"},
        {"if", "Si", "S", "l:Valor izquierdo:V", "op:Operador:O:=|!=|<|>|<=|>=",
            "r:Valor derecho (vacío = solo comprobar verdadero):V", "not:Not (negar la condición):B"},
        {"elif", "Sino, Si", "S", "l:Valor izquierdo:V", "op:Operador:O:=|!=|<|>|<=|>=",
            "r:Valor derecho (vacío = solo comprobar verdadero):V", "not:Not (negar la condición):B"},
        {"else", "Sino", "S"},
        {"while", "Repetir mientras", "S", "l:Valor izquierdo:V", "op:Operador:O:=|!=|<|>|<=|>=",
            "r:Valor derecho:V", "not:Not (negar la condición):B"},
        {"repeat", "Repetir (veces)", "S", "n:Veces:V"},
        {"forever", "Repetir siempre", "S"},
        {"break", "Romper (salir del bucle)", "S"},
        {"wait", "Esperar (segundos)", "S", "secs:Segundos:N"},
        {"var", "Variable", "S", "name:Nombre:R", "vtype:Tipo:O:int|float|bool|string", "val:Valor (=):V"},
        {"math", "Operar variable", "S", "name:Variable:R", "op:Operación:O:+|-|*|/", "val:Valor:V"},
        {"save", "Guardar progreso", "S"},
        {"clearsave", "Borrar progreso guardado", "S"},
        {"setimg", "Cambiar imagen", "S", "node:Nodo:G", "asset:Imagen:I"},
        {"setsnd", "Cambiar sonido", "S", "node:Nodo (Sound):G", "asset:Sonido:A"},
        {"play", "Reproducir sonido o animación", "S", "node:Nodo:G"},
        {"stop", "Detener sonido o animación", "S", "node:Nodo:G"},
        {"mod", "Modulate (alpha 0.0 a 1.0)", "S", "node:Nodo:G", "val:Valor decimal:N"},
        {"vis", "Visibilidad", "S", "node:Nodo:G", "val:Visible:O:true|false"},
        {"pos", "Mover a (vacío = no cambia)", "S", "node:Nodo:G", "x:X:V", "y:Y:V"},
        {"text", "Cambiar texto", "S", "node:Nodo (Button):G", "val:Texto:V"},
        {"tween", "Cambio progresivo", "S", "node:Nodo:G", "what:Qué cambia:O:modulate|mover|tamaño",
            "x:Valor (modulate) o X:V", "y:Y (mover o tamaño):V", "secs:Segundos:N"},
    };

    Game g;
    GameView pv;
    String pid;
    File dir, adir, jf;
    Game.Node sel;
    LinearLayout list, props;
    File exported;
    String exportName = "juego";

    // ================= Utilidades de interfaz =================
    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    TextView label(String t) {
        TextView x = new TextView(this);
        x.setText(t);
        x.setTextSize(13);
        x.setPadding(0, dp(10), 0, 0);
        return x;
    }

    TextView title(String t) {
        TextView x = new TextView(this);
        x.setText(t);
        x.setTextSize(17);
        x.setPadding(0, dp(16), 0, dp(4));
        x.setTypeface(null, android.graphics.Typeface.BOLD);
        return x;
    }

    Button btn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setOnClickListener(l);
        return b;
    }

    LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        return s;
    }

    EditText field(LinearLayout p, String lab, String val, int type, final Str cb) {
        p.addView(label(lab));
        EditText e = new EditText(this);
        e.setInputType(type);
        e.setText(val);
        e.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                cb.set(s.toString());
            }
        });
        p.addView(e);
        return e;
    }

    CheckBox check(LinearLayout p, String lab, boolean v, final Str cb) {
        CheckBox c = new CheckBox(this);
        c.setText(lab);
        c.setChecked(v);
        c.setOnCheckedChangeListener((b, on) -> cb.set(on ? "true" : "false"));
        p.addView(c);
        return c;
    }

    static float pf(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    static String fmt1(float v) {
        String s = String.format(Locale.US, "%.3f", v);
        while (s.endsWith("0") && !s.endsWith(".0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    static String trimf(float v) {
        if (v == Math.rint(v)) return String.valueOf((int) v);
        return String.format(Locale.US, "%.1f", v);
    }

    String readText(File f) throws IOException {
        try (InputStream i = new FileInputStream(f)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = i.read(b)) > 0) o.write(b, 0, n);
            return new String(o.toByteArray(), "UTF-8");
        }
    }

    void save() {
        try (FileOutputStream o = new FileOutputStream(jf)) {
            o.write(g.toJson().getBytes("UTF-8"));
        } catch (Exception e) {
            Toast.makeText(this, "No pude guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ================= Ciclo de vida =================
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pid = getIntent().getStringExtra("pid");
        if (pid == null) pid = "p_v1";
        dir = new File(getFilesDir(), "projects/" + pid);
        adir = new File(dir, "assets");
        adir.mkdirs();
        jf = new File(dir, "game.json");
        try {
            File nf = new File(dir, "name.txt");
            if (nf.exists()) exportName = readText(nf).trim();
        } catch (Exception e) {
        }
        Game.Src src = new Game.Src() {
            public InputStream open(String n) throws IOException {
                return new FileInputStream(new File(adir, n));
            }

            public File file(String n) throws IOException {
                return new File(adir, n);
            }
        };
        g = null;
        try {
            if (jf.exists()) g = Game.fromJson(readText(jf), src);
        } catch (Exception e) {
            g = null;
        }
        if (g == null) g = new Game(src);

        LinearLayout root = vbox();
        pv = new GameView(this, g, true);
        pv.l = new GameView.L() {
            public void sel(Game.Node n) {
                select(n);
            }

            public void moved(Game.Node n) {
                rebuildProps();
            }
        };
        root.addView(pv, new LinearLayout.LayoutParams(MP, dp(230)));

        ScrollView sv = new ScrollView(this);
        LinearLayout col = vbox();
        col.setPadding(dp(12), dp(8), dp(12), dp(24));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(MP, 0, 1));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout bar = new LinearLayout(this);
        hs.addView(bar);
        bar.addView(btn("＋ Nodo", x -> addNode()));
        bar.addView(btn("Ajustes", x -> settings()));
        bar.addView(btn("Assets", x -> assetsDialog()));
        bar.addView(btn("▶ Probar", x -> test()));
        bar.addView(btn("Exportar APK", x -> exportDialog()));
        bar.addView(btn("Guardar", x -> {
            save();
            Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show();
        }));
        col.addView(hs);
        col.addView(title("Nodos"));
        list = vbox();
        col.addView(list);
        props = vbox();
        col.addView(props);
        setContentView(root);
        rebuildList();
        rebuildProps();
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
        g.release();
    }

    // ================= Árbol de nodos =================
    void select(Game.Node n) {
        if (n == sel) return;
        sel = n;
        g.sel = n;
        rebuildList();
        rebuildProps();
    }

    void addNode() {
        new AlertDialog.Builder(this).setTitle("Agregar nodo")
                .setItems(Game.TYPES, (d, i) -> {
                    Game.Node n = g.add(Game.TYPES[i]);
                    sel = null;
                    select(n);
                }).show();
    }

    void rebuildList() {
        list.removeAllViews();
        if (g.nodes.isEmpty()) {
            list.addView(label("Vacío. Toca ＋ Nodo para empezar."));
            return;
        }
        addRows(-1, 0);
    }

    void addRows(int parentId, int depth) {
        for (final Game.Node n : g.nodes) {
            boolean root = parentId == -1 ? (n.parent == -1 || g.byId(n.parent) == null) : n.parent == parentId;
            if (!root) continue;
            TextView t = new TextView(this);
            StringBuilder ind = new StringBuilder();
            for (int i = 0; i < depth; i++) ind.append("      ");
            t.setText(ind.toString() + (depth > 0 ? "└ " : "") + n.name + "  (" + n.type + ")");
            t.setTextSize(15);
            t.setPadding(dp(8), dp(10), dp(8), dp(10));
            if (n == sel) t.setBackgroundColor(0x3300AAFF);
            t.setOnClickListener(x -> select(n));
            list.addView(t);
            addRows(n.id, depth + 1);
        }
    }

    // ================= Propiedades =================
    void rebuildProps() {
        props.removeAllViews();
        final Game.Node n = sel;
        if (n == null) {
            props.addView(label("Toca un nodo de la lista o de la escena para editarlo."));
            return;
        }
        props.addView(title(n.name + "   [" + n.type + "]"));

        field(props, "Nombre del nodo", n.name, TXT, s -> {
            String t = s.trim();
            Game.Node o = g.byName(t);
            if (!t.isEmpty() && (o == null || o == n)) {
                n.name = t;
                rebuildList();
            }
        });

        // Padre
        props.addView(label("Padre"));
        final ArrayList<Game.Node> cands = new ArrayList<Game.Node>();
        ArrayList<String> names = new ArrayList<String>();
        names.add("(ninguno)");
        int idx = 0;
        for (Game.Node c : g.nodes) {
            if (g.isDesc(n, c)) continue;
            cands.add(c);
            names.add(c.name);
            if (c.id == n.parent) idx = cands.size();
        }
        Spinner sp = spinner(names.toArray(new String[0]));
        sp.setSelection(idx, false);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                int np = pos == 0 ? -1 : cands.get(pos - 1).id;
                if (np != n.parent) {
                    g.reparent(n, np);
                    props.post(() -> {
                        rebuildList();
                        rebuildProps();
                    });
                }
            }

            public void onNothingSelected(AdapterView<?> a) {
            }
        });
        props.addView(sp);
        check(props, "Seguir al padre (posición, visibilidad y modulate)", n.inherit, s -> n.inherit = s.equals("true"));
        check(props, "Visible", n.visible, s -> n.visible = s.equals("true"));

        // Modulate
        props.addView(label("Modulate (alpha de 0.0 a 1.0)"));
        final EditText ea = new EditText(this);
        ea.setInputType(NUM);
        ea.setText(fmt1(n.alpha));
        final SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(Math.round(n.alpha * 100));
        final boolean[] lock = new boolean[1];
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user) return;
                n.alpha = p / 100f;
                lock[0] = true;
                ea.setText(fmt1(n.alpha));
                lock[0] = false;
            }

            public void onStartTrackingTouch(SeekBar s) {
            }

            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        ea.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                if (lock[0]) return;
                try {
                    float v = Float.parseFloat(s.toString().trim());
                    v = Math.max(0f, Math.min(1f, v));
                    n.alpha = v;
                    sb.setProgress(Math.round(v * 100));
                } catch (Exception e) {
                }
            }
        });
        props.addView(sb);
        props.addView(ea);

        // Posición y tamaño
        final CheckBox[] fitBox = new CheckBox[1];
        field(props, "Posición X", trimf(n.x), NUM, s -> n.x = pf(s, n.x));
        field(props, "Posición Y", trimf(n.y), NUM, s -> n.y = pf(s, n.y));
        field(props, "Ancho", trimf(n.w), NUM, s -> {
            n.w = Math.max(1f, pf(s, n.w));
            if (fitBox[0] != null && fitBox[0].isChecked()) fitBox[0].setChecked(false);
        });
        field(props, "Alto", trimf(n.h), NUM, s -> {
            n.h = Math.max(1f, pf(s, n.h));
            if (fitBox[0] != null && fitBox[0].isChecked()) fitBox[0].setChecked(false);
        });

        // Según el tipo
        if (n.type.equals("Image") || n.type.equals("ImageButton")) {
            props.addView(label("Recurso: " + (n.res.isEmpty() ? "(ninguno)" : n.res)));
            props.addView(btn("Elegir imagen", x -> chooseAsset(true, s -> {
                n.res = s;
                if (n.fit) g.fitNode(n);
                rebuildProps();
            })));
            fitBox[0] = check(props, "Ajustar el nodo al tamaño de la imagen", n.fit, s -> {
                n.fit = s.equals("true");
                if (n.fit) {
                    g.fitNode(n);
                    props.post(() -> rebuildProps());
                }
            });
        } else if (n.type.equals("Button")) {
            field(props, "Texto del botón", n.text, TXT, s -> n.text = s);
            field(props, "Tamaño del texto", trimf(n.tsize), NUM, s -> n.tsize = Math.max(4f, pf(s, n.tsize)));
        } else if (n.type.equals("Sound")) {
            props.addView(label("Recurso: " + (n.snd.isEmpty() ? "(ninguno)" : n.snd)));
            props.addView(btn("Elegir sonido", x -> chooseAsset(false, s -> {
                n.snd = s;
                rebuildProps();
            })));
            check(props, "Repetir (bucle)", n.loop, s -> n.loop = s.equals("true"));
            check(props, "Reproducir al iniciar", n.autoplay, s -> n.autoplay = s.equals("true"));
            props.addView(btn("▶ Probar sonido", x -> g.playSound(n)));
            props.addView(btn("■ Detener", x -> g.stopSound(n)));
        } else if (n.type.equals("AnimatedSprite")) {
            field(props, "FPS (cuadros por segundo)", trimf(n.fps), NUM, s -> n.fps = Math.max(0.1f, pf(s, n.fps)));
            check(props, "Repetir animación", n.loop, s -> n.loop = s.equals("true"));
            check(props, "Reproducir al iniciar el juego", n.autoplay, s -> n.autoplay = s.equals("true"));
            fitBox[0] = check(props, "Ajustar el nodo al tamaño del primer cuadro", n.fit, s -> {
                n.fit = s.equals("true");
                if (n.fit) {
                    g.fitNode(n);
                    props.post(() -> rebuildProps());
                }
            });
            props.addView(label("Cuadros (" + n.frames.size() + ")"));
            for (int i = 0; i < n.frames.size(); i++) {
                TextView t = new TextView(this);
                t.setText((i + 1) + ".  " + n.frames.get(i));
                props.addView(t);
            }
            props.addView(btn("＋ Agregar imagen", x -> chooseAsset(true, s -> {
                n.frames.add(s);
                if (n.frames.size() == 1 && n.fit) g.fitNode(n);
                rebuildProps();
            })));
            props.addView(btn("Quitar el último cuadro", x -> {
                if (!n.frames.isEmpty()) n.frames.remove(n.frames.size() - 1);
                n.frame = 0;
                rebuildProps();
            }));
            props.addView(btn(n.preview ? "■ Detener vista previa" : "▶ Reproducir vista previa", x -> {
                n.preview = !n.preview;
                n.frame = 0;
                n.ft = 0;
                rebuildProps();
            }));
            props.addView(label("La vista previa solo se ve aquí; no afecta al juego."));
        } else if (n.type.equals("Save")) {
            props.addView(label("Variables que se guardan en el teléfono y se restauran al abrir el juego. "
                    + "Toca una para quitarla."));
            for (int i = 0; i < n.sv.size(); i++) {
                final int k = i;
                String[] q = n.sv.get(i).split(":", 3);
                String txt = q[0] + "  (" + (q.length > 1 ? q[1] : "") + ")  inicial: " + (q.length > 2 ? q[2] : "");
                props.addView(row(0, txt, 0x14000000, x -> new AlertDialog.Builder(this)
                        .setTitle("¿Quitar de los datos guardados?")
                        .setPositiveButton("Quitar", (d, w) -> {
                            n.sv.remove(k);
                            rebuildProps();
                        }).setNegativeButton("Cancelar", null).show()));
            }
            props.addView(btn("＋ Agregar variable guardada", x -> saveVarDialog(n)));
            check(props, "Guardar automáticamente al cerrar o salir del juego", n.autosave,
                    s -> n.autosave = s.equals("true"));
            props.addView(label("También puedes usar los bloques Guardar progreso y Borrar progreso guardado."));
            props.addView(btn("Borrar progreso de prueba", x -> {
                getSharedPreferences("test_" + pid, MODE_PRIVATE).edit().clear().apply();
                Toast.makeText(this, "Progreso de prueba borrado", Toast.LENGTH_SHORT).show();
            }));
        }

        props.addView(title("Lógica"));
        props.addView(btn("Script de bloques (" + n.script.size() + ")", x -> openScript(n)));
        props.addView(btn("Eliminar nodo", x -> new AlertDialog.Builder(this)
                .setTitle("¿Eliminar " + n.name + "?")
                .setMessage("También se eliminan sus hijos.")
                .setPositiveButton("Eliminar", (d, w) -> {
                    g.remove(n);
                    sel = null;
                    g.sel = null;
                    rebuildList();
                    rebuildProps();
                }).setNegativeButton("Cancelar", null).show()));
    }

    void saveVarDialog(final Game.Node n) {
        LinearLayout v = vbox();
        v.setPadding(dp(20), dp(10), dp(20), dp(10));
        v.addView(label("Nombre de la variable"));
        final EditText en = new EditText(this);
        en.setInputType(TXT);
        LinearLayout r = new LinearLayout(this);
        r.addView(en, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r.addView(btn("▼", x -> varChooser(en)));
        v.addView(r);
        v.addView(label("Tipo"));
        final Spinner st = spinner(new String[]{"int", "float", "bool", "string"});
        v.addView(st);
        v.addView(label("Valor inicial (si aún no hay progreso guardado)"));
        final EditText ei = new EditText(this);
        ei.setInputType(TXT);
        v.addView(ei);
        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        new AlertDialog.Builder(this).setTitle("Variable guardada").setView(sv)
                .setPositiveButton("Agregar", (d, w) -> {
                    String nm = en.getText().toString().trim().replace(":", "");
                    if (nm.isEmpty()) return;
                    String entry = nm + ":" + st.getSelectedItem() + ":" + ei.getText().toString().trim();
                    for (int i = n.sv.size() - 1; i >= 0; i--) {
                        if (n.sv.get(i).split(":", 3)[0].equals(nm)) n.sv.remove(i);
                    }
                    n.sv.add(entry);
                    rebuildProps();
                }).setNegativeButton("Cancelar", null).show();
    }

    // ================= Assets =================
    static boolean isImg(String n) {
        n = n.toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp")
                || n.endsWith(".gif") || n.endsWith(".bmp");
    }

    static boolean isSnd(String n) {
        n = n.toLowerCase(Locale.ROOT);
        return n.endsWith(".ogg") || n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".m4a")
                || n.endsWith(".aac");
    }

    String[] assets(boolean image) {
        ArrayList<String> l = new ArrayList<String>();
        File[] fs = adir.listFiles();
        if (fs != null) for (File f : fs) if (image ? isImg(f.getName()) : isSnd(f.getName())) l.add(f.getName());
        Collections.sort(l);
        return l.toArray(new String[0]);
    }

    void chooseAsset(boolean image, final Pick cb) {
        final String[] names = assets(image);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(image ? "Elegir imagen" : "Elegir sonido");
        if (names.length == 0) b.setMessage("No hay assets de este tipo. Usa Importar.");
        else b.setItems(names, (d, i) -> cb.got(names[i]));
        b.setNeutralButton("Importar…", (d, i) -> startImport());
        b.setNegativeButton("Cancelar", null).show();
    }

    void startImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, 1);
    }

    void assetsDialog() {
        final ArrayList<String> all = new ArrayList<String>();
        File[] fs = adir.listFiles();
        if (fs != null) for (File f : fs) all.add(f.getName());
        Collections.sort(all);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Assets del proyecto");
        if (all.isEmpty()) {
            b.setMessage("Aún no hay assets. Importa imágenes (png, jpg, webp) y sonidos (ogg, mp3, wav).");
        } else {
            b.setItems(all.toArray(new String[0]), (d, i) -> {
                final String nm = all.get(i);
                new AlertDialog.Builder(this).setTitle("¿Eliminar " + nm + "?")
                        .setPositiveButton("Eliminar", (d2, w) -> {
                            new File(adir, nm).delete();
                            g.flush();
                        }).setNegativeButton("Cancelar", null).show();
            });
        }
        b.setNeutralButton("Importar…", (d, i) -> startImport());
        b.setNegativeButton("Cerrar", null).show();
    }

    String nameOf(Uri u) {
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception e) {
        }
        return "asset";
    }

    void importOne(Uri u) throws IOException {
        String nm = nameOf(u).replaceAll("[^A-Za-z0-9._-]", "_");
        File f = new File(adir, nm);
        int k = 1;
        while (f.exists()) f = new File(adir, (k++) + "_" + nm);
        try (InputStream i = getContentResolver().openInputStream(u); OutputStream o = new FileOutputStream(f)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (res != RESULT_OK || d == null) return;
        try {
            if (req == 1) {
                int cnt = 0;
                ClipData cd = d.getClipData();
                if (cd != null) {
                    for (int i = 0; i < cd.getItemCount(); i++) {
                        importOne(cd.getItemAt(i).getUri());
                        cnt++;
                    }
                } else if (d.getData() != null) {
                    importOne(d.getData());
                    cnt++;
                }
                g.flush();
                Toast.makeText(this, "Importados: " + cnt, Toast.LENGTH_SHORT).show();
            } else if (req == 2 && d.getData() != null && exported != null) {
                try (InputStream i = new FileInputStream(exported);
                     OutputStream o = getContentResolver().openOutputStream(d.getData())) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
                }
                Toast.makeText(this, "APK guardado ✔ (" + (exported.length() / 1024) + " KB)", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ================= Ajustes del proyecto =================
    void settings() {
        LinearLayout v = vbox();
        v.setPadding(dp(20), dp(10), dp(20), dp(10));
        v.addView(label("Orientación"));
        final Spinner so = spinner(new String[]{"Horizontal", "Vertical (portátil)"});
        so.setSelection(g.landscape ? 0 : 1);
        v.addView(so);
        v.addView(label("Modo de pantalla"));
        final Spinner sm = spinner(new String[]{"Mantener (con barras negras)", "Estirar", "Rellenar (recorta)"});
        sm.setSelection(g.stretch.equals("stretch") ? 1 : g.stretch.equals("fill") ? 2 : 0);
        v.addView(sm);
        v.addView(label("Tamaño del viewport (escribe con el teclado o elige uno)"));
        final String[] presets = {"Personalizado", "1280 x 720", "1920 x 1080", "720 x 1280", "1080 x 1920",
            "640 x 360", "960 x 540"};
        final Spinner spr = spinner(presets);
        v.addView(spr);
        final EditText ew = new EditText(this);
        ew.setInputType(InputType.TYPE_CLASS_NUMBER);
        ew.setHint("Ancho");
        ew.setText(String.valueOf(g.vw));
        final EditText eh = new EditText(this);
        eh.setInputType(InputType.TYPE_CLASS_NUMBER);
        eh.setHint("Alto");
        eh.setText(String.valueOf(g.vh));
        v.addView(ew);
        v.addView(eh);
        spr.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View vv, int pos, long id) {
                if (pos == 0) return;
                String[] p = presets[pos].split(" x ");
                ew.setText(p[0]);
                eh.setText(p[1]);
            }

            public void onNothingSelected(AdapterView<?> a) {
            }
        });
        v.addView(label("Color de fondo (hexadecimal RRGGBB)"));
        final EditText ec = new EditText(this);
        ec.setText(String.format(Locale.US, "%06X", g.bg & 0xFFFFFF));
        v.addView(ec);
        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        new AlertDialog.Builder(this).setTitle("Ajustes del proyecto").setView(sv)
                .setPositiveButton("Aplicar", (d, w) -> {
                    g.landscape = so.getSelectedItemPosition() == 0;
                    int mi = sm.getSelectedItemPosition();
                    g.stretch = mi == 1 ? "stretch" : mi == 2 ? "fill" : "keep";
                    try {
                        int nw = Integer.parseInt(ew.getText().toString().trim());
                        int nh = Integer.parseInt(eh.getText().toString().trim());
                        g.vw = Math.max(64, Math.min(8192, nw));
                        g.vh = Math.max(64, Math.min(8192, nh));
                    } catch (Exception e) {
                    }
                    try {
                        g.bg = 0xFF000000 | Integer.parseInt(ec.getText().toString().trim().replace("#", ""), 16);
                    } catch (Exception e) {
                    }
                    save();
                }).setNegativeButton("Cancelar", null).show();
    }

    // ================= Probar y exportar =================
    void test() {
        save();
        Intent i = new Intent(this, PlayActivity.class);
        i.putExtra("dir", dir.getPath());
        startActivity(i);
    }

    void exportDialog() {
        save();
        LinearLayout v = vbox();
        v.setPadding(dp(20), dp(10), dp(20), dp(10));
        v.addView(label("Nombre de la app"));
        final EditText en = new EditText(this);
        en.setText(exportName);
        v.addView(en);
        v.addView(label("Versión"));
        final EditText ev = new EditText(this);
        ev.setText("1.0");
        v.addView(ev);
        v.addView(label("Imágenes del APK"));
        final Spinner si = spinner(new String[]{"Optimizar (WebP, más liviano)",
            "Optimizar sin pérdida (pixel art)", "No tocar las imágenes"});
        v.addView(si);
        v.addView(label("Android: 7.0 (API 24) hasta la última versión (fijo)"));
        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        new AlertDialog.Builder(this).setTitle("Exportar APK").setView(sv)
                .setPositiveButton("Exportar", (d, w) -> {
                    String nm = en.getText().toString().trim();
                    String vr = ev.getText().toString().trim();
                    if (nm.isEmpty()) nm = "Juego";
                    if (vr.isEmpty()) vr = "1.0";
                    doExport(nm, vr, si.getSelectedItemPosition());
                }).setNegativeButton("Cancelar", null).show();
    }

    void doExport(final String name, final String ver, final int imgMode) {
        exportName = name;
        final String json;
        try {
            json = g.toJson();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        final Set<String> used = g.usedAssets();
        final HashMap<String, float[]> sizes = g.imageSizes();
        final boolean noDown = g.hasSizeTween();
        Toast.makeText(this, "Exportando…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File out = new File(getCacheDir(), "export.apk");
                Exporter.build(this, json, adir, used, sizes, noDown, imgMode, name, ver, out);
                exported = out;
                runOnUiThread(() -> {
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/vnd.android.package-archive");
                    i.putExtra(Intent.EXTRA_TITLE, name.replaceAll("[^A-Za-z0-9._-]", "_") + ".apk");
                    startActivityForResult(i, 2);
                });
            } catch (Throwable e) {
                final String m = e.getMessage() != null ? e.getMessage() : e.toString();
                runOnUiThread(() -> Toast.makeText(this, "Error al exportar: " + m, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // ================= Selectores rápidos (sin escribir) =================
    void collectNames(ArrayList<Game.Blk> l, TreeSet<String> vars, TreeSet<String> msgs) {
        for (Game.Blk b : l) {
            if ((b.t.equals("var") || b.t.equals("math")) && !b.s("name").trim().isEmpty()) vars.add(b.s("name").trim());
            if ((b.t.equals("send") || b.t.equals("recv")) && !b.s("msg").trim().isEmpty()) msgs.add(b.s("msg").trim());
            collectNames(b.body, vars, msgs);
            collectNames(b.els, vars, msgs);
        }
    }

    TreeSet<String> variableNames() {
        TreeSet<String> v = new TreeSet<String>();
        TreeSet<String> m = new TreeSet<String>();
        for (Game.Node n : g.nodes) {
            collectNames(n.script, v, m);
            for (String e : n.sv) {
                String nm = e.split(":", 3)[0];
                if (!nm.isEmpty()) v.add(nm);
            }
        }
        return v;
    }

    TreeSet<String> messageNames() {
        TreeSet<String> v = new TreeSet<String>();
        TreeSet<String> m = new TreeSet<String>();
        for (Game.Node n : g.nodes) collectNames(n.script, v, m);
        return m;
    }

    void inputDialog(String title, final Str cb) {
        final EditText e = new EditText(this);
        e.setInputType(TXT);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(10), dp(20), 0);
        box.addView(e, new LinearLayout.LayoutParams(MP, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setPositiveButton("Aceptar", (d, w) -> {
                    String s = e.getText().toString().trim();
                    if (!s.isEmpty()) cb.set(s);
                }).setNegativeButton("Cancelar", null).show();
    }

    void listChooser(String title, TreeSet<String> items, String newLabel, final EditText target) {
        final ArrayList<String> l = new ArrayList<String>(items);
        l.add(newLabel);
        final int newIdx = l.size() - 1;
        new AlertDialog.Builder(this).setTitle(title).setItems(l.toArray(new String[0]), (d, i) -> {
            if (i == newIdx) inputDialog(newLabel.replace("＋ ", ""), s -> target.setText(s));
            else target.setText(l.get(i));
        }).show();
    }

    void varChooser(EditText target) {
        listChooser("Elegir variable", variableNames(), "＋ Nueva variable…", target);
    }

    void msgChooser(EditText target) {
        listChooser("Elegir mensaje", messageNames(), "＋ Nuevo mensaje…", target);
    }

    void propChooser(final EditText target) {
        final String[] nn = nodeNames();
        new AlertDialog.Builder(this).setTitle("Elegir nodo").setItems(nn, (d, i) -> {
            final String node = nn[i];
            new AlertDialog.Builder(this).setTitle("Propiedad de " + node).setItems(PROPS,
                    (d2, k) -> target.setText(node + "." + PROPS[k])).show();
        }).show();
    }

    void pickValue(final EditText e, String kind) {
        if (kind.equals("R")) {
            varChooser(e);
            return;
        }
        if (kind.equals("M")) {
            msgChooser(e);
            return;
        }
        final String[] cats = {"Una variable", "Propiedad de un nodo (x, y, visible…)", "true", "false"};
        new AlertDialog.Builder(this).setTitle("Insertar").setItems(cats, (d, i) -> {
            if (i == 0) varChooser(e);
            else if (i == 1) propChooser(e);
            else e.setText(i == 2 ? "true" : "false");
        }).show();
    }

    // ================= Editor de bloques =================
    static String[] specOf(String t) {
        for (String[] s : SPEC) if (s[0].equals(t)) return s;
        return new String[]{t, t, "S"};
    }

    static boolean container(Game.Blk b) {
        String t = b.t;
        return specOf(t)[2].equals("E") || t.equals("if") || t.equals("elif") || t.equals("else")
                || t.equals("while") || t.equals("repeat") || t.equals("forever");
    }

    static boolean control(String t) {
        return t.equals("if") || t.equals("elif") || t.equals("else") || t.equals("while")
                || t.equals("repeat") || t.equals("forever") || t.equals("wait") || t.equals("break");
    }

    String describe(Game.Blk b) {
        String[] sp = specOf(b.t);
        if (b.t.equals("if") || b.t.equals("elif") || b.t.equals("while")) {
            return sp[1] + "  " + (b.b("not") ? "NOT " : "") + b.s("l") + " " + b.s("op") + " " + b.s("r");
        }
        if (b.t.equals("var")) return "Variable  " + b.s("name") + " (" + b.s("vtype") + ") = " + b.s("val");
        StringBuilder sb = new StringBuilder(sp[1]);
        boolean first = true;
        for (int i = 3; i < sp.length; i++) {
            String v = b.s(sp[i].split(":", 4)[0]);
            if (v.isEmpty()) continue;
            sb.append(first ? ":  " : "  ·  ").append(v);
            first = false;
        }
        return sb.toString();
    }

    Game.Blk newBlk(String t) {
        Game.Blk b = new Game.Blk(t);
        String[] sp = specOf(t);
        for (int i = 3; i < sp.length; i++) {
            String[] p = sp[i].split(":", 4);
            if (p[2].equals("O")) b.f.put(p[0], p[3].split("\\|")[0]);
            else if (p[2].equals("G")) b.f.put(p[0], "self");
            else if (p[2].equals("B")) b.f.put(p[0], "false");
        }
        return b;
    }

    String[] nodeNames() {
        ArrayList<String> l = new ArrayList<String>();
        l.add("self");
        for (Game.Node n : g.nodes) l.add(n.name);
        return l.toArray(new String[0]);
    }

    String[] withEmpty(String[] a) {
        String[] r = new String[a.length + 1];
        r[0] = "(ninguno)";
        System.arraycopy(a, 0, r, 1, a.length);
        return r;
    }

    static String normNum(String s) {
        s = s.trim();
        if (s.matches("-?\\d+")) return s + ".0";
        return s;
    }

    void openScript(final Game.Node n) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar);
        LinearLayout root = vbox();
        root.addView(title("  Script de " + n.name));
        final LinearLayout box = vbox();
        box.setPadding(0, 0, 0, dp(24));
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(MP, 0, 1));
        final Runnable[] ref = new Runnable[1];
        ref[0] = () -> {
            box.removeAllViews();
            render(box, n.script, 0, ref[0], true);
            box.addView(btn("＋ Agregar evento", x -> pickBlock(n.script, true, ref[0])));
        };
        root.addView(btn("Cerrar", x -> {
            d.dismiss();
            rebuildProps();
        }));
        ref[0].run();
        d.setContentView(root);
        d.show();
    }

    TextView row(int depth, String text, int bg, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setPadding(dp(12 + depth * 22), dp(10), dp(8), dp(10));
        t.setBackgroundColor(bg);
        if (l != null) t.setOnClickListener(l);
        return t;
    }

    void render(LinearLayout box, final ArrayList<Game.Blk> list, int depth, final Runnable ref, boolean top) {
        for (int i = 0; i < list.size(); i++) {
            final Game.Blk b = list.get(i);
            final int idx = i;
            boolean ev = specOf(b.t)[2].equals("E");
            int color = ev ? 0x44FFAA00 : control(b.t) ? 0x3300AAFF : 0x14000000;
            box.addView(row(depth, (ev ? "▶ " : "") + describe(b), color, x -> blockMenu(list, idx, ref)));
            if (container(b)) {
                if (b.t.equals("hold")) box.addView(row(depth + 1, "Si sigue presionado:", 0, null));
                render(box, b.body, depth + 1, ref, false);
                box.addView(row(depth + 1, "＋ bloque", 0, x -> pickBlock(b.body, false, ref)));
                if (b.t.equals("hold")) {
                    box.addView(row(depth + 1, "Si no (al soltar):", 0, null));
                    render(box, b.els, depth + 1, ref, false);
                    box.addView(row(depth + 1, "＋ bloque", 0, x -> pickBlock(b.els, false, ref)));
                }
            }
        }
    }

    void blockMenu(final ArrayList<Game.Blk> list, final int idx, final Runnable ref) {
        String[] items = {"Editar", "Subir", "Bajar", "Eliminar"};
        new AlertDialog.Builder(this).setItems(items, (d, w) -> {
            Game.Blk b = list.get(idx);
            if (w == 0) {
                editBlock(b, ref);
            } else if (w == 1 && idx > 0) {
                Collections.swap(list, idx, idx - 1);
                ref.run();
            } else if (w == 2 && idx < list.size() - 1) {
                Collections.swap(list, idx, idx + 1);
                ref.run();
            } else if (w == 3) {
                list.remove(idx);
                ref.run();
            }
        }).show();
    }

    void pickBlock(final ArrayList<Game.Blk> target, boolean events, final Runnable ref) {
        final ArrayList<String> types = new ArrayList<String>();
        ArrayList<String> labels = new ArrayList<String>();
        for (String[] s : SPEC) {
            if (s[2].equals("E") == events) {
                types.add(s[0]);
                labels.add(s[1]);
            }
        }
        new AlertDialog.Builder(this).setTitle(events ? "Evento" : "Bloque")
                .setItems(labels.toArray(new String[0]), (d, i) -> {
                    Game.Blk b = newBlk(types.get(i));
                    target.add(b);
                    ref.run();
                    if (specOf(b.t).length > 3) editBlock(b, ref);
                }).show();
    }

    void editBlock(final Game.Blk b, final Runnable ref) {
        final String[] sp = specOf(b.t);
        if (sp.length <= 3) {
            Toast.makeText(this, "Este bloque no tiene ajustes", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout v = vbox();
        v.setPadding(dp(20), dp(10), dp(20), dp(10));
        final ArrayList<View> views = new ArrayList<View>();
        final ArrayList<String> keys = new ArrayList<String>();
        final ArrayList<String> kinds = new ArrayList<String>();
        for (int i = 3; i < sp.length; i++) {
            String[] p = sp[i].split(":", 4);
            final String key = p[0], lab = p[1], kind = p[2];
            String opt = p.length > 3 ? p[3] : "";
            String cur = b.s(key);
            v.addView(label(lab));
            View w;
            if (kind.equals("T") || kind.equals("N") || kind.equals("V") || kind.equals("R") || kind.equals("M")) {
                final EditText e = new EditText(this);
                e.setInputType(TXT);
                e.setText(cur);
                w = e;
                if (kind.equals("T")) {
                    v.addView(e);
                } else {
                    LinearLayout r = new LinearLayout(this);
                    r.addView(e, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    r.addView(btn("▼", x -> pickValue(e, kind)));
                    v.addView(r);
                }
            } else if (kind.equals("B")) {
                CheckBox c = new CheckBox(this);
                c.setText("Sí");
                c.setChecked("true".equals(cur));
                w = c;
                v.addView(c);
            } else {
                String[] opts;
                if (kind.equals("G")) opts = nodeNames();
                else if (kind.equals("I")) opts = withEmpty(assets(true));
                else if (kind.equals("A")) opts = withEmpty(assets(false));
                else opts = opt.split("\\|");
                Spinner s = spinner(opts);
                for (int k = 0; k < opts.length; k++) if (opts[k].equals(cur)) s.setSelection(k);
                w = s;
                v.addView(s);
            }
            views.add(w);
            keys.add(key);
            kinds.add(kind);
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        new AlertDialog.Builder(this).setTitle(sp[1]).setView(sv)
                .setPositiveButton("Aceptar", (d, wh) -> {
                    for (int i = 0; i < views.size(); i++) {
                        View w = views.get(i);
                        String kind = kinds.get(i);
                        String val;
                        if (w instanceof EditText) {
                            val = ((EditText) w).getText().toString();
                            if (kind.equals("N")) val = normNum(val);
                        } else if (w instanceof CheckBox) {
                            val = ((CheckBox) w).isChecked() ? "true" : "false";
                        } else {
                            Spinner s = (Spinner) w;
                            val = String.valueOf(s.getSelectedItem());
                            if ((kind.equals("I") || kind.equals("A")) && s.getSelectedItemPosition() == 0) val = "";
                        }
                        b.f.put(keys.get(i), val);
                    }
                    ref.run();
                }).setNegativeButton("Cancelar", null).show();
    }
}
