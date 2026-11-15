package com.mini.engine;

import android.content.Context;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// Capa que reproduce los nodos Video (mp4, webm VP8/VP9, mkv) encima del juego.
// No consume toques: pasan al GameView que está debajo.
public class VideoLayer extends FrameLayout implements Game.Layer {
    static class H {
        Game.Node n;
        TextureView tv;
        MediaPlayer mp;
        Surface sf;
        String res = "";
        boolean ready, wantPlay, hidden, resume;
    }

    final HashMap<Game.Node, H> map = new HashMap<Game.Node, H>();
    final RectF r = new RectF();
    boolean paused;

    public VideoLayer(Context c) {
        super(c);
        setClickable(false);
        setFocusable(false);
    }

    @Override
    public void sync(Game g) {
        // Quitar los que ya no existen (cambio de escena)
        ArrayList<Game.Node> gone = new ArrayList<Game.Node>();
        for (Game.Node n : map.keySet()) if (!g.nodes.contains(n)) gone.add(n);
        for (Game.Node n : gone) drop(map.remove(n));

        for (int i = 0; i < g.nodes.size(); i++) {
            Game.Node n = g.nodes.get(i);
            if (!n.type.equals("Video")) continue;
            H h = map.get(n);
            if (h == null) {
                h = make(n);
                map.put(n, h);
                h.res = n.res;
                h.wantPlay = n.autoplay;
                open(h, g);
            }
            command(h, g);
            place(h, g);
        }
    }

    H make(Game.Node n) {
        final H h = new H();
        h.n = n;
        h.tv = new TextureView(getContext());
        h.tv.setOpaque(false);
        h.tv.setClickable(false);
        h.tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int hh) {
                h.sf = new Surface(st);
                try {
                    if (h.mp != null) h.mp.setSurface(h.sf);
                } catch (Exception e) {
                }
            }

            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int hh) {
            }

            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                try {
                    if (h.mp != null) h.mp.setSurface(null);
                } catch (Exception e) {
                }
                if (h.sf != null) h.sf.release();
                h.sf = null;
                return true;
            }

            public void onSurfaceTextureUpdated(SurfaceTexture st) {
            }
        });
        addView(h.tv, new FrameLayout.LayoutParams(1, 1));
        return h;
    }

    void open(final H h, Game g) {
        closePlayer(h);
        if (h.res == null || h.res.isEmpty()) return;
        try {
            final MediaPlayer mp = new MediaPlayer();
            h.mp = mp;
            h.ready = false;
            mp.setDataSource(g.src.file(h.res).getPath());
            if (h.sf != null) mp.setSurface(h.sf);
            mp.setOnPreparedListener(p -> {
                h.ready = true;
                tryStart(h);
            });
            mp.setOnCompletionListener(p -> {
                if (!h.n.loop) h.n.vdone = true;
            });
            mp.setOnErrorListener((p, what, extra) -> {
                // Formato no soportado en este equipo
                h.ready = false;
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            closePlayer(h);
        }
    }

    void tryStart(H h) {
        if (h.mp == null || !h.ready || !h.wantPlay || paused || h.hidden) return;
        try {
            h.mp.start();
            h.wantPlay = false;
        } catch (Exception e) {
        }
    }

    void command(H h, Game g) {
        Game.Node n = h.n;
        if (!n.res.equals(h.res) && !"reload".equals(n.vcmd)) n.vcmd = "reload";
        String c = n.vcmd;
        if (c != null && !c.isEmpty()) {
            n.vcmd = "";
            try {
                if (c.equals("reload")) {
                    h.res = n.res;
                    h.wantPlay = n.autoplay;
                    open(h, g);
                } else if (c.equals("play")) {
                    h.wantPlay = true;
                    tryStart(h);
                } else if (c.equals("pause")) {
                    h.wantPlay = false;
                    if (h.ready) h.mp.pause();
                } else if (c.equals("stop")) {
                    h.wantPlay = false;
                    if (h.ready) {
                        h.mp.pause();
                        h.mp.seekTo(0);
                    }
                } else if (c.equals("seek")) {
                    if (h.ready) h.mp.seekTo((int) (Math.max(0f, n.vseek) * 1000f));
                }
            } catch (Exception e) {
            }
        }
        if (h.mp != null && h.ready) {
            try {
                h.mp.setLooping(n.loop);
                h.mp.setVolume(n.vol, n.vol);
            } catch (Exception e) {
            }
        }
    }

    void place(H h, Game g) {
        Game.Node n = h.n;
        boolean show = n.wvis && n.wa > 0.003f && !paused;
        // Si el nodo se oculta, el video se pausa y se reanuda al volver a verse
        if (!show && !h.hidden) {
            h.hidden = true;
            try {
                if (h.mp != null && h.ready && h.mp.isPlaying()) {
                    h.mp.pause();
                    h.resume = true;
                }
            } catch (Exception e) {
            }
        } else if (show && h.hidden) {
            h.hidden = false;
            try {
                if (h.resume && h.mp != null && h.ready) h.mp.start();
            } catch (Exception e) {
            }
            h.resume = false;
            tryStart(h);
        }
        h.tv.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        if (!show) return;
        r.set(n.wx, n.wy, n.wx + n.w, n.wy + n.h);
        if (g.useCam && !g.snap && !n.fixed) g.camM.mapRect(r);
        int w = Math.max(1, Math.round((r.right - r.left) * g.sx));
        int hh = Math.max(1, Math.round((r.bottom - r.top) * g.sy));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) h.tv.getLayoutParams();
        if (lp.width != w || lp.height != hh) {
            lp.width = w;
            lp.height = hh;
            h.tv.setLayoutParams(lp);
        }
        h.tv.setTranslationX(r.left * g.sx + g.ox);
        h.tv.setTranslationY(r.top * g.sy + g.oy);
        h.tv.setAlpha(Math.max(0f, Math.min(1f, n.wa)));
    }

    void closePlayer(H h) {
        if (h.mp != null) {
            try {
                h.mp.release();
            } catch (Exception e) {
            }
            h.mp = null;
        }
        h.ready = false;
    }

    void drop(H h) {
        if (h == null) return;
        closePlayer(h);
        removeView(h.tv);
    }

    public void pauseAll() {
        paused = true;
        for (Map.Entry<Game.Node, H> e : map.entrySet()) {
            H h = e.getValue();
            try {
                if (h.mp != null && h.ready && h.mp.isPlaying()) {
                    h.mp.pause();
                    h.resume = true;
                }
            } catch (Exception ex) {
            }
        }
    }

    public void resumeAll() {
        paused = false;
        for (Map.Entry<Game.Node, H> e : map.entrySet()) {
            H h = e.getValue();
            try {
                if (h.resume && h.mp != null && h.ready && !h.hidden) h.mp.start();
            } catch (Exception ex) {
            }
            h.resume = false;
        }
    }

    public void releaseAll() {
        for (H h : map.values()) drop(h);
        map.clear();
    }
}
