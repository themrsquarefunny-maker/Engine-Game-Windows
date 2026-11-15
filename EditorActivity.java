package com.mini.engine;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.MotionEvent;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    interface Xy {
        void got(float x, float y);
    }

    static final int NUM = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
            | InputType.TYPE_NUMBER_FLAG_SIGNED;
    static final int TXT = InputType.TYPE_CLASS_TEXT;
    static final int MP = ViewGroup.LayoutParams.MATCH_PARENT;
    static final String[] PROPS = {"x", "y", "w", "h", "modulate", "visible", "text", "fps", "frame", "zoom"};
    static final String[] BASE_TYPES = {"int", "float", "bool", "string"};

    // tipo, etiqueta, E (evento) / S (bloque), campos "clave:etiqueta:tipo[:opciones]"
    // tipos: T texto, N número, V valor (con selector), R variable (crear o elegir), Q variable existente,
    //        M mensaje, B sí/no, G nodo, I imagen, A audio, C escena, Y tipo de variable,
    //        L lista de variables, E lista de valores de enum, O opciones
    static final String[][] SPEC = {
        {"start", "Al iniciar", "E"},
        {"press", "Al presionar", "E"},
        {"hold", "Al mantener presionado", "E"},
        {"recv", "Recibir mensaje", "E", "msg:Mensaje:M"},
        {"frame", "Cada fotograma", "E"},
        {"every", "Cada X segundos", "E", "secs:Segundos:N"},
        {"change", "Al cambiar una propiedad", "E", "node:Nodo:G", "prop:Propiedad:O:visible|modulate|x|y|w|h|text"},
        {"orient", "Al cambiar de orientación", "E"},
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
        {"var", "Variable (crear)", "S", "name:Nombre:R", "vtype:Tipo:Y",
            "scope:Alcance:O:global|escena|nodo", "val:Valor inicial:V"},
        {"setvar", "Cambiar valor de variable", "S", "name:Variable:Q", "val:Nuevo valor:V"},
        {"math", "Operar variable", "S", "name:Variable:Q", "op:Operación:O:+|-|*|/", "val:Valor:V"},
        {"enum", "Enum (definir)", "S", "name:Nombre del enum:T", "values:Valores:E"},
        {"enumnext", "Cambiar enum (siguiente o anterior)", "S", "name:Variable enum:Q",
            "dir:Dirección:O:siguiente|anterior", "wrap:Volver al inicio al llegar al final:B"},
        {"save", "Guardar variables", "S", "names:Variables a guardar:L"},
        {"clearsave", "Borrar progreso guardado", "S"},
        {"scene", "Cambiar de escena", "S", "scene:Escena:C"},
        {"orientset", "Cambiar orientación", "S", "mode:Orientación:O:horizontal|vertical"},
        {"viewmode", "Usar viewport o cámara", "S", "mode:Modo:O:viewport|camera2D"},
        {"camset", "Configurar cámara", "S", "node:Cámara (Camera2D):G",
            "what:Qué cambia:O:zoom|seguir a|suavizado|activar", "val:Valor (nombre del nodo si es seguir a):V"},
        {"setimg", "Cambiar imagen", "S", "node:Nodo:G", "asset:Imagen:I"},
        {"setsnd", "Cambiar sonido", "S", "node:Nodo (Sound):G", "asset:Sonido:A"},
        {"play", "Reproducir sonido o animación", "S", "node:Nodo:G"},
        {"stop", "Detener sonido o animación", "S", "node:Nodo:G"},
        {"mod", "Modulate (alpha 0.0 a 1.0)", "S", "node:Nodo:G", "val:Valor decimal:N",
                "touch:Permitir tocar aunque la transparencia sea máxima (alpha 0):B"},
        {"vis", "Visibilidad", "S", "node:Nodo:G", "val:Visible:O:true|false"},
        {"pos", "Mover a (vacío = no cambia)", "S", "node:Nodo:G", "x:X:V", "y:Y:V"},
        {"text", "Cambiar texto", "S", "node:Nodo (Button):G", "val:Texto:V"},
        {"tween", "Cambio progresivo", "S", "node:Nodo:G", "what:Qué cambia:O:modulate|mover|tamaño",
            "x:Valor (modulate) o X:V", "y:Y (mover o tamaño):V", "secs:Segundos:N",
                "touch:Permitir tocar aunque la transparencia sea máxima (solo modulate):B"},
        {"vend", "Al terminar el video", "E"},
        {"vpause", "Pausar video", "S", "node:Nodo (Video):G"},
        {"vseek", "Ir a un segundo del video", "S", "node:Nodo (Video):G", "secs:Segundos:V"},
        {"vvol", "Volumen del video (0.0 a 1.0)", "S", "node:Nodo (Video):G", "val:Volumen:V"},
        {"setvideo", "Cambiar video", "S", "node:Nodo (Video):G", "asset:Video (mp4, webm, mkv):W"},
    };

    Game g;
    GameView pv;
    String pid;
    File dir, adir, jf, iconFile;
    Game.Node sel, scriptOwner;
    LinearLayout list, props;
    TextView sceneLabel, tIconInfo;
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

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
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

    byte[] readBytes(File f) throws IOException {
        try (InputStream i = new FileInputStream(f)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = i.read(b)) > 0) o.write(b, 0, n);
            return o.toByteArray();
        }
    }

    String readText(File f) throws IOException {
        return new String(readBytes(f), "UTF-8");
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
        iconFile = new File(dir, "icon.png");
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
        bar.addView(btn("Escenas", x -> scenesDialog()));
        bar.addView(btn("Ajustes", x -> settings()));
        bar.addView(btn("Assets", x -> assetsDialog()));
        bar.addView(btn("▶ Probar", x -> test(null)));
        bar.addView(btn("Exportar APK", x -> exportDialog()));
        bar.addView(btn("Guardar", x -> {
            save();
            toast("Guardado");
        }));
        col.addView(hs);
        sceneLabel = title("");
        col.addView(sceneLabel);
        list = vbox();
        col.addView(list);
        props = vbox();
        col.addView(props);
        setContentView(root);
        updateSceneLabel();
        rebuildList();
        rebuildProps();
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
        g.release();
    }

    // ================= Escenas =================
    void updateSceneLabel() {
        if (sceneLabel != null) {
            sceneLabel.setText("Escena: " + g.curScene.name + (g.curScene.id == g.startId ? "   ★ inicial" : ""));
        }
    }

    void refreshAll() {
        sel = null;
        g.sel = null;
        rebuildList();
        rebuildProps();
        updateSceneLabel();
    }

    String[] sceneNames() {
        String[] r = new String[g.scenes.size()];
        for (int i = 0; i < r.length; i++) r[i] = g.scenes.get(i).name;
        return r;
    }

    void scenesDialog() {
        ArrayList<String> items = new ArrayList<String>();
        for (Game.Scene s : g.scenes) {
            items.add((s == g.curScene ? "● " : "    ") + (s.id == g.startId ? "★ " : "") + s.name);
        }
        new AlertDialog.Builder(this).setTitle("Escenas  (● editando, ★ inicial)")
                .setItems(items.toArray(new String[0]), (d, i) -> sceneMenu(g.scenes.get(i)))
                .setNeutralButton("＋ Nueva escena", (d, w) -> inputDialog("Nombre de la escena", s -> {
                    Game.Scene n = g.addScene(s);
                    g.openScene(n);
                    refreshAll();
                }))
                .setNegativeButton("Cerrar", null).show();
    }

    void sceneMenu(final Game.Scene sc) {
        String[] items = {"Abrir en el editor", "Probar desde esta escena", "Renombrar", "Duplicar",
            "Marcar como inicial", "Eliminar"};
        new AlertDialog.Builder(this).setTitle(sc.name).setItems(items, (d, w) -> {
            if (w == 0) {
                g.openScene(sc);
                refreshAll();
            } else if (w == 1) {
                test(sc.name);
            } else if (w == 2) {
                inputDialog("Nuevo nombre", s -> {
                    String old = sc.name;
                    String nn = s.trim();
                    Game.Scene o = g.sceneByName(nn);
                    if (nn.isEmpty() || (o != null && o != sc)) {
                        toast("Ese nombre ya existe");
                        return;
                    }
                    sc.name = nn;
                    g.renameSceneRefs(old, nn);
                    updateSceneLabel();
                });
            } else if (w == 3) {
                try {
                    Game.Scene c = g.duplicateScene(sc, sc.name + " copia");
                    g.openScene(c);
                    refreshAll();
                } catch (Exception e) {
                    toast("Error: " + e.getMessage());
                }
            } else if (w == 4) {
                g.startId = sc.id;
                updateSceneLabel();
            } else {
                new AlertDialog.Builder(this).setTitle("¿Eliminar la escena " + sc.name + "?")
                        .setPositiveButton("Eliminar", (d2, w2) -> {
                            if (!g.removeScene(sc)) toast("Debe existir al menos una escena");
                            else refreshAll();
                        }).setNegativeButton("Cancelar", null).show();
            }
        }).show();
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
        if (!n.type.equals("Camera2D") && !n.type.equals("Sound")) {
            check(props, "Fijo a la pantalla (interfaz, ignora la cámara)", n.fixed, s -> n.fixed = s.equals("true"));
        }

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
        } else if (n.type.equals("Video")) {
            props.addView(label("Video: " + (n.res.isEmpty() ? "(ninguno)" : n.res)));
            props.addView(btn("Elegir video (mp4, webm, mkv)", x -> chooseVideo(s -> {
                n.res = s;
                if (n.fit) g.fitNode(n);
                rebuildProps();
            })));
            fitBox[0] = check(props, "Ajustar el nodo al tamaño del video", n.fit, s -> {
                n.fit = s.equals("true");
                if (n.fit) {
                    g.fitNode(n);
                    props.post(() -> rebuildProps());
                }
            });
            check(props, "Repetir (bucle)", n.loop, s -> n.loop = s.equals("true"));
            check(props, "Reproducir al iniciar", n.autoplay, s -> n.autoplay = s.equals("true"));
            field(props, "Volumen (0.0 a 1.0)", fmt1(n.vol), NUM, s -> n.vol = Math.max(0f, Math.min(1f, pf(s, n.vol))));
            props.addView(label("El video se ve al probar el juego, no en el editor. Se dibuja encima de los demás nodos."));
        } else if (n.type.equals("Camera2D")) {
            props.addView(label("El centro de este nodo es el centro de la cámara."));
            field(props, "Zoom (1.0 = normal)", fmt1(n.zoom), NUM, s -> n.zoom = Math.max(0.05f, pf(s, n.zoom)));
            field(props, "Suavizado (0 = sin suavizado, 5 = suave)", fmt1(n.smooth), NUM,
                    s -> n.smooth = Math.max(0f, pf(s, n.smooth)));
            check(props, "Cámara activa al iniciar la escena", n.current, s -> n.current = s.equals("true"));
            props.addView(label("Seguir a"));
            final ArrayList<String> fn = new ArrayList<String>();
            fn.add("(ninguno)");
            int fi = 0;
            for (Game.Node c : g.nodes) {
                if (c == n || c.type.equals("Camera2D")) continue;
                fn.add(c.name);
                if (c.name.equals(n.follow)) fi = fn.size() - 1;
            }
            Spinner sf = spinner(fn.toArray(new String[0]));
            sf.setSelection(fi, false);
            sf.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                    n.follow = pos == 0 ? "" : fn.get(pos);
                }

                public void onNothingSelected(AdapterView<?> a) {
                }
            });
            props.addView(sf);
            check(props, "Limitar el movimiento de la cámara", n.lim, s -> n.lim = s.equals("true"));
            field(props, "Límite izquierdo", trimf(n.ll), NUM, s -> n.ll = pf(s, n.ll));
            field(props, "Límite de arriba", trimf(n.lt), NUM, s -> n.lt = pf(s, n.lt));
            field(props, "Límite derecho", trimf(n.lr), NUM, s -> n.lr = pf(s, n.lr));
            field(props, "Límite de abajo", trimf(n.lb), NUM, s -> n.lb = pf(s, n.lb));
            props.addView(label("La cámara solo se usa si el modo de vista del proyecto es Camera2D "
                    + "(en Ajustes) o con el bloque Usar viewport o cámara."));
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

    static boolean isVid(String n) {
        n = n.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp4") || n.endsWith(".webm") || n.endsWith(".mkv") || n.endsWith(".3gp");
    }

    String[] videoAssets() {
        ArrayList<String> l = new ArrayList<String>();
        File[] fs = adir.listFiles();
        if (fs != null) for (File f : fs) if (isVid(f.getName())) l.add(f.getName());
        Collections.sort(l);
        return l.toArray(new String[0]);
    }

    void chooseVideo(final Pick cb) {
        final String[] names = videoAssets();
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Elegir video (mp4, webm VP8/VP9, mkv)");
        if (names.length == 0) b.setMessage("No hay videos. Usa Importar y elige un .mp4, .webm o .mkv.");
        else b.setItems(names, (d, i) -> cb.got(names[i]));
        b.setNeutralButton("Importar…", (d, i) -> startImport());
        b.setNegativeButton("Cancelar", null).show();
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
            b.setMessage("Aún no hay assets. Importa imágenes (png, jpg, webp), sonidos (ogg, mp3, wav) y videos (mp4, webm, mkv).");
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

    // Recorta la imagen elegida en un cuadrado de 192 x 192 y la guarda como ícono del proyecto
    void makeIcon(Uri u) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream i = getContentResolver().openInputStream(u)) {
            BitmapFactory.decodeStream(i, null, o);
        }
        int sample = 1;
        while (Math.min(o.outWidth, o.outHeight) / (sample * 2) >= 384) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        Bitmap src;
        try (InputStream i = getContentResolver().openInputStream(u)) {
            src = BitmapFactory.decodeStream(i, null, o);
        }
        if (src == null) throw new IOException("No pude leer la imagen");
        int sz = Math.min(src.getWidth(), src.getHeight());
        Bitmap sq = Bitmap.createBitmap(src, (src.getWidth() - sz) / 2, (src.getHeight() - sz) / 2, sz, sz);
        Bitmap ic = Bitmap.createScaledBitmap(sq, 192, 192, true);
        try (FileOutputStream f = new FileOutputStream(iconFile)) {
            ic.compress(Bitmap.CompressFormat.PNG, 100, f);
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
                toast("Importados: " + cnt);
            } else if (req == 2 && d.getData() != null && exported != null) {
                try (InputStream i = new FileInputStream(exported);
                     OutputStream o = getContentResolver().openOutputStream(d.getData())) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
                }
                Toast.makeText(this, "APK guardado ✔ (" + (exported.length() / 1024) + " KB)", Toast.LENGTH_LONG).show();
            } else if (req == 3 && d.getData() != null) {
                makeIcon(d.getData());
                if (tIconInfo != null) tIconInfo.setText("Ícono personalizado ✔");
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ================= Ajustes del proyecto =================
    void settings() {
        LinearLayout v = vbox();
        v.setPadding(dp(20), dp(10), dp(20), dp(10));
        v.addView(label("Orientación inicial"));
        final Spinner so = spinner(new String[]{"Horizontal", "Vertical (portátil)"});
        so.setSelection(g.landscape ? 0 : 1);
        v.addView(so);
        v.addView(label("Modo de pantalla"));
        final Spinner sm = spinner(new String[]{"Mantener (con barras negras)", "Estirar", "Rellenar (recorta)"});
        sm.setSelection(g.stretch.equals("stretch") ? 1 : g.stretch.equals("fill") ? 2 : 0);
        v.addView(sm);
        v.addView(label("Modo de vista"));
        final Spinner sv2 = spinner(new String[]{"Viewport (sin cámara)", "Camera2D"});
        sv2.setSelection(g.viewMode.equals("camera") ? 1 : 0);
        v.addView(sv2);
        v.addView(label("Viewport horizontal (con el teclado o elige uno)"));
        final String[] presets = {"Personalizado", "1280 x 720", "1920 x 1080", "640 x 360", "960 x 540"};
        final Spinner spr = spinner(presets);
        v.addView(spr);
        final EditText ew = new EditText(this);
        ew.setInputType(InputType.TYPE_CLASS_NUMBER);
        ew.setHint("Ancho horizontal");
        ew.setText(String.valueOf(g.vwL));
        final EditText eh = new EditText(this);
        eh.setInputType(InputType.TYPE_CLASS_NUMBER);
        eh.setHint("Alto horizontal");
        eh.setText(String.valueOf(g.vhL));
        v.addView(ew);
        v.addView(eh);
        v.addView(label("Viewport vertical"));
        final EditText pw = new EditText(this);
        pw.setInputType(InputType.TYPE_CLASS_NUMBER);
        pw.setHint("Ancho vertical");
        pw.setText(String.valueOf(g.vwP));
        final EditText ph = new EditText(this);
        ph.setInputType(InputType.TYPE_CLASS_NUMBER);
        ph.setHint("Alto vertical");
        ph.setText(String.valueOf(g.vhP));
        v.addView(pw);
        v.addView(ph);
        spr.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View vv, int pos, long id) {
                if (pos == 0) return;
                String[] p = presets[pos].split(" x ");
                ew.setText(p[0]);
                eh.setText(p[1]);
                pw.setText(p[1]);
                ph.setText(p[0]);
            }

            public void onNothingSelected(AdapterView<?> a) {
            }
        });
        v.addView(label("Color de fondo (hexadecimal RRGGBB)"));
        final EditText ec = new EditText(this);
        ec.setText(String.format(Locale.US, "%06X", g.bg & 0xFFFFFF));
        v.addView(ec);
        final CheckBox ca = check(v, "Guardar variables automáticamente al cerrar el juego", g.autosave, s -> {
        });
        ScrollView sc = new ScrollView(this);
        sc.addView(v);
        new AlertDialog.Builder(this).setTitle("Ajustes del proyecto").setView(sc)
                .setPositiveButton("Aplicar", (d, w) -> {
                    int mi = sm.getSelectedItemPosition();
                    g.stretch = mi == 1 ? "stretch" : mi == 2 ? "fill" : "keep";
                    g.viewMode = sv2.getSelectedItemPosition() == 1 ? "camera" : "viewport";
                    g.autosave = ca.isChecked();
                    try {
                        g.vwL = Math.max(64, Math.min(8192, Integer.parseInt(ew.getText().toString().trim())));
                        g.vhL = Math.max(64, Math.min(8192, Integer.parseInt(eh.getText().toString().trim())));
                        g.vwP = Math.max(64, Math.min(8192, Integer.parseInt(pw.getText().toString().trim())));
                        g.vhP = Math.max(64, Math.min(8192, Integer.parseInt(ph.getText().toString().trim())));
                    } catch (Exception e) {
                    }
                    try {
                        g.bg = 0xFF000000 | Integer.parseInt(ec.getText().toString().trim().replace("#", ""), 16);
                    } catch (Exception e) {
                    }
                    g.applyOrientation(so.getSelectedItemPosition() == 0);
                    save();
                }).setNegativeButton("Cancelar", null).show();
    }

    // ================= Probar y exportar =================
    void test(String sceneName) {
        save();
        Intent i = new Intent(this, PlayActivity.class);
        i.putExtra("dir", dir.getPath());
        if (sceneName != null) i.putExtra("scene", sceneName);
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
        v.addView(label("Ícono de la app"));
        tIconInfo = new TextView(this);
        tIconInfo.setText(iconFile.exists() ? "Ícono personalizado ✔" : "Ícono por defecto");
        v.addView(tIconInfo);
        v.addView(btn("Elegir ícono (una imagen)", x -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            startActivityForResult(i, 3);
        }));
        v.addView(btn("Quitar ícono", x -> {
            iconFile.delete();
            tIconInfo.setText("Ícono por defecto");
        }));
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
        final byte[] icon;
        try {
            json = g.toJson();
            icon = iconFile.exists() ? readBytes(iconFile) : null;
        } catch (Exception e) {
            toast("Error: " + e.getMessage());
            return;
        }
        final Set<String> used = g.usedAssets();
        final HashMap<String, float[]> sizes = g.imageSizes();
        final boolean noDown = g.hasSizeTween();
        toast("Exportando…");
        new Thread(() -> {
            try {
                File out = new File(getCacheDir(), "export.apk");
                Exporter.build(this, json, adir, used, sizes, noDown, imgMode, icon, name, ver, out);
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
    void collectVars(ArrayList<Game.Blk> l, TreeSet<String> vars, TreeSet<String> msgs) {
        for (Game.Blk b : l) {
            if (b.t.equals("var") && !b.s("name").trim().isEmpty()) vars.add(b.s("name").trim());
            if ((b.t.equals("send") || b.t.equals("recv")) && !b.s("msg").trim().isEmpty()) msgs.add(b.s("msg").trim());
            collectVars(b.body, vars, msgs);
            collectVars(b.els, vars, msgs);
        }
    }

    TreeSet<String> variableNames() {
        TreeSet<String> v = new TreeSet<String>();
        TreeSet<String> m = new TreeSet<String>();
        for (Game.Node n : g.allNodes()) collectVars(n.script, v, m);
        return v;
    }

    TreeSet<String> messageNames() {
        TreeSet<String> v = new TreeSet<String>();
        TreeSet<String> m = new TreeSet<String>();
        for (Game.Node n : g.allNodes()) collectVars(n.script, v, m);
        return m;
    }

    String[] typeNames() {
        LinkedHashMap<String, String[]> en = new LinkedHashMap<String, String[]>();
        LinkedHashSet<String> ps = new LinkedHashSet<String>();
        g.collectDecls(en, ps);
        ArrayList<String> l = new ArrayList<String>();
        for (String s : BASE_TYPES) l.add(s);
        l.addAll(en.keySet());
        return l.toArray(new String[0]);
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

    void listChooser(String title, TreeSet<String> items, final String newLabel, final EditText target) {
        final ArrayList<String> l = new ArrayList<String>(items);
        if (newLabel != null) l.add(newLabel);
        if (l.isEmpty()) {
            toast("Aún no hay variables: crea una con el bloque Variable");
            return;
        }
        final int newIdx = newLabel != null ? l.size() - 1 : -1;
        new AlertDialog.Builder(this).setTitle(title).setItems(l.toArray(new String[0]), (d, i) -> {
            if (i == newIdx) inputDialog(newLabel.replace("＋ ", ""), s -> target.setText(s));
            else target.setText(l.get(i));
        }).show();
    }

    void varChooser(EditText target, boolean allowNew) {
        listChooser("Elegir variable", variableNames(), allowNew ? "＋ Nueva variable…" : null, target);
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

    void enumChooser(final EditText target) {
        final LinkedHashMap<String, String[]> en = new LinkedHashMap<String, String[]>();
        LinkedHashSet<String> ps = new LinkedHashSet<String>();
        g.collectDecls(en, ps);
        if (en.isEmpty()) {
            toast("Aún no hay enums: crea uno con el bloque Enum");
            return;
        }
        final ArrayList<String> names = new ArrayList<String>(en.keySet());
        new AlertDialog.Builder(this).setTitle("Elegir enum").setItems(names.toArray(new String[0]), (d, i) -> {
            final String nm = names.get(i);
            final String[] vals = en.get(nm);
            new AlertDialog.Builder(this).setTitle(nm).setItems(vals,
                    (d2, k) -> target.setText(nm + "." + vals[k])).show();
        }).show();
    }

    void pickValue(final EditText e, String kind) {
        if (kind.equals("R")) {
            varChooser(e, true);
            return;
        }
        if (kind.equals("Q")) {
            varChooser(e, false);
            return;
        }
        if (kind.equals("M")) {
            msgChooser(e);
            return;
        }
        final String[] cats = {"Una variable", "Propiedad de un nodo (x, y, visible…)", "Valor de un enum",
            "true", "false"};
        new AlertDialog.Builder(this).setTitle("Insertar").setItems(cats, (d, i) -> {
            if (i == 0) varChooser(e, false);
            else if (i == 1) propChooser(e);
            else if (i == 2) enumChooser(e);
            else e.setText(i == 3 ? "true" : "false");
        }).show();
    }

    // Editor de listas separadas por coma: variables a guardar (L) o valores de un enum (E)
    View listEditor(final String[] holder, final boolean fromVars) {
        LinearLayout box = vbox();
        final TextView tv = new TextView(this);
        tv.setTextSize(15);
        tv.setPadding(0, dp(6), 0, dp(6));
        tv.setText(holder[0].isEmpty() ? "(vacío)" : holder[0]);
        box.addView(tv);
        box.addView(btn(fromVars ? "＋ Agregar variable" : "＋ Agregar valor", x -> {
            if (fromVars) {
                final String[] items = variableNames().toArray(new String[0]);
                if (items.length == 0) {
                    toast("Primero crea variables con el bloque Variable");
                    return;
                }
                new AlertDialog.Builder(this).setTitle("Elegir variable").setItems(items, (d, i) -> {
                    holder[0] = addItem(holder[0], items[i]);
                    tv.setText(holder[0]);
                }).show();
            } else {
                inputDialog("Nuevo valor", s -> {
                    holder[0] = addItem(holder[0], s.replaceAll("[^A-Za-z0-9_]", "_"));
                    tv.setText(holder[0]);
                });
            }
        }));
        box.addView(btn("Quitar el último", x -> {
            int k = holder[0].lastIndexOf(',');
            holder[0] = k < 0 ? "" : holder[0].substring(0, k);
            tv.setText(holder[0].isEmpty() ? "(vacío)" : holder[0]);
        }));
        return box;
    }

    static String addItem(String cur, String item) {
        for (String s : cur.split(",")) if (s.equals(item)) return cur;
        return cur.isEmpty() ? item : cur + "," + item;
    }

    // ================= Elegir posición visualmente =================
    class PickView extends View {
        Bitmap bm;
        float gw, gh, gx, gy, dx, dy;
        TextView info;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF r = new RectF();

        PickView(Context c, Bitmap bm, float gw, float gh, float x, float y, TextView info) {
            super(c);
            this.bm = bm;
            this.gw = gw;
            this.gh = gh;
            this.gx = x;
            this.gy = y;
            this.info = info;
            upd();
        }

        float scale() {
            return Math.min(getWidth() / (float) g.vw, getHeight() / (float) g.vh);
        }

        float offX() {
            return (getWidth() - g.vw * scale()) / 2f;
        }

        float offY() {
            return (getHeight() - g.vh * scale()) / 2f;
        }

        void upd() {
            info.setText("X: " + Math.round(gx) + "    Y: " + Math.round(gy));
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawColor(0xFF111111);
            float s = scale(), ox = offX(), oy = offY();
            r.set(ox, oy, ox + g.vw * s, oy + g.vh * s);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF202020);
            c.drawRect(r, p);
            c.drawBitmap(bm, null, r, null);
            r.set(ox + gx * s, oy + gy * s, ox + (gx + gw) * s, oy + (gy + gh) * s);
            p.setColor(0x66FF00FF);
            c.drawRect(r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f);
            p.setColor(0xFFFF00FF);
            c.drawRect(r, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float s = scale();
            float vx = (e.getX() - offX()) / s, vy = (e.getY() - offY()) / s;
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                if (vx >= gx && vx <= gx + gw && vy >= gy && vy <= gy + gh) {
                    dx = vx - gx;
                    dy = vy - gy;
                } else {
                    dx = gw / 2f;
                    dy = gh / 2f;
                    gx = vx - dx;
                    gy = vy - dy;
                }
            } else if (a == MotionEvent.ACTION_MOVE) {
                gx = vx - dx;
                gy = vy - dy;
            }
            upd();
            invalidate();
            return true;
        }
    }

    void showPicker(Bitmap bm, boolean fromGame, float gw, float gh, float x0, float y0, final Xy cb) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar);
        LinearLayout root = vbox();
        TextView head = new TextView(this);
        head.setText(fromGame ? "Última partida: arrastra el recuadro a su lugar"
                : "Escena del editor (aún no hay partida guardada): arrastra el recuadro");
        head.setPadding(dp(12), dp(16), dp(12), dp(8));
        root.addView(head);
        final TextView info = new TextView(this);
        info.setPadding(dp(12), dp(8), dp(12), dp(8));
        final PickView pk = new PickView(this, bm, gw, gh, x0, y0, info);
        root.addView(pk, new LinearLayout.LayoutParams(MP, 0, 1));
        root.addView(info);
        LinearLayout row = new LinearLayout(this);
        row.addView(btn("✕ Cancelar", x -> d.dismiss()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(btn("✓ Aceptar", x -> {
            cb.got(pk.gx, pk.gy);
            d.dismiss();
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(row);
        d.setContentView(root);
        d.show();
    }

    void pickPosition(String nodeName, final EditText ex, final EditText ey) {
        Game.Node tn = nodeName.equals("self") ? scriptOwner : g.byName(nodeName);
        float gw = tn != null ? tn.w : 100f, gh = tn != null ? tn.h : 100f;
        float px = 0, py = 0;
        if (tn != null && tn.inherit && tn.parent >= 0) {
            Game.Node pn = g.byId(tn.parent);
            if (pn != null) {
                px = pn.wx;
                py = pn.wy;
            }
        }
        final float fox = px, foy = py;
        float cx = tn != null ? tn.wx : (g.vw - gw) / 2f;
        float cy = tn != null ? tn.wy : (g.vh - gh) / 2f;
        String tx = ex.getText().toString().trim(), ty = ey.getText().toString().trim();
        try {
            if (!tx.isEmpty()) cx = Float.parseFloat(tx) + fox;
        } catch (Exception e) {
        }
        try {
            if (!ty.isEmpty()) cy = Float.parseFloat(ty) + foy;
        } catch (Exception e) {
        }
        Bitmap bm = null;
        boolean fromGame = false;
        File last = new File(dir, "last.png");
        if (last.exists()) {
            bm = BitmapFactory.decodeFile(last.getPath());
            fromGame = bm != null;
        }
        if (bm == null) bm = g.snapshot(960);
        showPicker(bm, fromGame, gw, gh, cx, cy, (rx, ry) -> {
            ex.setText(trimf(rx - fox));
            ey.setText(trimf(ry - foy));
        });
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
        if (b.t.equals("var")) {
            return "Variable  " + b.s("name") + " (" + b.s("vtype") + ", " + b.s("scope") + ") = " + b.s("val");
        }
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
            else if (p[2].equals("Y")) b.f.put(p[0], "int");
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
        scriptOwner = n;
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
            toast("Este bloque no tiene ajustes");
            return;
        }
        LinearLayout v = vbox();
        v.setPadding(dp(20), dp(10), dp(20), dp(10));
        final ArrayList<View> views = new ArrayList<View>();
        final ArrayList<String> keys = new ArrayList<String>();
        final ArrayList<String> kinds = new ArrayList<String>();
        final ArrayList<String[]> holders = new ArrayList<String[]>();
        for (int i = 3; i < sp.length; i++) {
            String[] p = sp[i].split(":", 4);
            final String key = p[0], lab = p[1], kind = p[2];
            String opt = p.length > 3 ? p[3] : "";
            String cur = b.s(key);
            v.addView(label(lab));
            View w;
            String[] holder = null;
            if (kind.equals("L") || kind.equals("E")) {
                holder = new String[]{cur};
                w = listEditor(holder, kind.equals("L"));
                v.addView(w);
            } else if (kind.equals("T") || kind.equals("N") || kind.equals("V") || kind.equals("R")
                    || kind.equals("Q") || kind.equals("M")) {
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
                else if (kind.equals("W")) opts = withEmpty(videoAssets());
                else if (kind.equals("C")) opts = sceneNames();
                else if (kind.equals("Y")) opts = typeNames();
                else opts = opt.split("\\|");
                Spinner s = spinner(opts);
                for (int k = 0; k < opts.length; k++) if (opts[k].equals(cur)) s.setSelection(k);
                w = s;
                v.addView(s);
            }
            views.add(w);
            keys.add(key);
            kinds.add(kind);
            holders.add(holder);
        }
        if (b.t.equals("pos") || b.t.equals("tween")) {
            v.addView(btn("Elegir posición visualmente", x -> {
                if (b.t.equals("tween")) {
                    Spinner sw = (Spinner) views.get(keys.indexOf("what"));
                    if (!String.valueOf(sw.getSelectedItem()).equals("mover")) {
                        toast("Cambia 'Qué cambia' a mover para elegir una posición");
                        return;
                    }
                }
                Spinner sn = (Spinner) views.get(keys.indexOf("node"));
                pickPosition(String.valueOf(sn.getSelectedItem()), (EditText) views.get(keys.indexOf("x")),
                        (EditText) views.get(keys.indexOf("y")));
            }));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        new AlertDialog.Builder(this).setTitle(sp[1]).setView(sv)
                .setPositiveButton("Aceptar", (d, wh) -> {
                    for (int i = 0; i < views.size(); i++) {
                        View w = views.get(i);
                        String kind = kinds.get(i);
                        String val;
                        if (holders.get(i) != null) {
                            val = holders.get(i)[0];
                        } else if (w instanceof EditText) {
                            val = ((EditText) w).getText().toString();
                            if (kind.equals("N")) val = normNum(val);
                        } else if (w instanceof CheckBox) {
                            val = ((CheckBox) w).isChecked() ? "true" : "false";
                        } else {
                            Spinner s = (Spinner) w;
                            val = String.valueOf(s.getSelectedItem());
                            if ((kind.equals("I") || kind.equals("A") || kind.equals("W")) && s.getSelectedItemPosition() == 0) val = "";
                        }
                        b.f.put(keys.get(i), val);
                    }
                    ref.run();
                }).setNegativeButton("Cancelar", null).show();
    }
}
