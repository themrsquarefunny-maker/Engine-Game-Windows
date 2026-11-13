package com.mini.engine;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

public class GameView extends View {
    public interface L {
        void sel(Game.Node n);
        void moved(Game.Node n);
    }

    public L l;
    Game g;
    boolean edit;
    long last;
    Game.Node drag;
    float lx, ly;

    public GameView(Context c, Game g, boolean edit) {
        super(c);
        this.g = g;
        this.edit = edit;
        g.edit = edit;
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0f : (now - last) / 1e9f;
        last = now;
        if (dt > 0.1f) dt = 0.1f;
        g.update(dt);
        g.draw(c, getWidth(), getHeight());
        postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (!edit) {
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
                int i = e.getActionIndex();
                g.down(e.getPointerId(i), g.toVx(e.getX(i)), g.toVy(e.getY(i)));
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_POINTER_UP) {
                g.up(e.getPointerId(e.getActionIndex()));
            } else if (a == MotionEvent.ACTION_CANCEL) {
                g.upAll();
            }
            return true;
        }
        float vx = g.toVx(e.getX()), vy = g.toVy(e.getY());
        if (a == MotionEvent.ACTION_DOWN) {
            drag = g.hit(vx, vy, false);
            lx = vx;
            ly = vy;
            if (l != null) l.sel(drag);
        } else if (a == MotionEvent.ACTION_MOVE) {
            if (drag != null) {
                drag.x += vx - lx;
                drag.y += vy - ly;
                lx = vx;
                ly = vy;
            }
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            if (drag != null && l != null) l.moved(drag);
            drag = null;
        }
        return true;
    }
}
