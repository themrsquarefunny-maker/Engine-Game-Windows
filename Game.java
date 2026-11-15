package com.mini.engine;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.media.MediaPlayer;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class Game {
    public interface Src {
        InputStream open(String name) throws IOException;
        File file(String name) throws IOException;
    }

    public interface Store {
        String get(String k);
        void put(String k, String v);
        void clear();
    }

    public interface Host {
        void setOrientation(boolean landscape);
    }

    public interface Layer {
        void sync(Game g);
    }

    public static final String[] TYPES = {"Image", "Button", "ImageButton", "Label", "Sound", "AnimatedSprite", "Video", "Camera2D"};

    // ================= Valores de enum =================
    public static class EnumVal {
        public final String type, name;
        public final int idx;

        EnumVal(String t, String n, int i) {
            type = t;
            name = n;
            idx = i;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // ================= Bloques =================
    public static class Blk {
        public String t;
        public HashMap<String, String> f = new HashMap<String, String>();
        public ArrayList<Blk> body = new ArrayList<Blk>();
        public ArrayList<Blk> els = new ArrayList<Blk>();
        String last;
        float acc;
        Task task;

        public Blk(String t) {
            this.t = t;
        }

        public String s(String k) {
            String v = f.get(k);
            return v == null ? "" : v;
        }

        public boolean b(String k) {
            return "true".equals(f.get(k));
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("t", t);
            JSONObject fo = new JSONObject();
            for (Map.Entry<String, String> e : f.entrySet()) fo.put(e.getKey(), e.getValue());
            o.put("f", fo);
            if (!body.isEmpty()) o.put("b", arr(body));
            if (!els.isEmpty()) o.put("e", arr(els));
            return o;
        }

        static JSONArray arr(ArrayList<Blk> l) throws Exception {
            JSONArray a = new JSONArray();
            for (Blk b : l) a.put(b.toJson());
            return a;
        }

        static ArrayList<Blk> list(JSONArray a) throws Exception {
            ArrayList<Blk> l = new ArrayList<Blk>();
            if (a != null) for (int i = 0; i < a.length(); i++) l.add(fromJson(a.getJSONObject(i)));
            return l;
        }

        static Blk fromJson(JSONObject o) throws Exception {
            Blk b = new Blk(o.getString("t"));
            JSONObject fo = o.optJSONObject("f");
            if (fo != null) {
                Iterator<String> it = fo.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    b.f.put(k, fo.getString(k));
                }
            }
            b.body = list(o.optJSONArray("b"));
            b.els = list(o.optJSONArray("e"));
            return b;
        }
    }

    // ================= Nodos =================
    public static class Node {
        public int id;
        public String name = "", type = "", res = "", snd = "", text = "", follow = "";
        public int parent = -1;
        public boolean inherit = true, visible = true, loop, autoplay, fit = true, fixed;
        public boolean current, lim;
        public boolean touchAlways, smart, smartMaj, bold, italic, shadow, wrap = true, vdone;
        public int smartRes = 32, align = 1, valign = 1, color = 0xFFFFFFFF, ocolor = 0xFF000000;
        public float outline = 0f, vol = 1f, vseek;
        public String font = "", vcmd = "";
        TextPaint tpF, tpO;
        StaticLayout lay, layO;
        String layKey;
        int layW;
        public float x, y, w = 200, h = 200, alpha = 1f, fps = 8f, tsize = 36f;
        public float zoom = 1f, smooth = 0f, ll = 0, lt = 0, lr = 1280, lb = 720;
        public ArrayList<String> frames = new ArrayList<String>();
        public ArrayList<Blk> script = new ArrayList<Blk>();
        // tiempo de ejecución
        public float wx, wy, wa;
        public boolean wvis, playing, preview, pressed;
        public int pid = -1, frame;
        public float ft;
        public HashMap<String, Object> vars = new HashMap<String, Object>();
        Node par;
        boolean inOrder, mpWas;
        MediaPlayer mp;
        String mpName;

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("type", type);
            o.put("parent", parent);
            o.put("inh", inherit);
            o.put("vis", visible);
            o.put("loop", loop);
            o.put("auto", autoplay);
            o.put("fit", fit);
            o.put("fixed", fixed);
            o.put("x", x);
            o.put("y", y);
            o.put("w", w);
            o.put("h", h);
            o.put("a", alpha);
            o.put("fps", fps);
            o.put("ts", tsize);
            o.put("res", res);
            o.put("snd", snd);
            o.put("text", text);
            o.put("zoom", zoom);
            o.put("smooth", smooth);
            o.put("follow", follow);
            o.put("cur", current);
            o.put("lim", lim);
            o.put("ll", ll);
            o.put("lt", lt);
            o.put("lr", lr);
            o.put("lb", lb);
            o.put("ta", touchAlways);
            o.put("sm", smart);
            o.put("smr", smartRes);
            o.put("smm", smartMaj);
            o.put("bold", bold);
            o.put("ital", italic);
            o.put("shad", shadow);
            o.put("wrap", wrap);
            o.put("al", align);
            o.put("vl", valign);
            o.put("col", color);
            o.put("oc", ocolor);
            o.put("ow", outline);
            o.put("vol", vol);
            o.put("font", font);
            JSONArray fr = new JSONArray();
            for (String s : frames) fr.put(s);
            o.put("fr", fr);
            o.put("script", Blk.arr(script));
            return o;
        }

        static Node fromJson(JSONObject o) throws Exception {
            Node n = new Node();
            n.id = o.getInt("id");
            n.name = o.optString("name", "Nodo");
            n.type = o.optString("type", "Image");
            n.parent = o.optInt("parent", -1);
            n.inherit = o.optBoolean("inh", true);
            n.visible = o.optBoolean("vis", true);
            n.loop = o.optBoolean("loop", false);
            n.autoplay = o.optBoolean("auto", false);
            n.fit = o.optBoolean("fit", true);
            n.fixed = o.optBoolean("fixed", false);
            n.x = (float) o.optDouble("x", 0);
            n.y = (float) o.optDouble("y", 0);
            n.w = (float) o.optDouble("w", 200);
            n.h = (float) o.optDouble("h", 200);
            n.alpha = (float) o.optDouble("a", 1);
            n.fps = (float) o.optDouble("fps", 8);
            n.tsize = (float) o.optDouble("ts", 36);
            n.res = o.optString("res", "");
            n.snd = o.optString("snd", "");
            n.text = o.optString("text", "");
            n.zoom = (float) o.optDouble("zoom", 1);
            n.smooth = (float) o.optDouble("smooth", 0);
            n.follow = o.optString("follow", "");
            n.current = o.optBoolean("cur", false);
            n.lim = o.optBoolean("lim", false);
            n.ll = (float) o.optDouble("ll", 0);
            n.lt = (float) o.optDouble("lt", 0);
            n.lr = (float) o.optDouble("lr", 1280);
            n.lb = (float) o.optDouble("lb", 720);
            n.touchAlways = o.optBoolean("ta", false);
            n.smart = o.optBoolean("sm", false);
            n.smartRes = o.optInt("smr", 32);
            n.smartMaj = o.optBoolean("smm", false);
            n.bold = o.optBoolean("bold", false);
            n.italic = o.optBoolean("ital", false);
            n.shadow = o.optBoolean("shad", false);
            n.wrap = o.optBoolean("wrap", true);
            n.align = o.optInt("al", 1);
            n.valign = o.optInt("vl", 1);
            n.color = o.optInt("col", 0xFFFFFFFF);
            n.ocolor = o.optInt("oc", 0xFF000000);
            n.outline = (float) o.optDouble("ow", 0);
            n.vol = (float) o.optDouble("vol", 1);
            n.font = o.optString("font", "");
            JSONArray fr = o.optJSONArray("fr");
            if (fr != null) for (int i = 0; i < fr.length(); i++) n.frames.add(fr.getString(i));
            n.script = Blk.list(o.optJSONArray("script"));
            if (n.type.equals("Save")) convertLegacySave(n, o.optJSONArray("sv"));
            return n;
        }

        // Los nodos Save de la versión 2 se convierten en un nodo invisible con bloques equivalentes
        static void convertLegacySave(Node n, JSONArray sa) {
            n.type = "Image";
            n.visible = false;
            Blk st = new Blk("start");
            StringBuilder names = new StringBuilder();
            if (sa != null) {
                for (int i = 0; i < sa.length(); i++) {
                    String[] q = sa.optString(i, "").split(":", 3);
                    if (q.length < 2 || q[0].isEmpty()) continue;
                    Blk v = new Blk("var");
                    v.f.put("name", q[0]);
                    v.f.put("vtype", q[1]);
                    v.f.put("scope", "global");
                    v.f.put("val", q.length > 2 ? q[2] : "");
                    st.body.add(v);
                    if (names.length() > 0) names.append(',');
                    names.append(q[0]);
                }
            }
            if (names.length() > 0) {
                Blk rv = new Blk("recv");
                rv.f.put("msg", "guardar_progreso");
                Blk sv = new Blk("save");
                sv.f.put("names", names.toString());
                rv.body.add(sv);
                n.script.add(0, rv);
                n.script.add(0, st);
            }
        }
    }

    public static class Scene {
        public int id;
        public String name;
        public ArrayList<Node> nodes = new ArrayList<Node>();
        String raw;
    }

    static class Mask {
        int w, h;
        boolean[] b;
    }

    static class Tw {
        Node n;
        int kind;
        float[] from = new float[2], to = new float[2];
        boolean[] has = new boolean[2];
        float t, d;

        void apply(float k) {
            if (kind == 0) {
                n.alpha = from[0] + (to[0] - from[0]) * k;
            } else if (kind == 1) {
                if (has[0]) n.x = from[0] + (to[0] - from[0]) * k;
                if (has[1]) n.y = from[1] + (to[1] - from[1]) * k;
            } else {
                if (has[0]) n.w = from[0] + (to[0] - from[0]) * k;
                if (has[1]) n.h = from[1] + (to[1] - from[1]) * k;
            }
        }
    }

    static class Frame {
        ArrayList<Blk> list;
        int idx, kind, rem;
        boolean chain;
        Blk blk;
    }

    static class Task {
        Node self;
        ArrayList<Frame> stack = new ArrayList<Frame>();
        float wait;
        boolean done, fresh;
    }

    static class Hold {
        Node n;
        Blk b;
        int pid;
        Task task;
    }

    // ================= Estado =================
    public ArrayList<Scene> scenes = new ArrayList<Scene>();
    public Scene curScene;
    public ArrayList<Node> nodes = new ArrayList<Node>();
    public ArrayList<Node> order = new ArrayList<Node>();
    public boolean landscape = true, autosave = true;
    public String stretch = "keep", viewMode = "viewport";
    public int vw = 1280, vh = 720, vwL = 1280, vhL = 720, vwP = 720, vhP = 1280;
    public int bg = 0xFF202020, nextId = 1, nextSceneId = 1, startId = 1;
    public Src src;
    public Store store;
    public Host host;
    public boolean edit;
    public Node sel;
    public float ox, oy, sx = 1, sy = 1;

    HashMap<String, Object> gvars = new HashMap<String, Object>();
    HashMap<String, Object> svars = new HashMap<String, Object>();
    HashMap<String, String[]> enums = new HashMap<String, String[]>();
    HashSet<String> persist = new HashSet<String>();
    HashMap<String, Node> nameMap;
    boolean hasPoll, orientChanged, useCam, snap, camSnap = true, hasVideo;
    public Layer layer;
    public int sceneSerial;
    String pendingScene;
    Node cam;
    float camX, camY, camZ = 1f;
    Matrix camM = new Matrix();
    ArrayDeque<String> msgs = new ArrayDeque<String>();
    ArrayList<Tw> tws = new ArrayList<Tw>();
    ArrayList<Hold> holds = new ArrayList<Hold>();
    ArrayList<Task> tasks = new ArrayList<Task>();
    HashMap<String, Bitmap> cache = new HashMap<String, Bitmap>();
    HashMap<String, Bitmap> thumbs = new HashMap<String, Bitmap>();
    HashMap<String, Mask> masks = new HashMap<String, Mask>();
    HashMap<String, Typeface> tfCache = new HashMap<String, Typeface>();
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    RectF rf = new RectF();

    public Game(Src s) {
        src = s;
        Scene sc = new Scene();
        sc.id = nextSceneId++;
        sc.name = "Escena 1";
        scenes.add(sc);
        startId = sc.id;
        curScene = sc;
        nodes = sc.nodes;
    }

    public void applyOrientation(boolean land) {
        landscape = land;
        vw = land ? vwL : vwP;
        vh = land ? vhL : vhP;
    }

    // ================= JSON =================
    public String toJson() throws Exception {
        return toJson(null);
    }

    public String toJson(Set<Integer> keep) throws Exception {
        JSONObject o = new JSONObject();
        o.put("land", landscape);
        o.put("st", stretch);
        o.put("vm", viewMode);
        o.put("vwL", vwL);
        o.put("vhL", vhL);
        o.put("vwP", vwP);
        o.put("vhP", vhP);
        o.put("bg", bg);
        o.put("asave", autosave);
        o.put("next", nextId);
        o.put("nextScene", nextSceneId);
        o.put("start", startId);
        JSONArray sa = new JSONArray();
        for (Scene s : scenes) {
            if (keep != null && !keep.contains(s.id)) continue;
            JSONObject so = new JSONObject();
            so.put("id", s.id);
            so.put("name", s.name);
            JSONArray na = new JSONArray();
            for (Node n : s.nodes) na.put(n.toJson());
            so.put("nodes", na);
            sa.put(so);
        }
        o.put("scenes", sa);
        LinkedHashMap<String, String[]> en = new LinkedHashMap<String, String[]>();
        LinkedHashSet<String> ps = new LinkedHashSet<String>();
        collectDecls(en, ps, keep);
        JSONObject eo = new JSONObject();
        for (Map.Entry<String, String[]> e : en.entrySet()) {
            JSONArray v = new JSONArray();
            for (String x : e.getValue()) v.put(x);
            eo.put(e.getKey(), v);
        }
        o.put("enums", eo);
        JSONArray pa = new JSONArray();
        for (String s : ps) pa.put(s);
        o.put("persist", pa);
        return o.toString();
    }

    static ArrayList<Node> parseNodes(JSONArray a) throws Exception {
        ArrayList<Node> l = new ArrayList<Node>();
        if (a != null) for (int i = 0; i < a.length(); i++) l.add(Node.fromJson(a.getJSONObject(i)));
        return l;
    }

    public static Game fromJson(String json, Src s, boolean runtime) throws Exception {
        JSONObject o = new JSONObject(json);
        Game g = new Game(s);
        g.scenes.clear();
        g.landscape = o.optBoolean("land", true);
        g.stretch = o.optString("st", "keep");
        g.viewMode = o.optString("vm", "viewport");
        g.autosave = o.optBoolean("asave", true);
        g.bg = o.optInt("bg", 0xFF202020);
        if (o.has("vwL")) {
            g.vwL = Math.max(16, o.optInt("vwL", 1280));
            g.vhL = Math.max(16, o.optInt("vhL", 720));
            g.vwP = Math.max(16, o.optInt("vwP", 720));
            g.vhP = Math.max(16, o.optInt("vhP", 1280));
        } else {
            int w = Math.max(16, o.optInt("vw", 1280)), h = Math.max(16, o.optInt("vh", 720));
            if (g.landscape) {
                g.vwL = w;
                g.vhL = h;
                g.vwP = h;
                g.vhP = w;
            } else {
                g.vwP = w;
                g.vhP = h;
                g.vwL = h;
                g.vhL = w;
            }
        }
        g.applyOrientation(g.landscape);
        JSONArray sa = o.optJSONArray("scenes");
        if (sa != null) {
            for (int i = 0; i < sa.length(); i++) {
                JSONObject so = sa.getJSONObject(i);
                Scene sc = new Scene();
                sc.id = so.getInt("id");
                sc.name = so.optString("name", "Escena " + (i + 1));
                if (runtime) sc.raw = so.toString();
                else sc.nodes = parseNodes(so.optJSONArray("nodes"));
                g.scenes.add(sc);
            }
        } else {
            Scene sc = new Scene();
            sc.id = 1;
            sc.name = "Escena 1";
            JSONArray na = o.optJSONArray("nodes");
            if (runtime) {
                JSONObject so = new JSONObject();
                so.put("nodes", na == null ? new JSONArray() : na);
                sc.raw = so.toString();
            } else {
                sc.nodes = parseNodes(na);
            }
            g.scenes.add(sc);
        }
        if (g.scenes.isEmpty()) {
            Scene sc = new Scene();
            sc.id = 1;
            sc.name = "Escena 1";
            g.scenes.add(sc);
        }
        int maxScene = 0, maxNode = 0;
        for (Scene sc : g.scenes) {
            maxScene = Math.max(maxScene, sc.id);
            for (Node n : sc.nodes) maxNode = Math.max(maxNode, n.id);
        }
        g.nextSceneId = Math.max(o.optInt("nextScene", 1), maxScene + 1);
        g.nextId = Math.max(o.optInt("next", 1), maxNode + 1);
        g.startId = o.optInt("start", g.scenes.get(0).id);
        if (g.sceneById(g.startId) == null) g.startId = g.scenes.get(0).id;
        g.curScene = g.sceneById(g.startId);
        g.nodes = g.curScene.nodes;
        JSONObject eo = o.optJSONObject("enums");
        if (eo != null) {
            Iterator<String> it = eo.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONArray va = eo.getJSONArray(k);
                String[] vs = new String[va.length()];
                for (int i = 0; i < vs.length; i++) vs[i] = va.getString(i);
                g.enums.put(k, vs);
            }
        }
        JSONArray pa = o.optJSONArray("persist");
        if (pa != null) for (int i = 0; i < pa.length(); i++) g.persist.add(pa.getString(i));
        if (!runtime) g.rebuild();
        return g;
    }

    public static Game fromJson(String json, Src s) throws Exception {
        return fromJson(json, s, false);
    }

    // ================= Escenas =================
    public Scene sceneById(int id) {
        for (Scene s : scenes) if (s.id == id) return s;
        return null;
    }

    public Scene sceneByName(String n) {
        for (Scene s : scenes) if (s.name.equals(n)) return s;
        return null;
    }

    String uniqueSceneName(String base) {
        String nm = base;
        int k = 2;
        while (sceneByName(nm) != null) nm = base + " " + (k++);
        return nm;
    }

    public ArrayList<Node> allNodes() {
        return allNodes(null);
    }

    public ArrayList<Node> allNodes(Set<Integer> keep) {
        ArrayList<Node> l = new ArrayList<Node>();
        for (Scene s : scenes) if (keep == null || keep.contains(s.id)) l.addAll(s.nodes);
        return l;
    }

    // Escenas a las que se puede llegar desde la inicial (el exportador omite las demás)
    public HashSet<Integer> reachableScenes() {
        HashSet<Integer> keep = new HashSet<Integer>();
        ArrayList<Scene> q = new ArrayList<Scene>();
        Scene st = sceneById(startId);
        if (st == null) st = scenes.get(0);
        keep.add(st.id);
        q.add(st);
        for (int i = 0; i < q.size(); i++) for (Node n : q.get(i).nodes) sceneRefs(n.script, q, keep);
        return keep;
    }

    void sceneRefs(ArrayList<Blk> l, ArrayList<Scene> q, HashSet<Integer> keep) {
        for (Blk b : l) {
            if (b.t.equals("scene")) {
                Scene s = sceneByName(b.s("scene"));
                if (s != null && keep.add(s.id)) q.add(s);
            }
            sceneRefs(b.body, q, keep);
            sceneRefs(b.els, q, keep);
        }
    }

    public Scene addScene(String name) {
        Scene s = new Scene();
        s.id = nextSceneId++;
        s.name = uniqueSceneName(name);
        scenes.add(s);
        return s;
    }

    public Scene duplicateScene(Scene src0, String name) throws Exception {
        JSONArray a = new JSONArray();
        for (Node n : src0.nodes) a.put(n.toJson());
        ArrayList<Node> copy = parseNodes(a);
        HashMap<Integer, Integer> map = new HashMap<Integer, Integer>();
        for (Node n : copy) {
            int nid = nextId++;
            map.put(n.id, nid);
            n.id = nid;
        }
        for (Node n : copy) {
            if (n.parent >= 0) {
                Integer np = map.get(n.parent);
                n.parent = np == null ? -1 : np;
            }
        }
        Scene s = new Scene();
        s.id = nextSceneId++;
        s.name = uniqueSceneName(name);
        s.nodes = copy;
        scenes.add(s);
        return s;
    }

    public boolean removeScene(Scene s) {
        if (scenes.size() <= 1) return false;
        scenes.remove(s);
        if (startId == s.id) startId = scenes.get(0).id;
        if (curScene == s) openScene(scenes.get(0));
        return true;
    }

    // Abre una escena en el editor
    public void openScene(Scene s) {
        curScene = s;
        nodes = s.nodes;
        sel = null;
        rebuild();
    }

    public void renameSceneRefs(String from, String to) {
        for (Node n : allNodes()) renameWalk(n.script, from, to);
    }

    void renameWalk(ArrayList<Blk> l, String from, String to) {
        for (Blk b : l) {
            if (b.t.equals("scene") && b.s("scene").equals(from)) b.f.put("scene", to);
            renameWalk(b.body, from, to);
            renameWalk(b.els, from, to);
        }
    }

    // Declaraciones del proyecto: enums definidos y variables que se guardan
    public void collectDecls(LinkedHashMap<String, String[]> en, LinkedHashSet<String> ps) {
        collectDecls(en, ps, null);
    }

    public void collectDecls(LinkedHashMap<String, String[]> en, LinkedHashSet<String> ps, Set<Integer> keep) {
        for (Node n : allNodes(keep)) declWalk(n.script, en, ps);
    }

    void declWalk(ArrayList<Blk> l, LinkedHashMap<String, String[]> en, LinkedHashSet<String> ps) {
        for (Blk b : l) {
            if (b.t.equals("enum") && !b.s("name").trim().isEmpty()) {
                ArrayList<String> vs = new ArrayList<String>();
                for (String x : b.s("values").split(",")) if (!x.trim().isEmpty()) vs.add(x.trim());
                en.put(b.s("name").trim(), vs.toArray(new String[0]));
            }
            if (b.t.equals("save")) {
                for (String x : b.s("names").split(",")) if (!x.trim().isEmpty()) ps.add(x.trim());
            }
            declWalk(b.body, en, ps);
            declWalk(b.els, en, ps);
        }
    }

    // ================= Árbol =================
    public Node byId(int id) {
        for (Node n : nodes) if (n.id == id) return n;
        return null;
    }

    public Node byName(String s) {
        if (nameMap != null) return nameMap.get(s);
        for (Node n : nodes) if (n.name.equals(s)) return n;
        return null;
    }

    public boolean isDesc(Node anc, Node c) {
        int guard = 0;
        for (Node q = c; q != null && guard < 500; guard++) {
            if (q == anc) return true;
            q = q.parent >= 0 ? byId(q.parent) : null;
        }
        return false;
    }

    public Node add(String type) {
        Node n = new Node();
        n.id = nextId++;
        n.type = type;
        int c = 1;
        String nm = type + c;
        while (byName(nm) != null) {
            c++;
            nm = type + c;
        }
        n.name = nm;
        if (type.equals("Button")) {
            n.w = 300;
            n.h = 100;
            n.text = "Botón";
        } else if (type.equals("Sound")) {
            n.w = 90;
            n.h = 90;
        } else if (type.equals("Camera2D")) {
            n.w = 60;
            n.h = 60;
            n.lr = vw;
            n.lb = vh;
            boolean any = false;
            for (Node q : nodes) if (q.type.equals("Camera2D")) any = true;
            n.current = !any;
        } else if (type.equals("AnimatedSprite")) {
            n.loop = true;
            n.autoplay = true;
        } else if (type.equals("Label")) {
            n.w = 400;
            n.h = 100;
            n.text = "Texto";
            n.tsize = 40;
        } else if (type.equals("Video")) {
            n.w = 640;
            n.h = 360;
            n.autoplay = true;
        }
        n.x = vw / 2f - n.w / 2f;
        n.y = vh / 2f - n.h / 2f;
        nodes.add(n);
        rebuild();
        return n;
    }

    public void remove(Node n) {
        ArrayList<Node> del = new ArrayList<Node>();
        for (Node q : nodes) if (isDesc(n, q)) del.add(q);
        for (Node q : del) {
            if (q.mp != null) {
                try {
                    q.mp.release();
                } catch (Exception e) {
                }
            }
            nodes.remove(q);
        }
        if (sel != null && del.contains(sel)) sel = null;
        rebuild();
    }

    public void reparent(Node n, int np) {
        Node pn = np >= 0 ? byId(np) : null;
        compute();
        if (n.inherit) {
            if (pn != null) {
                n.x = n.wx - pn.wx;
                n.y = n.wy - pn.wy;
            } else if (n.parent >= 0) {
                n.x = n.wx;
                n.y = n.wy;
            }
        }
        n.parent = np;
        rebuild();
        compute();
    }

    public void rebuild() {
        order.clear();
        for (Node n : nodes) {
            n.inOrder = false;
            n.par = n.parent >= 0 ? byId(n.parent) : null;
        }
        addOrder(-1);
    }

    void addOrder(int pid) {
        for (Node n : nodes) {
            if (n.inOrder) continue;
            boolean root = pid == -1 ? (n.parent == -1 || n.par == null) : n.parent == pid;
            if (!root) continue;
            n.inOrder = true;
            order.add(n);
            addOrder(n.id);
        }
    }

    void compute() {
        for (int i = 0; i < order.size(); i++) {
            Node n = order.get(i);
            Node pn = n.par;
            if (pn != null && n.inherit) {
                n.wx = pn.wx + n.x;
                n.wy = pn.wy + n.y;
                n.wa = pn.wa * n.alpha;
                n.wvis = pn.wvis && n.visible;
            } else {
                n.wx = n.x;
                n.wy = n.y;
                n.wa = n.alpha;
                n.wvis = n.visible;
            }
        }
    }

    // ================= Información del proyecto =================
    public HashSet<String> usedAssets() {
        return usedAssets(null);
    }

    public HashSet<String> usedAssets(Set<Integer> keep) {
        HashSet<String> s = new HashSet<String>();
        for (Node n : allNodes(keep)) {
            if (!n.font.isEmpty()) s.add(n.font);
            if (!n.res.isEmpty()) s.add(n.res);
            if (!n.snd.isEmpty()) s.add(n.snd);
            s.addAll(n.frames);
            collect(n.script, s);
        }
        return s;
    }

    void collect(ArrayList<Blk> l, HashSet<String> s) {
        for (Blk b : l) {
            String a = b.s("asset");
            if (!a.isEmpty()) s.add(a);
            collect(b.body, s);
            collect(b.els, s);
        }
    }

    public HashMap<String, float[]> imageSizes() {
        return imageSizes(null);
    }

    public HashMap<String, float[]> imageSizes(Set<Integer> keep) {
        HashMap<String, float[]> m = new HashMap<String, float[]>();
        ArrayList<Node> all = allNodes(keep);
        for (Node n : all) {
            if (!n.res.isEmpty()) putSize(m, n.res, n.w, n.h);
            for (String s : n.frames) putSize(m, s, n.w, n.h);
        }
        for (Node n : all) markUnknown(n.script, m);
        return m;
    }

    static void putSize(HashMap<String, float[]> m, String k, float w, float h) {
        float[] v = m.get(k);
        if (v == null) {
            m.put(k, new float[]{w, h});
        } else {
            v[0] = Math.max(v[0], w);
            v[1] = Math.max(v[1], h);
        }
    }

    void markUnknown(ArrayList<Blk> l, HashMap<String, float[]> m) {
        for (Blk b : l) {
            if (b.t.equals("setimg") && !b.s("asset").isEmpty()) m.put(b.s("asset"), new float[]{-1, -1});
            markUnknown(b.body, m);
            markUnknown(b.els, m);
        }
    }

    public boolean hasSizeTween() {
        return hasSizeTween(null);
    }

    public boolean hasSizeTween(Set<Integer> keep) {
        for (Node n : allNodes(keep)) if (sizeTween(n.script)) return true;
        return false;
    }

    boolean sizeTween(ArrayList<Blk> l) {
        for (Blk b : l) {
            if (b.t.equals("tween") && !b.s("what").equals("modulate") && !b.s("what").equals("mover")) return true;
            if (sizeTween(b.body) || sizeTween(b.els)) return true;
        }
        return false;
    }

    // ================= Recursos =================
    public Bitmap img(String name) {
        if (name == null || name.isEmpty()) return null;
        if (cache.containsKey(name)) return cache.get(name);
        Bitmap b = null;
        try (InputStream i = src.open(name)) {
            b = BitmapFactory.decodeStream(i);
        } catch (Exception e) {
        }
        cache.put(name, b);
        return b;
    }

    public void flush() {
        cache.clear();
    }

    public void fitNode(Node n) {
        if (n.type.equals("Video")) {
            int[] d = vdims(n.res);
            if (d != null) {
                n.w = d[0];
                n.h = d[1];
            }
            return;
        }
        String a = n.type.equals("AnimatedSprite") ? (n.frames.isEmpty() ? "" : n.frames.get(0)) : n.res;
        Bitmap b = img(a);
        if (b != null) {
            n.w = b.getWidth();
            n.h = b.getHeight();
        }
    }

    int[] vdims(String name) {
        if (name == null || name.isEmpty()) return null;
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(src.file(name).getPath());
            int w = Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int h = Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            String rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (rot != null && (rot.equals("90") || rot.equals("270"))) {
                int t = w;
                w = h;
                h = t;
            }
            return new int[]{w, h};
        } catch (Exception e) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Exception e2) {
            }
        }
    }

    Bitmap vthumb(String name) {
        if (name == null || name.isEmpty()) return null;
        if (thumbs.containsKey(name)) return thumbs.get(name);
        Bitmap b = null;
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(src.file(name).getPath());
            b = r.getFrameAtTime(0);
            if (b != null && b.getWidth() > 480) b = Bitmap.createScaledBitmap(b, 480, Math.max(1, b.getHeight() * 480 / b.getWidth()), true);
        } catch (Exception e) {
            b = null;
        } finally {
            try {
                r.release();
            } catch (Exception e2) {
            }
        }
        thumbs.put(name, b);
        return b;
    }

    public void playSound(Node n) {
        if (n.snd == null || n.snd.isEmpty()) return;
        try {
            if (n.mp == null || !n.snd.equals(n.mpName)) {
                if (n.mp != null) n.mp.release();
                n.mp = new MediaPlayer();
                n.mp.setDataSource(src.file(n.snd).getPath());
                n.mp.prepare();
                n.mpName = n.snd;
            }
            n.mp.setLooping(n.loop);
            n.mp.seekTo(0);
            n.mp.start();
        } catch (Exception e) {
            try {
                if (n.mp != null) n.mp.release();
            } catch (Exception e2) {
            }
            n.mp = null;
        }
    }

    public void stopSound(Node n) {
        try {
            if (n.mp != null && n.mp.isPlaying()) {
                n.mp.pause();
                n.mp.seekTo(0);
            }
        } catch (Exception e) {
        }
    }

    public void pauseAudio() {
        for (Node n : nodes) {
            try {
                if (n.mp != null && n.mp.isPlaying()) {
                    n.mp.pause();
                    n.mpWas = true;
                }
            } catch (Exception e) {
            }
        }
    }

    public void resumeAudio() {
        for (Node n : nodes) {
            try {
                if (n.mpWas && n.mp != null) n.mp.start();
            } catch (Exception e) {
            }
            n.mpWas = false;
        }
    }

    public void release() {
        for (Node n : nodes) {
            if (n.mp != null) {
                try {
                    n.mp.release();
                } catch (Exception e) {
                }
                n.mp = null;
            }
        }
    }

    // ================= Variables =================
    static String enc(Object o) {
        if (o instanceof Long) return "i:" + o;
        if (o instanceof Double) return "f:" + o;
        if (o instanceof Boolean) return "b:" + o;
        if (o instanceof EnumVal) return "e:" + ((EnumVal) o).type + ":" + ((EnumVal) o).name;
        return "s:" + o;
    }

    Object dec(String raw) {
        if (raw == null || raw.length() < 2) return null;
        String v = raw.substring(2);
        try {
            switch (raw.charAt(0)) {
                case 'i': return Long.valueOf(v);
                case 'f': return Double.valueOf(v);
                case 'b': return Boolean.valueOf(v);
                case 'e': {
                    String[] q = v.split(":", 2);
                    String[] vals = enums.get(q[0]);
                    if (vals == null || q.length < 2) return null;
                    for (int i = 0; i < vals.length; i++) if (vals[i].equals(q[1])) return new EnumVal(q[0], q[1], i);
                    return null;
                }
                default: return v;
            }
        } catch (Exception e) {
            return null;
        }
    }

    Object castEnum(String type, Object v) {
        String[] vals = enums.get(type);
        if (vals == null || vals.length == 0) return String.valueOf(v);
        String s = String.valueOf(v);
        int d = s.lastIndexOf('.');
        if (d >= 0) s = s.substring(d + 1);
        for (int i = 0; i < vals.length; i++) if (vals[i].equals(s)) return new EnumVal(type, s, i);
        return new EnumVal(type, vals[0], 0);
    }

    Object castType(String type, Object v) {
        switch (type) {
            case "int": return Long.valueOf((long) num(v));
            case "float": return Double.valueOf(num(v));
            case "bool": return Boolean.valueOf(truthy(v));
            case "string": return String.valueOf(v);
            default: return castEnum(type, v);
        }
    }

    Object castLike(Object cur, Object v) {
        if (cur instanceof Long) return Long.valueOf((long) num(v));
        if (cur instanceof Double) return Double.valueOf(num(v));
        if (cur instanceof Boolean) return Boolean.valueOf(truthy(v));
        if (cur instanceof EnumVal) return castEnum(((EnumVal) cur).type, v);
        return String.valueOf(v);
    }

    Object getVar(String name, Node self) {
        if (self != null && self.vars.containsKey(name)) return self.vars.get(name);
        if (svars.containsKey(name)) return svars.get(name);
        return gvars.get(name);
    }

    void putVar(String name, Object v, Node self) {
        if (self != null && self.vars.containsKey(name)) self.vars.put(name, v);
        else if (svars.containsKey(name)) svars.put(name, v);
        else gvars.put(name, v);
    }

    public void autoSave() {
        if (!autosave || store == null) return;
        for (String nm : persist) {
            Object v = svars.containsKey(nm) ? svars.get(nm) : gvars.get(nm);
            if (v != null) store.put(nm, enc(v));
        }
    }

    // ================= Valores y lógica =================
    static double num(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof Boolean) return ((Boolean) o) ? 1 : 0;
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    static boolean truthy(Object o) {
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) return ((Number) o).doubleValue() != 0;
        String s = String.valueOf(o);
        return !s.isEmpty() && !s.equals("false");
    }

    static boolean isInt(String s) {
        int n = s.length();
        int i = s.charAt(0) == '-' ? 1 : 0;
        if (i == n) return false;
        for (; i < n; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    static boolean isDec(String s) {
        int n = s.length();
        int i = s.charAt(0) == '-' ? 1 : 0;
        boolean dot = false, dig = false;
        for (; i < n; i++) {
            char c = s.charAt(i);
            if (c == '.') {
                if (dot) return false;
                dot = true;
            } else if (c >= '0' && c <= '9') {
                dig = true;
            } else {
                return false;
            }
        }
        return dot && dig;
    }

    Object prop(Node n, String pr) {
        switch (pr) {
            case "x": return Double.valueOf(n.x);
            case "y": return Double.valueOf(n.y);
            case "w": return Double.valueOf(n.w);
            case "h": return Double.valueOf(n.h);
            case "alpha":
            case "modulate": return Double.valueOf(n.alpha);
            case "visible": return Boolean.valueOf(n.visible);
            case "text": return n.text;
            case "fps": return Double.valueOf(n.fps);
            case "frame": return Long.valueOf(n.frame);
            case "zoom": return Double.valueOf(n.zoom);
            default: return null;
        }
    }

    Object val(String s, Node self) {
        s = s.trim();
        if (s.isEmpty()) return "";
        if (s.equals("true")) return Boolean.TRUE;
        if (s.equals("false")) return Boolean.FALSE;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        try {
            if (isInt(s)) return Long.valueOf(s);
            if (isDec(s)) return Double.valueOf(s);
        } catch (Exception e) {
        }
        Object gv = getVar(s, self);
        if (gv != null) return gv;
        int d = s.indexOf('.');
        if (d > 0) {
            String nn = s.substring(0, d), rest = s.substring(d + 1);
            String[] ev = enums.get(nn);
            if (ev != null) {
                for (int i = 0; i < ev.length; i++) if (ev[i].equals(rest)) return new EnumVal(nn, rest, i);
            }
            Node n = nn.equals("self") ? self : byName(nn);
            if (n != null) {
                Object v = prop(n, rest);
                if (v == null) v = n.vars.get(rest);
                if (v != null) return v;
            }
        }
        return s;
    }

    boolean cond(Blk b, Node self) {
        Object l = val(b.s("l"), self);
        boolean res;
        if (b.s("r").trim().isEmpty()) {
            res = truthy(l);
        } else {
            Object r = val(b.s("r"), self);
            String op = b.s("op");
            if (l instanceof Number && r instanceof Number) {
                double x = num(l), y = num(r);
                switch (op) {
                    case "<": res = x < y; break;
                    case ">": res = x > y; break;
                    case "<=": res = x <= y; break;
                    case ">=": res = x >= y; break;
                    case "!=": res = x != y; break;
                    default: res = x == y;
                }
            } else {
                boolean eq = String.valueOf(l).equals(String.valueOf(r));
                if (op.equals("!=")) res = !eq;
                else if (op.equals("=") || op.isEmpty()) res = eq;
                else res = false;
            }
        }
        return b.b("not") ? !res : res;
    }

    Node tgt(Blk b, Node self) {
        String n = b.s("node").trim();
        if (n.isEmpty() || n.equals("self")) return self;
        return byName(n);
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    void setOrient(boolean land) {
        if (land == landscape) return;
        applyOrientation(land);
        if (host != null) host.setOrientation(land);
        orientChanged = true;
    }

    void exec(Blk b, Node self) {
        Node t = tgt(b, self);
        if (b.f.containsKey("node") && t == null) return;
        switch (b.t) {
            case "send":
                msgs.add(b.s("msg"));
                break;
            case "var": {
                String name = b.s("name").trim();
                if (name.isEmpty()) break;
                String type = b.s("vtype");
                Object v = castType(type, val(b.s("val"), self));
                if (store != null && persist.contains(name)) {
                    Object st = dec(store.get(name));
                    if (st != null) v = castType(type, st);
                }
                String sc = b.s("scope");
                if (sc.equals("nodo")) self.vars.put(name, v);
                else if (sc.equals("escena")) svars.put(name, v);
                else gvars.put(name, v);
                break;
            }
            case "setvar": {
                String name = b.s("name").trim();
                Object cur = getVar(name, self);
                if (cur != null) putVar(name, castLike(cur, val(b.s("val"), self)), self);
                break;
            }
            case "math": {
                String name = b.s("name").trim();
                Object cur = getVar(name, self);
                if (cur == null) cur = Long.valueOf(0);
                Object v = val(b.s("val"), self);
                double x = num(cur), y = num(v), r;
                switch (b.s("op")) {
                    case "-": r = x - y; break;
                    case "*": r = x * y; break;
                    case "/": r = y == 0 ? 0 : x / y; break;
                    default: r = x + y;
                }
                if (cur instanceof Long && v instanceof Long) putVar(name, Long.valueOf((long) r), self);
                else putVar(name, Double.valueOf(r), self);
                break;
            }
            case "enumnext": {
                String name = b.s("name").trim();
                Object cur = getVar(name, self);
                if (cur instanceof EnumVal) {
                    EnumVal e = (EnumVal) cur;
                    String[] vals = enums.get(e.type);
                    if (vals != null && vals.length > 0) {
                        int i = e.idx + (b.s("dir").equals("anterior") ? -1 : 1);
                        if (b.b("wrap")) i = (i % vals.length + vals.length) % vals.length;
                        else i = Math.max(0, Math.min(vals.length - 1, i));
                        putVar(name, new EnumVal(e.type, vals[i], i), self);
                    }
                }
                break;
            }
            case "save":
                if (store != null) {
                    for (String nm : b.s("names").split(",")) {
                        nm = nm.trim();
                        if (nm.isEmpty()) continue;
                        Object v = getVar(nm, self);
                        if (v != null) store.put(nm, enc(v));
                    }
                }
                break;
            case "clearsave":
                if (store != null) store.clear();
                break;
            case "scene":
                pendingScene = b.s("scene");
                break;
            case "orientset":
                setOrient(!b.s("mode").equals("vertical"));
                break;
            case "viewmode":
                viewMode = b.s("mode").equals("viewport") ? "viewport" : "camera";
                camSnap = true;
                break;
            case "camset":
                if (t.type.equals("Camera2D")) {
                    String what = b.s("what");
                    if (what.equals("zoom")) t.zoom = Math.max(0.05f, (float) num(val(b.s("val"), self)));
                    else if (what.equals("seguir a")) t.follow = b.s("val").trim();
                    else if (what.equals("suavizado")) t.smooth = Math.max(0f, (float) num(val(b.s("val"), self)));
                    else if (what.equals("activar")) {
                        cam = t;
                        camSnap = true;
                    }
                }
                break;
            case "setimg":
                t.res = b.s("asset");
                if (t.fit) fitNode(t);
                break;
            case "setsnd":
                t.snd = b.s("asset");
                break;
            case "play":
                if (t.type.equals("Sound")) playSound(t);
                else if (t.type.equals("AnimatedSprite")) t.playing = true;
                else if (t.type.equals("Video")) t.vcmd = "play";
                break;
            case "stop":
                if (t.type.equals("Sound")) stopSound(t);
                else if (t.type.equals("AnimatedSprite")) t.playing = false;
                else if (t.type.equals("Video")) t.vcmd = "stop";
                break;
            case "vpause":
                if (t.type.equals("Video")) t.vcmd = "pause";
                break;
            case "vseek":
                if (t.type.equals("Video")) {
                    t.vseek = (float) num(val(b.s("secs"), self));
                    t.vcmd = "seek";
                }
                break;
            case "vvol":
                t.vol = clamp01((float) num(val(b.s("val"), self)));
                break;
            case "setvideo":
                if (t.type.equals("Video")) {
                    t.res = b.s("asset");
                    t.vcmd = "reload";
                }
                break;
            case "mod":
                t.alpha = clamp01((float) num(val(b.s("val"), self)));
                if (b.f.containsKey("touch")) t.touchAlways = b.b("touch");
                break;
            case "vis":
                t.visible = truthy(val(b.s("val"), self));
                break;
            case "pos":
                if (!b.s("x").trim().isEmpty()) t.x = (float) num(val(b.s("x"), self));
                if (!b.s("y").trim().isEmpty()) t.y = (float) num(val(b.s("y"), self));
                break;
            case "text":
                t.text = String.valueOf(val(b.s("val"), self));
                break;
            case "tween": {
                Tw w = new Tw();
                w.n = t;
                w.d = (float) num(val(b.s("secs"), self));
                String what = b.s("what");
                if (what.equals("modulate")) {
                    w.kind = 0;
                    w.from[0] = t.alpha;
                    w.to[0] = clamp01((float) num(val(b.s("x"), self)));
                } else {
                    boolean mv = what.equals("mover");
                    w.kind = mv ? 1 : 2;
                    w.from[0] = mv ? t.x : t.w;
                    w.from[1] = mv ? t.y : t.h;
                    w.has[0] = !b.s("x").trim().isEmpty();
                    w.has[1] = !b.s("y").trim().isEmpty();
                    w.to[0] = (float) num(val(b.s("x"), self));
                    w.to[1] = (float) num(val(b.s("y"), self));
                }
                for (int i = tws.size() - 1; i >= 0; i--) {
                    if (tws.get(i).n == t && tws.get(i).kind == w.kind) tws.remove(i);
                }
                if (what.equals("modulate") && b.f.containsKey("touch")) t.touchAlways = b.b("touch");
                if (w.d <= 0) w.apply(1f);
                else tws.add(w);
                break;
            }
            default:
                break;
        }
    }

    // ================= Ejecutor de scripts (con esperas y bucles) =================
    void push(Task t, ArrayList<Blk> list, int kind, int rem, Blk blk) {
        Frame f = new Frame();
        f.list = list;
        f.kind = kind;
        f.rem = rem;
        f.blk = blk;
        t.stack.add(f);
    }

    Task spawn(ArrayList<Blk> list, Node self) {
        Task t = new Task();
        t.self = self;
        t.fresh = true;
        push(t, list, 0, 0, null);
        tasks.add(t);
        step(t);
        return t;
    }

    void step(Task t) {
        int budget = 3000;
        while (!t.done && t.wait <= 0f && budget-- > 0) {
            if (t.stack.isEmpty()) {
                t.done = true;
                break;
            }
            Frame f = t.stack.get(t.stack.size() - 1);
            if (f.idx >= f.list.size()) {
                boolean again = false;
                if (f.kind == 1) {
                    f.rem--;
                    again = f.rem > 0;
                } else if (f.kind == 2) {
                    again = true;
                } else if (f.kind == 3) {
                    again = cond(f.blk, t.self);
                }
                if (again) {
                    f.idx = 0;
                    f.chain = false;
                } else {
                    t.stack.remove(t.stack.size() - 1);
                }
                continue;
            }
            Blk b = f.list.get(f.idx++);
            switch (b.t) {
                case "if":
                    f.chain = cond(b, t.self);
                    if (f.chain) push(t, b.body, 0, 0, null);
                    break;
                case "elif":
                    if (!f.chain && cond(b, t.self)) {
                        f.chain = true;
                        push(t, b.body, 0, 0, null);
                    }
                    break;
                case "else":
                    if (!f.chain) push(t, b.body, 0, 0, null);
                    f.chain = true;
                    break;
                case "wait":
                    t.wait = Math.max(0.0001f, (float) num(val(b.s("secs"), t.self)));
                    break;
                case "repeat": {
                    int n = (int) num(val(b.s("n"), t.self));
                    if (n > 0) push(t, b.body, 1, n, b);
                    break;
                }
                case "forever":
                    push(t, b.body, 2, 0, b);
                    break;
                case "while":
                    if (cond(b, t.self)) push(t, b.body, 3, 0, b);
                    break;
                case "break":
                    while (!t.stack.isEmpty()) {
                        Frame x = t.stack.remove(t.stack.size() - 1);
                        if (x.kind != 0) break;
                    }
                    break;
                default:
                    exec(b, t.self);
                    f.chain = false;
                    if (pendingScene != null) t.done = true;
            }
        }
        if (!t.done && t.wait <= 0f && budget <= 0) t.wait = 0.0001f;
    }

    void pump() {
        int guard = 0;
        while (!msgs.isEmpty() && guard++ < 500) {
            String m = msgs.poll().trim();
            for (Node n : nodes) {
                for (int i = 0; i < n.script.size(); i++) {
                    Blk b = n.script.get(i);
                    if (b.t.equals("recv") && b.s("msg").trim().equals(m)) spawn(b.body, n);
                }
            }
        }
    }

    // ================= Escenas y ciclo de juego =================
    public void start() {
        gvars.clear();
        Scene s = sceneById(startId);
        if (s == null && !scenes.isEmpty()) s = scenes.get(0);
        if (s != null) enterScene(s);
    }

    void enterScene(Scene sc) {
        release();
        curScene = sc;
        if (!edit && sc.raw != null) {
            try {
                nodes = parseNodes(new JSONObject(sc.raw).optJSONArray("nodes"));
            } catch (Exception e) {
                nodes = new ArrayList<Node>();
            }
        } else {
            nodes = sc.nodes;
        }
        svars.clear();
        msgs.clear();
        tws.clear();
        holds.clear();
        tasks.clear();
        cache.clear();
        pendingScene = null;
        rebuild();
        nameMap = new HashMap<String, Node>();
        hasPoll = false;
        cam = null;
        camSnap = true;
        hasVideo = false;
        sceneSerial++;
        for (Node n : nodes) {
            nameMap.put(n.name, n);
            if (n.type.equals("Video")) hasVideo = true;
            n.playing = n.autoplay;
            n.frame = 0;
            n.ft = 0;
            n.pressed = false;
            n.pid = -1;
            if (n.type.equals("Camera2D") && (cam == null || (n.current && !cam.current))) cam = n;
            for (Blk b : n.script) {
                b.last = null;
                b.acc = 0;
                b.task = null;
                if (b.t.equals("frame") || b.t.equals("change") || b.t.equals("every")) hasPoll = true;
            }
        }
        for (Node n : nodes) if (n.type.equals("Sound") && n.autoplay) playSound(n);
        for (Node n : nodes) {
            for (int i = 0; i < n.script.size(); i++) {
                Blk b = n.script.get(i);
                if (b.t.equals("start")) spawn(b.body, n);
            }
        }
        pump();
    }

    public boolean active() {
        if (edit || hasPoll || hasVideo || pendingScene != null || orientChanged || useCam) return true;
        if (!tasks.isEmpty() || !tws.isEmpty() || !holds.isEmpty() || !msgs.isEmpty()) return true;
        for (Node n : nodes) {
            if (n.playing && n.type.equals("AnimatedSprite") && !n.frames.isEmpty()) return true;
        }
        return false;
    }

    void animate(float dt) {
        for (int k = 0; k < nodes.size(); k++) {
            Node n = nodes.get(k);
            if (!n.type.equals("AnimatedSprite") || n.frames.isEmpty()) continue;
            boolean on = edit ? n.preview : n.playing;
            if (!on) continue;
            float spf = 1f / Math.max(0.1f, n.fps);
            n.ft += dt;
            while (n.ft >= spf) {
                n.ft -= spf;
                n.frame++;
                if (n.frame >= n.frames.size()) {
                    if (n.loop) {
                        n.frame = 0;
                    } else {
                        n.frame = n.frames.size() - 1;
                        n.playing = false;
                        n.preview = false;
                        n.ft = 0;
                        break;
                    }
                }
            }
        }
    }

    void updateCamera(float dt) {
        useCam = !edit && viewMode.equals("camera") && cam != null;
        if (!useCam) return;
        compute();
        float tx = cam.wx + cam.w / 2f, ty = cam.wy + cam.h / 2f;
        if (!cam.follow.isEmpty()) {
            Node f = byName(cam.follow);
            if (f != null) {
                tx = f.wx + f.w / 2f;
                ty = f.wy + f.h / 2f;
            }
        }
        if (camSnap || cam.smooth <= 0f) {
            camX = tx;
            camY = ty;
            camSnap = false;
        } else {
            float k = 1f - (float) Math.exp(-cam.smooth * dt);
            camX += (tx - camX) * k;
            camY += (ty - camY) * k;
        }
        camZ = Math.max(0.05f, cam.zoom);
        if (cam.lim) {
            float hw = vw / (2f * camZ), hh = vh / (2f * camZ);
            camX = (cam.lr - cam.ll < 2 * hw) ? (cam.ll + cam.lr) / 2f : Math.max(cam.ll + hw, Math.min(cam.lr - hw, camX));
            camY = (cam.lb - cam.lt < 2 * hh) ? (cam.lt + cam.lb) / 2f : Math.max(cam.lt + hh, Math.min(cam.lb - hh, camY));
        }
    }

    public void update(float dt) {
        animate(dt);
        if (edit) return;
        if (hasVideo) {
            for (int a = 0; a < nodes.size(); a++) {
                Node n = nodes.get(a);
                if (!n.vdone) continue;
                n.vdone = false;
                for (int i = 0; i < n.script.size(); i++) {
                    Blk b = n.script.get(i);
                    if (b.t.equals("vend")) spawn(b.body, n);
                }
            }
        }
        for (int i = tws.size() - 1; i >= 0; i--) {
            Tw t = tws.get(i);
            t.t += dt;
            float k = t.d <= 0 ? 1f : Math.min(1f, t.t / t.d);
            t.apply(k);
            if (k >= 1f) tws.remove(i);
        }
        for (int i = holds.size() - 1; i >= 0; i--) {
            Hold h = holds.get(i);
            if (h.n.pid == h.pid) {
                if (h.task == null || h.task.done) h.task = spawn(h.b.body, h.n);
            } else {
                if (h.task != null) h.task.done = true;
                spawn(h.b.els, h.n);
                holds.remove(i);
            }
        }
        if (hasPoll) {
            for (int a = 0; a < nodes.size(); a++) {
                Node n = nodes.get(a);
                for (int i = 0; i < n.script.size(); i++) {
                    Blk b = n.script.get(i);
                    if (b.t.equals("frame")) {
                        if (b.task == null || b.task.done) b.task = spawn(b.body, n);
                    } else if (b.t.equals("every")) {
                        double iv = num(val(b.s("secs"), n));
                        b.acc += dt;
                        if (iv > 0.0001 && b.acc >= iv) {
                            b.acc = 0;
                            if (b.task == null || b.task.done) b.task = spawn(b.body, n);
                        }
                    } else if (b.t.equals("change")) {
                        Node t = tgt(b, n);
                        if (t != null) {
                            String cur = String.valueOf(prop(t, b.s("prop")));
                            if (b.last == null) {
                                b.last = cur;
                            } else if (!cur.equals(b.last)) {
                                b.last = cur;
                                spawn(b.body, n);
                            }
                        }
                    }
                }
            }
        }
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            if (t.done) continue;
            if (t.fresh) {
                t.fresh = false;
                continue;
            }
            if (t.wait > 0f) {
                t.wait -= dt;
                if (t.wait > 0f) continue;
                t.wait = 0f;
            }
            step(t);
        }
        for (int i = tasks.size() - 1; i >= 0; i--) if (tasks.get(i).done) tasks.remove(i);
        pump();
        if (orientChanged) {
            orientChanged = false;
            for (int a = 0; a < nodes.size(); a++) {
                Node n = nodes.get(a);
                for (int i = 0; i < n.script.size(); i++) {
                    Blk b = n.script.get(i);
                    if (b.t.equals("orient")) spawn(b.body, n);
                }
            }
        }
        if (pendingScene != null) {
            String nm = pendingScene;
            pendingScene = null;
            Scene sc = sceneByName(nm);
            if (sc != null) enterScene(sc);
        }
        updateCamera(dt);
    }

    // ================= Entrada =================
    boolean touchable(Node n) {
        if (n.type.equals("Button") || n.type.equals("ImageButton")) return true;
        for (int i = 0; i < n.script.size(); i++) {
            String t = n.script.get(i).t;
            if (t.equals("press") || t.equals("hold")) return true;
        }
        return false;
    }

    public float toVx(float px) {
        return (px - ox) / sx;
    }

    public float toVy(float py) {
        return (py - oy) / sy;
    }

    public Node hit(float vx, float vy, boolean onlyTouchable) {
        float cx = useCam ? (vx - vw / 2f) / camZ + camX : vx;
        float cy = useCam ? (vy - vh / 2f) / camZ + camY : vy;
        for (int i = order.size() - 1; i >= 0; i--) {
            Node n = order.get(i);
            if (!n.wvis) continue;
            if (onlyTouchable && ((n.wa <= 0.003f && !n.touchAlways) || !touchable(n))) continue;
            float px = (useCam && !n.fixed) ? cx : vx;
            float py = (useCam && !n.fixed) ? cy : vy;
            if (px >= n.wx && px <= n.wx + n.w && py >= n.wy && py <= n.wy + n.h) {
                if (onlyTouchable && n.smart && !maskHit(n, px, py)) continue;
                return n;
            }
        }
        return null;
    }

    public void down(int pid, float vx, float vy) {
        Node n = hit(vx, vy, true);
        if (n == null) return;
        n.pressed = true;
        n.pid = pid;
        for (int i = 0; i < n.script.size(); i++) {
            Blk b = n.script.get(i);
            if (b.t.equals("press")) {
                spawn(b.body, n);
            } else if (b.t.equals("hold")) {
                Hold h = new Hold();
                h.n = n;
                h.b = b;
                h.pid = pid;
                holds.add(h);
            }
        }
    }

    public void up(int pid) {
        for (Node n : nodes) {
            if (n.pid == pid) {
                n.pid = -1;
                n.pressed = false;
            }
        }
    }

    public void upAll() {
        for (Node n : nodes) {
            n.pid = -1;
            n.pressed = false;
        }
    }

    // ================= Toque inteligente (máscara de bits) =================
    String maskImage(Node n) {
        if (n.type.equals("AnimatedSprite")) return n.frames.isEmpty() ? "" : n.frames.get(Math.min(n.frame, n.frames.size() - 1));
        return n.res;
    }

    Mask mask(String name, int res, boolean maj) {
        String key = name + "|" + res + "|" + maj;
        if (masks.containsKey(key)) return masks.get(key);
        Bitmap bm = img(name);
        Mask m = null;
        if (bm != null) {
            int bw = bm.getWidth(), bh = bm.getHeight();
            m = new Mask();
            m.w = bw >= bh ? res : Math.max(1, Math.round(res * (float) bw / bh));
            m.h = bh >= bw ? res : Math.max(1, Math.round(res * (float) bh / bw));
            m.b = new boolean[m.w * m.h];
            boolean alpha = bm.hasAlpha();
            for (int cy = 0; cy < m.h; cy++) {
                for (int cx = 0; cx < m.w; cx++) {
                    int x0 = cx * bw / m.w, x1 = Math.max(x0 + 1, (cx + 1) * bw / m.w);
                    int y0 = cy * bh / m.h, y1 = Math.max(y0 + 1, (cy + 1) * bh / m.h);
                    int stx = Math.max(1, (x1 - x0) / 6), sty = Math.max(1, (y1 - y0) / 6);
                    int tot = 0, on = 0;
                    for (int y = y0; y < y1; y += sty) {
                        for (int x = x0; x < x1; x += stx) {
                            tot++;
                            if (!alpha || (bm.getPixel(Math.min(x, bw - 1), Math.min(y, bh - 1)) >>> 24) > 25) on++;
                        }
                    }
                    m.b[cy * m.w + cx] = maj ? on * 2 >= tot : on > 0;
                }
            }
        }
        masks.put(key, m);
        return m;
    }

    boolean maskHit(Node n, float px, float py) {
        String name = maskImage(n);
        if (name.isEmpty()) return true;
        Mask m = mask(name, n.smartRes, n.smartMaj);
        if (m == null) return true;
        int cx = (int) ((px - n.wx) / n.w * m.w), cy = (int) ((py - n.wy) / n.h * m.h);
        cx = Math.max(0, Math.min(m.w - 1, cx));
        cy = Math.max(0, Math.min(m.h - 1, cy));
        return m.b[cy * m.w + cx];
    }

    void drawMask(Canvas c, Node n) {
        String name = maskImage(n);
        if (name.isEmpty()) return;
        Mask m = mask(name, n.smartRes, n.smartMaj);
        if (m == null) return;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x6600FF00);
        float cw = n.w / m.w, ch = n.h / m.h;
        for (int cy = 0; cy < m.h; cy++) {
            for (int cx = 0; cx < m.w; cx++) {
                if (m.b[cy * m.w + cx]) c.drawRect(n.wx + cx * cw, n.wy + cy * ch, n.wx + (cx + 1) * cw, n.wy + (cy + 1) * ch, p);
            }
        }
    }

    // ================= Texto y fuentes =================
    Typeface fontFor(String font, boolean bold, boolean italic) {
        String key = font + "|" + bold + italic;
        Typeface t = tfCache.get(key);
        if (t != null) return t;
        Typeface base = Typeface.DEFAULT;
        if (!font.isEmpty()) {
            try {
                base = Typeface.createFromFile(src.file(font));
            } catch (Exception e) {
                base = Typeface.DEFAULT;
            }
        }
        int st = (bold ? Typeface.BOLD : 0) | (italic ? Typeface.ITALIC : 0);
        t = st == 0 ? base : Typeface.create(base, st);
        tfCache.put(key, t);
        return t;
    }

    // Reemplaza {variable} por su valor
    String labelText(Node n) {
        String t = n.text;
        if (edit || t.indexOf('{') < 0) return t;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < t.length()) {
            char ch = t.charAt(i);
            if (ch == '{') {
                int j = t.indexOf('}', i);
                if (j > i) {
                    String nm = t.substring(i + 1, j).trim();
                    Object v = getVar(nm, n);
                    if (v == null && nm.indexOf('.') > 0) {
                        Object o = val(nm, n);
                        if (!nm.equals(String.valueOf(o))) v = o;
                    }
                    sb.append(v != null ? String.valueOf(v) : "{" + nm + "}");
                    i = j + 1;
                    continue;
                }
            }
            sb.append(ch);
            i++;
        }
        return sb.toString();
    }

    void drawLabel(Canvas c, Node n, int a) {
        String txt = labelText(n);
        int w = Math.max(1, Math.round(n.w));
        String key = txt + "|" + w + "|" + n.tsize + "|" + n.font + "|" + n.bold + n.italic + "|" + n.align + "|"
                + n.color + "|" + n.outline + "|" + n.ocolor + "|" + n.shadow + "|" + n.wrap;
        if (n.lay == null || !key.equals(n.layKey)) {
            n.layKey = key;
            TextPaint pf = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            pf.setTextSize(n.tsize);
            pf.setTypeface(fontFor(n.font, n.bold, n.italic));
            pf.setColor(n.color);
            if (n.shadow) pf.setShadowLayer(Math.max(2f, n.tsize / 12f), n.tsize / 14f, n.tsize / 14f, 0xAA000000);
            Layout.Alignment al = n.align == 0 ? Layout.Alignment.ALIGN_NORMAL
                    : n.align == 2 ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_CENTER;
            int lw = w;
            if (!n.wrap) lw = Math.max(1, (int) Math.ceil(Layout.getDesiredWidth(txt, pf)));
            n.layW = lw;
            n.tpF = pf;
            n.lay = new StaticLayout(txt, pf, lw, al, 1f, 0f, false);
            if (n.outline > 0f) {
                TextPaint po = new TextPaint(pf);
                po.setStyle(Paint.Style.STROKE);
                po.setStrokeWidth(n.outline);
                po.setStrokeJoin(Paint.Join.ROUND);
                po.setColor(n.ocolor);
                po.clearShadowLayer();
                n.tpO = po;
                n.layO = new StaticLayout(txt, po, lw, al, 1f, 0f, false);
            } else {
                n.tpO = null;
                n.layO = null;
            }
        }
        float th = n.lay.getHeight();
        float oy = n.valign == 1 ? (n.h - th) / 2f : n.valign == 2 ? n.h - th : 0f;
        float ox = 0f;
        if (!n.wrap) ox = n.align == 1 ? (n.w - n.layW) / 2f : n.align == 2 ? n.w - n.layW : 0f;
        c.save();
        c.translate(n.wx + ox, n.wy + oy);
        if (n.layO != null) {
            n.tpO.setAlpha(a);
            n.layO.draw(c);
        }
        n.tpF.setAlpha(a);
        n.lay.draw(c);
        c.restore();
    }

    // ================= Estado de la última partida (para elegir posiciones) =================
    public String stateJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("scene", curScene.name);
        JSONArray a = new JSONArray();
        for (Node n : nodes) {
            JSONObject j = new JSONObject();
            j.put("id", n.id);
            j.put("x", n.x);
            j.put("y", n.y);
            j.put("w", n.w);
            j.put("h", n.h);
            j.put("a", n.alpha);
            j.put("vis", n.visible);
            j.put("res", n.res);
            j.put("text", n.text);
            j.put("frame", n.frame);
            a.put(j);
        }
        o.put("nodes", a);
        return o.toString();
    }

    public void applyState(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        if (!o.optString("scene", "").equals(curScene.name)) return;
        JSONArray a = o.optJSONArray("nodes");
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject j = a.getJSONObject(i);
            Node n = byId(j.getInt("id"));
            if (n == null) continue;
            n.x = (float) j.optDouble("x", n.x);
            n.y = (float) j.optDouble("y", n.y);
            n.w = (float) j.optDouble("w", n.w);
            n.h = (float) j.optDouble("h", n.h);
            n.alpha = (float) j.optDouble("a", n.alpha);
            n.visible = j.optBoolean("vis", n.visible);
            n.res = j.optString("res", n.res);
            n.text = j.optString("text", n.text);
            n.frame = j.optInt("frame", n.frame);
        }
    }

    // ================= Dibujo =================
    void computeMap(int sw, int sh) {
        String m = edit ? "keep" : stretch;
        float ax = sw / (float) vw, ay = sh / (float) vh;
        if (m.equals("stretch")) {
            sx = ax;
            sy = ay;
        } else {
            float s = m.equals("fill") ? Math.max(ax, ay) : Math.min(ax, ay);
            sx = s;
            sy = s;
        }
        ox = (sw - vw * sx) / 2f;
        oy = (sh - vh * sy) / 2f;
    }

    public void draw(Canvas c, int sw, int sh) {
        computeMap(sw, sh);
        c.drawColor(0xFF000000);
        c.save();
        c.translate(ox, oy);
        c.scale(sx, sy);
        c.clipRect(0, 0, vw, vh);
        c.drawColor(bg);
        compute();
        boolean uc = useCam && !snap;
        if (uc) {
            camM.reset();
            camM.postTranslate(-camX, -camY);
            camM.postScale(camZ, camZ);
            camM.postTranslate(vw / 2f, vh / 2f);
        }
        for (int i = 0; i < order.size(); i++) {
            Node n = order.get(i);
            if (uc && !n.fixed) {
                c.save();
                c.concat(camM);
                drawNode(c, n, false);
                c.restore();
            } else {
                drawNode(c, n, true);
            }
        }
        if (edit && sel != null && sel.inOrder && sel.smart) drawMask(c, sel);
        if (edit && sel != null && sel.inOrder) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f / Math.max(0.1f, sx));
            p.setColor(0xFFFF00FF);
            c.drawRect(sel.wx, sel.wy, sel.wx + sel.w, sel.wy + sel.h, p);
            p.setStyle(Paint.Style.FILL);
        }
        c.restore();
    }

    // Imagen de la escena en coordenadas del viewport (sin cámara), para elegir posiciones visualmente
    public Bitmap snapshot(int maxW) {
        float k = Math.min(1f, maxW / (float) vw);
        int w = Math.max(1, Math.round(vw * k)), h = Math.max(1, Math.round(vh * k));
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Node s0 = sel;
        String st = stretch;
        sel = null;
        stretch = "keep";
        snap = true;
        try {
            draw(c, w, h);
        } finally {
            snap = false;
            stretch = st;
            sel = s0;
        }
        return bm;
    }

    void placeholder(Canvas c, Node n, int a) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(0xFF888888);
        p.setAlpha(a);
        c.drawRect(rf, p);
        p.setStyle(Paint.Style.FILL);
        tp.setColor(0xFFAAAAAA);
        tp.setAlpha(a);
        tp.setTextSize(Math.max(14f, Math.min(n.h * 0.3f, 36f)));
        tp.setTextAlign(Paint.Align.CENTER);
        String label = n.name;
        if (n.type.equals("Sound")) label = "♪ " + n.name;
        c.drawText(label, rf.centerX(), rf.centerY(), tp);
    }

    void drawNode(Canvas c, Node n, boolean cull) {
        if (!n.wvis || n.wa <= 0.003f) return;
        if (cull && (n.wx > vw || n.wy > vh || n.wx + n.w < 0 || n.wy + n.h < 0)) return;
        int a = Math.max(0, Math.min(255, (int) (n.wa * 255)));
        rf.set(n.wx, n.wy, n.wx + n.w, n.wy + n.h);
        p.setStyle(Paint.Style.FILL);
        switch (n.type) {
            case "Image":
            case "ImageButton": {
                Bitmap b = img(n.res);
                int aa = (n.type.equals("ImageButton") && n.pressed) ? a * 7 / 10 : a;
                if (b != null) {
                    p.setAlpha(aa);
                    c.drawBitmap(b, null, rf, p);
                } else if (edit) {
                    placeholder(c, n, a);
                }
                break;
            }
            case "AnimatedSprite": {
                Bitmap b = n.frames.isEmpty() ? null : img(n.frames.get(Math.min(n.frame, n.frames.size() - 1)));
                if (b != null) {
                    p.setAlpha(a);
                    c.drawBitmap(b, null, rf, p);
                } else if (edit) {
                    placeholder(c, n, a);
                }
                break;
            }
            case "Button": {
                p.setColor(n.pressed ? 0xFF3A3A3A : 0xFF5A5A5A);
                p.setAlpha(a);
                float r = Math.min(n.w, n.h) * 0.2f;
                c.drawRoundRect(rf, r, r, p);
                tp.setColor(0xFFFFFFFF);
                tp.setAlpha(a);
                tp.setTextSize(n.tsize);
                tp.setTextAlign(Paint.Align.CENTER);
                tp.setTypeface(fontFor(n.font, n.bold, n.italic));
                c.drawText(n.text, rf.centerX(), rf.centerY() - (tp.ascent() + tp.descent()) / 2f, tp);
                break;
            }
            case "Label":
                drawLabel(c, n, a);
                break;
            case "Video":
                if (edit && !snap) {
                    Bitmap tb = vthumb(n.res);
                    if (tb != null) {
                        p.setAlpha(a);
                        c.drawBitmap(tb, null, rf, p);
                    } else {
                        placeholder(c, n, a);
                    }
                }
                break;
            case "Sound":
                if (edit && !snap) placeholder(c, n, a);
                break;
            case "Camera2D":
                if (edit && !snap) {
                    float z = Math.max(0.05f, n.zoom);
                    float cx = rf.centerX(), cy = rf.centerY();
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2f);
                    p.setColor(0xFF00CCFF);
                    p.setAlpha(a);
                    c.drawRect(cx - vw / (2f * z), cy - vh / (2f * z), cx + vw / (2f * z), cy + vh / (2f * z), p);
                    p.setStyle(Paint.Style.FILL);
                    placeholder(c, n, a);
                }
                break;
            default:
                break;
        }
    }
}
