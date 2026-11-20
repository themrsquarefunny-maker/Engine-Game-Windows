package com.mini.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class WindowsCompat {
    public static final Set<String> SUPPORTED_NODES = new HashSet<String>(Arrays.asList(
            "Image", "Button", "ImageButton", "Label", "Color", "Sound",
            "AnimatedSprite", "Camera2D", "Group", "Anchor", "Grab",
            "Collision", "Limiter", "Autoload"
    ));

    public static final Set<String> SUPPORTED_BLOCKS = new HashSet<String>(Arrays.asList(
            "start", "press", "hold", "recv", "frame", "collision", "collisionExit",
            "collisionStay", "every", "change", "orient", "func",
            "send", "call", "callNode", "funcExists", "return",
            "if", "elif", "else", "while", "repeat", "forever", "break", "breakrep",
            "breakloop", "wait",
            "var", "setvar", "math", "enum", "enumnext", "match", "case", "default",
            "layer", "save", "clearsave", "scene", "orientset", "viewmode", "camset",
            "setimg", "setsnd", "play", "stop", "quit", "mod", "vis", "pos",
            "clone", "visualClone", "clearVisualClones", "advance", "rotate",
            "stoprender", "startrender", "collisionEnable", "speed", "text", "tween"
    ));

    public static List<String> check(Game g) {
        LinkedHashSet<String> warnings = new LinkedHashSet<String>();

        if (g.vwL > 1280 || g.vhL > 720) {
            warnings.add("El viewport horizontal (" + g.vwL + "×" + g.vhL
                    + ") supera el máximo admitido en Windows (1280×720).");
        }

        for (Game.Scene sc : g.scenes) {
            HashMap<String, String> nodeTypes = new HashMap<String, String>();
            for (Game.Node n : sc.nodes) nodeTypes.put(n.name, n.type);
            for (Game.Node n : sc.nodes) {
                checkNode(sc.name, n, nodeTypes, warnings);
            }
        }
        if (!g.autoload.script.isEmpty()) {
            checkNode("Autoload", g.autoload, new HashMap<String, String>(), warnings);
        }

        return new ArrayList<String>(warnings);
    }

    static void checkNode(String sceneName, Game.Node n, HashMap<String, String> nodeTypes,
                          LinkedHashSet<String> warnings) {
        if ("Video".equals(n.type)) {
            warnings.add("Escena «" + sceneName + "», nodo «" + n.name
                    + "»: los nodos Video todavía no son compatibles con Windows.");
        } else if (!SUPPORTED_NODES.contains(n.type)) {
            warnings.add("Escena «" + sceneName + "», nodo «" + n.name
                    + "»: el tipo «" + n.type + "» todavía no es compatible con Windows.");
        }

        if ("Sound".equals(n.type) && isUnsupportedAudio(n.snd)) {
            warnings.add("Escena «" + sceneName + "», nodo «" + n.name
                    + "»: el formato de audio «" + n.snd
                    + "» todavía no es compatible con Windows (usa .wav, .mp3 u .ogg).");
        }

        if (isGif(n.res)) {
            warnings.add("Escena «" + sceneName + "», nodo «" + n.name
                    + "»: «" + n.res + "» es .gif; en Windows solo se mostrará el primer cuadro.");
        }

        checkBlocks(sceneName, n, n.script, nodeTypes, warnings);
    }

    static void checkBlocks(String sceneName, Game.Node owner, List<Game.Blk> list,
                            HashMap<String, String> nodeTypes, LinkedHashSet<String> warnings) {
        for (Game.Blk b : list) {
            if ("vend".equals(b.t) || "vpause".equals(b.t) || "vseek".equals(b.t)
                    || "vvol".equals(b.t) || "setvideo".equals(b.t)) {
                warnings.add("Escena «" + sceneName + "», nodo «" + owner.name
                        + "»: el bloque de video «" + b.t + "» todavía no es compatible con Windows.");
            } else if (!SUPPORTED_BLOCKS.contains(b.t)) {
                warnings.add("Escena «" + sceneName + "», nodo «" + owner.name
                        + "»: el bloque «" + b.t + "» todavía no es compatible con Windows.");
            }

            if ("setsnd".equals(b.t) && isUnsupportedAudio(b.s("asset"))) {
                warnings.add("Escena «" + sceneName + "», nodo «" + owner.name
                        + "»: el formato de audio «" + b.s("asset")
                        + "» todavía no es compatible con Windows (usa .wav, .mp3 u .ogg).");
            }

            if ("play".equals(b.t) || "stop".equals(b.t) || "breakloop".equals(b.t)) {
                String targetName = b.s("node").trim();
                String targetType = (targetName.isEmpty() || "self".equals(targetName))
                        ? owner.type : nodeTypes.get(targetName);
                if ("Video".equals(targetType)) {
                    warnings.add("Escena «" + sceneName + "», nodo «" + owner.name
                            + "»: «" + b.t + "» apunta a un nodo Video (aún no compatible en Windows).");
                }
            }

            if (!b.body.isEmpty()) checkBlocks(sceneName, owner, b.body, nodeTypes, warnings);
            if (!b.els.isEmpty()) checkBlocks(sceneName, owner, b.els, nodeTypes, warnings);
        }
    }

    static boolean isUnsupportedAudio(String name) {
        if (name == null || name.isEmpty()) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".m4a") || lower.endsWith(".aac");
    }

    static boolean isGif(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".gif");
    }
}
