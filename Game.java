package com.mini.engine;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
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
import java.util.Map;

public class Game {
    public interface Src {
        InputStream open(String name) throws IOException;
        File file(String name) throws IOException;
    }

    public static final String[] TYPES = {"Image", "Button", "ImageButton", "Sound", "AnimatedSprite"};

    // ================= Bloques =================
    public static class Blk {
        public String t;
        public HashMap<String, String> f = new HashMap<String, String>();
        public ArrayList<Blk> body = new ArrayList<Blk>();
        public ArrayList<Blk> els = new ArrayList<Blk>();
        String last;

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
            o.put("b", arr(body));
            o.put("e", arr(els));
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
        public String name = "", type = "", res = "", snd = "", text = "";
        public int parent = -1;
        public boolean inherit = true, visible = true, loop, autoplay;
        public float x, y, w = 200, h = 200, alpha = 1f, fps = 8f, tsize = 36f;
        public ArrayList<String> frames = new ArrayList<String>();
        public ArrayList<Blk> script = new ArrayList<Blk>();
        // tiempo de ejecución
        public float wx, wy, wa;
        public boolean wvis, playing, preview, pressed;
        public int pid = -1, frame;
        public float ft;
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
            JSONArray fr = o.optJSONArray("fr");
            if (fr != null) for (int i = 0; i < fr.length(); i++) n.frames.add(fr.getString(i));
            n.script = Blk.list(o.optJSONArray("script"));
            return n;
        }
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

    static class Hold {
        Node n;
        Blk b;
        int pid;
    }

    // ================= Estado =================
    public ArrayList<Node> nodes = new ArrayList<Node>();
    public ArrayList<Node> order = new ArrayList<Node>();
    public boolean landscape = true;
    public String stretch = "keep";
    public int vw = 1280, vh = 720, bg = 0xFF202020, nextId = 1;
    public Src src;
    public boolean edit;
    public Node sel;
    public float ox, oy, sx = 1, sy = 1;

    HashMap<String, Object> vars = new HashMap<String, Object>();
    ArrayDeque<String> msgs = new ArrayDeque<String>();
    ArrayList<Tw> tws = new ArrayList<Tw>();
    ArrayList<Hold> holds = new ArrayList<Hold>();
    HashMap<String, Bitmap> cache = new HashMap<String, Bitmap>();
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    RectF rf = new RectF();

    public Game(Src s) {
        src = s;
    }

    // ================= JSON =================
    public String toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("land", landscape);
        o.put("st", stretch);
        o.put("vw", vw);
        o.put("vh", vh);
        o.put("bg", bg);
        o.put("next", nextId);
        JSONArray a = new JSONArray();
        for (Node n : nodes) a.put(n.toJson());
        o.put("nodes", a);
        return o.toString();
    }

    public static Game fromJson(String json, Src s) throws Exception {
        JSONObject o = new JSONObject(json);
        Game g = new Game(s);
        g.landscape = o.optBoolean("land", true);
        g.stretch = o.optString("st", "keep");
        g.vw = Math.max(16, o.optInt("vw", 1280));
        g.vh = Math.max(16, o.optInt("vh", 720));
        g.bg = o.optInt("bg", 0xFF202020);
        g.nextId = o.optInt("next", 1);
        JSONArray a = o.optJSONArray("nodes");
        if (a != null) for (int i = 0; i < a.length(); i++) g.nodes.add(Node.fromJson(a.getJSONObject(i)));
        for (Node n : g.nodes) if (n.id >= g.nextId) g.nextId = n.id + 1;
        g.rebuild();
        return g;
    }

    // ================= Árbol =================
    public Node byId(int id) {
        for (Node n : nodes) if (n.id == id) return n;
        return null;
    }

    public Node byName(String s) {
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
        } else if (type.equals("AnimatedSprite")) {
            n.loop = true;
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
        addOrder(-1);
    }

    void addOrder(int pid) {
        for (Node n : nodes) {
            boolean root = pid == -1 ? (n.parent == -1 || byId(n.parent) == null) : n.parent == pid;
            if (!root || order.contains(n)) continue;
            order.add(n);
            addOrder(n.id);
        }
    }

    void compute() {
        for (Node n : order) {
            Node pn = n.parent >= 0 ? byId(n.parent) : null;
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

    public HashSet<String> usedAssets() {
        HashSet<String> s = new HashSet<String>();
        for (Node n : nodes) {
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
            if (s.matches("-?\\d+")) return Long.valueOf(s);
            if (s.matches("-?\\d*\\.\\d+")) return Double.valueOf(s);
        } catch (Exception e) {
        }
        if (vars.containsKey(s)) return vars.get(s);
        int d = s.indexOf('.');
        if (d > 0) {
            String nn = s.substring(0, d);
            Node n = nn.equals("self") ? self : byName(nn);
            if (n != null) {
                Object v = prop(n, s.substring(d + 1));
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

    void setVar(String name, String type, Object v) {
        if (name.isEmpty()) return;
        Object o;
        switch (type) {
            case "int": o = Long.valueOf((long) num(v)); break;
            case "float": o = Double.valueOf(num(v)); break;
            case "bool": o = Boolean.valueOf(truthy(v)); break;
            default: o = String.valueOf(v);
        }
        vars.put(name, o);
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    void exec(Blk b, Node self) {
        Node t = tgt(b, self);
        if (b.f.containsKey("node") && t == null) return;
        switch (b.t) {
            case "send":
                msgs.add(b.s("msg"));
                break;
            case "var":
                setVar(b.s("name").trim(), b.s("vtype"), val(b.s("val"), self));
                break;
            case "math": {
                String name = b.s("name").trim();
                Object cur = vars.get(name);
                if (cur == null) cur = Long.valueOf(0);
                Object v = val(b.s("val"), self);
                double x = num(cur), y = num(v), r;
                switch (b.s("op")) {
                    case "-": r = x - y; break;
                    case "*": r = x * y; break;
                    case "/": r = y == 0 ? 0 : x / y; break;
                    default: r = x + y;
                }
                if (cur instanceof Long && v instanceof Long) vars.put(name, Long.valueOf((long) r));
                else vars.put(name, Double.valueOf(r));
                break;
            }
            case "setimg":
                t.res = b.s("asset");
                break;
            case "setsnd":
                t.snd = b.s("asset");
                break;
            case "play":
                if (t.type.equals("Sound")) playSound(t);
                else if (t.type.equals("AnimatedSprite")) t.playing = true;
                break;
            case "stop":
                if (t.type.equals("Sound")) stopSound(t);
                else if (t.type.equals("AnimatedSprite")) t.playing = false;
                break;
            case "mod":
                t.alpha = clamp01((float) num(val(b.s("val"), self)));
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
                } else if (what.equals("mover")) {
                    w.kind = 1;
                    w.from[0] = t.x;
                    w.from[1] = t.y;
                    w.has[0] = !b.s("x").trim().isEmpty();
                    w.has[1] = !b.s("y").trim().isEmpty();
                    w.to[0] = (float) num(val(b.s("x"), self));
                    w.to[1] = (float) num(val(b.s("y"), self));
                } else {
                    w.kind = 2;
                    w.from[0] = t.w;
                    w.from[1] = t.h;
                    w.has[0] = !b.s("x").trim().isEmpty();
                    w.has[1] = !b.s("y").trim().isEmpty();
                    w.to[0] = (float) num(val(b.s("x"), self));
                    w.to[1] = (float) num(val(b.s("y"), self));
                }
                for (int i = tws.size() - 1; i >= 0; i--) {
                    if (tws.get(i).n == t && tws.get(i).kind == w.kind) tws.remove(i);
                }
                if (w.d <= 0) w.apply(1f);
                else tws.add(w);
                break;
            }
            default:
                break;
        }
    }

    void run(ArrayList<Blk> list, Node self, int depth) {
        if (depth > 12) return;
        boolean chain = false;
        for (int i = 0; i < list.size(); i++) {
            Blk b = list.get(i);
            switch (b.t) {
                case "if":
                    chain = cond(b, self);
                    if (chain) run(b.body, self, depth + 1);
                    break;
                case "elif":
                    if (!chain && cond(b, self)) {
                        chain = true;
                        run(b.body, self, depth + 1);
                    }
                    break;
                case "else":
                    if (!chain) run(b.body, self, depth + 1);
                    chain = true;
                    break;
                default:
                    exec(b, self);
                    chain = false;
            }
        }
    }

    void pump() {
        int guard = 0;
        while (!msgs.isEmpty() && guard++ < 500) {
            String m = msgs.poll().trim();
            for (Node n : nodes) {
                for (int i = 0; i < n.script.size(); i++) {
                    Blk b = n.script.get(i);
                    if (b.t.equals("recv") && b.s("msg").trim().equals(m)) run(b.body, n, 0);
                }
            }
        }
    }

    // ================= Ciclo de juego =================
    public void start() {
        vars.clear();
        msgs.clear();
        tws.clear();
        holds.clear();
        for (Node n : nodes) {
            n.playing = n.autoplay;
            n.frame = 0;
            n.ft = 0;
            n.pressed = false;
            n.pid = -1;
            for (Blk b : n.script) b.last = null;
        }
        for (Node n : nodes) if (n.type.equals("Sound") && n.autoplay) playSound(n);
        for (Node n : nodes) {
            for (int i = 0; i < n.script.size(); i++) {
                Blk b = n.script.get(i);
                if (b.t.equals("start")) run(b.body, n, 0);
            }
        }
        pump();
    }

    void animate(float dt) {
        for (Node n : nodes) {
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

    public void update(float dt) {
        animate(dt);
        if (edit) return;
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
                run(h.b.body, h.n, 0);
            } else {
                run(h.b.els, h.n, 0);
                holds.remove(i);
            }
        }
        for (Node n : nodes) {
            for (int i = 0; i < n.script.size(); i++) {
                Blk b = n.script.get(i);
                if (b.t.equals("frame")) {
                    run(b.body, n, 0);
                } else if (b.t.equals("change")) {
                    Node t = tgt(b, n);
                    if (t != null) {
                        String cur = String.valueOf(prop(t, b.s("prop")));
                        if (b.last == null) {
                            b.last = cur;
                        } else if (!cur.equals(b.last)) {
                            b.last = cur;
                            run(b.body, n, 0);
                        }
                    }
                }
            }
        }
        pump();
    }

    // ================= Entrada =================
    boolean touchable(Node n) {
        if (n.type.equals("Button") || n.type.equals("ImageButton")) return true;
        for (Blk b : n.script) if (b.t.equals("press") || b.t.equals("hold")) return true;
        return false;
    }

    public float toVx(float px) {
        return (px - ox) / sx;
    }

    public float toVy(float py) {
        return (py - oy) / sy;
    }

    public Node hit(float vx, float vy, boolean onlyTouchable) {
        for (int i = order.size() - 1; i >= 0; i--) {
            Node n = order.get(i);
            if (!n.wvis) continue;
            if (onlyTouchable && (n.wa <= 0.003f || !touchable(n))) continue;
            if (vx >= n.wx && vx <= n.wx + n.w && vy >= n.wy && vy <= n.wy + n.h) return n;
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
                run(b.body, n, 0);
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
        for (Node n : order) drawNode(c, n);
        if (edit && sel != null && order.contains(sel)) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f / Math.max(0.1f, sx));
            p.setColor(0xFFFF00FF);
            c.drawRect(sel.wx, sel.wy, sel.wx + sel.w, sel.wy + sel.h, p);
            p.setStyle(Paint.Style.FILL);
        }
        c.restore();
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
        c.drawText(n.type.equals("Sound") ? "♪ " + n.name : n.name, rf.centerX(), rf.centerY(), tp);
    }

    void drawNode(Canvas c, Node n) {
        if (!n.wvis || n.wa <= 0.003f) return;
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
                c.drawText(n.text, rf.centerX(), rf.centerY() - (tp.ascent() + tp.descent()) / 2f, tp);
                break;
            }
            case "Sound":
                if (edit) placeholder(c, n, a);
                break;
            default:
                break;
        }
    }
}
