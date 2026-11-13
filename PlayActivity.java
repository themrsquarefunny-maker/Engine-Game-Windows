package com.mini.engine;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.WindowManager;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PlayActivity extends Activity {
    Game g;

    static byte[] readAll(InputStream i) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = i.read(b)) > 0) o.write(b, 0, n);
        return o.toByteArray();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            String dir = getIntent().getStringExtra("dir");
            String json;
            Game.Src src;
            if (dir != null) {
                final File ad = new File(dir, "assets");
                try (InputStream i = new FileInputStream(new File(dir, "game.json"))) {
                    json = new String(readAll(i), "UTF-8");
                }
                src = new Game.Src() {
                    public InputStream open(String n) throws IOException {
                        return new FileInputStream(new File(ad, n));
                    }

                    public File file(String n) throws IOException {
                        return new File(ad, n);
                    }
                };
            } else {
                final AssetManager am = getAssets();
                final File cache = getCacheDir();
                File[] old = cache.listFiles();
                if (old != null) for (File f : old) if (f.getName().startsWith("a_")) f.delete();
                try (InputStream i = am.open("game.json")) {
                    json = new String(readAll(i), "UTF-8");
                }
                src = new Game.Src() {
                    public InputStream open(String n) throws IOException {
                        return am.open("a/" + n);
                    }

                    public File file(String n) throws IOException {
                        File f = new File(cache, "a_" + n);
                        if (!f.exists()) {
                            try (InputStream i = am.open("a/" + n); FileOutputStream o = new FileOutputStream(f)) {
                                byte[] buf = new byte[16384];
                                int k;
                                while ((k = i.read(buf)) > 0) o.write(buf, 0, k);
                            }
                        }
                        return f;
                    }
                };
            }
            g = Game.fromJson(json, src);
            setRequestedOrientation(g.landscape ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            GameView v = new GameView(this, g, false);
            setContentView(v);
            g.start();
        } catch (Exception e) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (g != null) g.release();
    }
}
