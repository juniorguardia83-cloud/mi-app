package com.gamerpanel;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Burbuja flotante con forma de control de consola. Al tocarla se abre un panel
 * con controles de música y accesos a WhatsApp, Spotify y otras apps.
 * Pensado para gastar poca RAM: el panel se crea al abrirlo y se destruye al cerrarlo.
 */
public class OverlayService extends Service {

    static final String ACTION_STOP = "com.gamerpanel.STOP";
    private static final String CHANNEL_ID = "panel_gamer";
    private static final int NOTIF_ID = 1;

    static volatile boolean running = false;

    private static final class App {
        final String name, pkg, letter;
        final int color;

        App(String name, String pkg, String letter, int color) {
            this.name = name;
            this.pkg = pkg;
            this.letter = letter;
            this.color = color;
        }
    }

    private static final App[] APPS = {
            new App("WhatsApp", "com.whatsapp", "W", 0xFF25D366),
            new App("WA Business", "com.whatsapp.w4b", "B", 0xFF128C7E),
            new App("Spotify", "com.spotify.music", "S", 0xFF1DB954),
            new App("YouTube", "com.google.android.youtube", "Y", 0xFFE62117),
            new App("TikTok", "com.zhiliaoapp.musically", "T", 0xFF3A3A4A),
            new App("Instagram", "com.instagram.android", "I", 0xFFE1306C),
            new App("Messenger", "com.facebook.orca", "M", 0xFF0084FF),
            new App("Facebook", "com.facebook.katana", "F", 0xFF1877F2),
            new App("Discord", "com.discord", "D", 0xFF5865F2),
            new App("Telegram", "org.telegram.messenger", "T", 0xFF2AABEE),
            new App("Chrome", "com.android.chrome", "C", 0xFF4285F4),
    };

    private WindowManager wm;
    private View bubble;
    private View panel;
    private WindowManager.LayoutParams bubbleLp;
    private int bubbleSize;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        running = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIF_ID, buildNotification());
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (bubble == null) addBubble();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        hidePanel();
        if (bubble != null) {
            try {
                wm.removeView(bubble);
            } catch (Exception ignored) {
            }
            bubble = null;
        }
        running = false;
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hidePanel();
        if (bubble != null) snapBubble();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) hidePanel();
    }

    // ------------------------------------------------------------------ notificación

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Panel Gamer", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, OverlayService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this).setPriority(Notification.PRIORITY_MIN);

        b.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Panel Gamer activo")
                .setContentText("Toca la burbuja para abrir tus apps")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                        "Detener", stop).build());
        return b.build();
    }

    // ------------------------------------------------------------------ burbuja

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private DisplayMetrics metrics() {
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        return dm;
    }

    private void addBubble() {
        bubbleSize = Ui.dp(this, 48);

        Ui.IconView icon = new Ui.IconView(this, Ui.IconView.PAD);
        int pad = Ui.dp(this, 9);
        icon.setPadding(pad, pad, pad, pad);
        icon.setBackground(Ui.oval(0xFF7C5CFF, 0xFF2B1A66));
        icon.setAlpha(0.88f);
        bubble = icon;

        bubbleLp = new WindowManager.LayoutParams(
                bubbleSize, bubbleSize, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;
        bubbleLp.x = 0;
        bubbleLp.y = metrics().heightPixels / 4;

        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        bubble.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float downX, downY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = bubbleLp.x;
                        startY = bubbleLp.y;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (Math.abs(dx) > slop || Math.abs(dy) > slop) moved = true;
                        if (moved) {
                            bubbleLp.x = startX + (int) dx;
                            bubbleLp.y = startY + (int) dy;
                            wm.updateViewLayout(bubble, bubbleLp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) snapBubble();
                        else togglePanel();
                        return true;
                    default:
                        return false;
                }
            }
        });

        try {
            wm.addView(bubble, bubbleLp);
        } catch (Exception ex) {
            stopSelf();
        }
    }

    /** Pega la burbuja al borde más cercano para que no estorbe. */
    private void snapBubble() {
        DisplayMetrics dm = metrics();
        int margin = Ui.dp(this, 2);
        bubbleLp.x = (bubbleLp.x + bubbleSize / 2 < dm.widthPixels / 2)
                ? margin
                : dm.widthPixels - bubbleSize - margin;
        bubbleLp.y = Math.max(0, Math.min(bubbleLp.y, dm.heightPixels - bubbleSize));
        try {
            wm.updateViewLayout(bubble, bubbleLp);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ panel

    private void togglePanel() {
        if (panel != null) hidePanel();
        else showPanel();
    }

    private void showPanel() {
        DisplayMetrics dm = metrics();
        int width = Math.min(dm.widthPixels - Ui.dp(this, 32), Ui.dp(this, 320));

        panel = buildPanel();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width, WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;

        panel.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_OUTSIDE) hidePanel();
                return false;
            }
        });

        try {
            wm.addView(panel, lp);
        } catch (Exception ex) {
            panel = null;
        }
    }

    private void hidePanel() {
        if (panel != null) {
            try {
                wm.removeView(panel);
            } catch (Exception ignored) {
            }
            panel = null;
        }
    }

    private View buildPanel() {
        final Context c = this;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        GradientDrawable bg = Ui.rounded(0xF0151326, Ui.dp(c, 22));
        bg.setStroke(Ui.dp(c, 1), 0x33FFFFFF);
        root.setBackground(bg);

        // Encabezado
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(c);
        title.setText("PANEL GAMER");
        title.setTextSize(12);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.15f);
        title.setTextColor(0xFF9D8CFF);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Ui.IconView close = new Ui.IconView(c, Ui.IconView.CLOSE);
        close.setIconColor(0xFFB8B4D8);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hidePanel();
            }
        });
        header.addView(close, new LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30)));
        root.addView(header);

        // Controles de música (funcionan con Spotify y cualquier reproductor)
        LinearLayout media = new LinearLayout(c);
        media.setOrientation(LinearLayout.HORIZONTAL);
        media.setGravity(Gravity.CENTER);
        media.setBackground(Ui.rounded(0x1AFFFFFF, Ui.dp(c, 18)));
        media.addView(mediaButton(Ui.IconView.PREV, KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        media.addView(mediaButton(Ui.IconView.PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        media.addView(mediaButton(Ui.IconView.NEXT, KeyEvent.KEYCODE_MEDIA_NEXT));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = Ui.dp(c, 10);
        root.addView(media, mlp);

        // Apps instaladas
        List<App> installed = new ArrayList<>();
        for (App a : APPS) {
            if (getPackageManager().getLaunchIntentForPackage(a.pkg) != null) installed.add(a);
        }

        if (installed.isEmpty()) {
            TextView none = new TextView(c);
            none.setText("No encontré apps compatibles instaladas");
            none.setTextColor(0xFFB8B4D8);
            none.setTextSize(12);
            none.setGravity(Gravity.CENTER);
            none.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 16));
            root.addView(none);
        } else {
            final int cols = 4;
            LinearLayout row = null;
            for (int i = 0; i < installed.size(); i++) {
                if (i % cols == 0) {
                    row = new LinearLayout(c);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    rlp.topMargin = Ui.dp(c, 10);
                    root.addView(row, rlp);
                }
                row.addView(appTile(installed.get(i)),
                        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            }
            // Rellena la última fila para que las columnas queden alineadas
            int rest = (cols - installed.size() % cols) % cols;
            for (int i = 0; i < rest; i++) {
                row.addView(new View(c),
                        new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }

        // Pie
        TextView stop = new TextView(c);
        stop.setText("Detener panel");
        stop.setTextSize(12);
        stop.setTextColor(0xFFFF7A8A);
        stop.setGravity(Gravity.CENTER);
        stop.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 2));
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSelf();
            }
        });
        root.addView(stop);

        return root;
    }

    private View mediaButton(int iconType, final int keyCode) {
        Ui.IconView v = new Ui.IconView(this, iconType);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMediaKey(keyCode);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f);
        v.setLayoutParams(lp);
        return v;
    }

    private View appTile(final App app) {
        final Context c = this;
        LinearLayout tile = new LinearLayout(c);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(Ui.dp(c, 2), Ui.dp(c, 4), Ui.dp(c, 2), Ui.dp(c, 4));

        TextView circle = new TextView(c);
        circle.setText(app.letter);
        circle.setTextColor(0xFFFFFFFF);
        circle.setTextSize(18);
        circle.setTypeface(Typeface.DEFAULT_BOLD);
        circle.setGravity(Gravity.CENTER);
        circle.setBackground(Ui.oval(app.color, app.color));
        tile.addView(circle, new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));

        TextView label = new TextView(c);
        label.setText(app.name);
        label.setTextSize(10);
        label.setTextColor(0xFFD7D4EE);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dp(c, 4);
        tile.addView(label, llp);

        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launch(app.pkg);
            }
        });
        return tile;
    }

    // ------------------------------------------------------------------ acciones

    private void sendMediaKey(int keyCode) {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        } catch (Exception ignored) {
        }
    }

    /**
     * Abre la app. Si el sistema soporta ventanas flotantes (modo freeform) la abre
     * en una ventana; si no, la abre normal y se vuelve al juego con el botón de recientes.
     */
    private void launch(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        Bundle opts = null;
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
                DisplayMetrics dm = metrics();
                int w = dm.widthPixels, h = dm.heightPixels;
                Rect r = new Rect(w / 10, h / 6, w - w / 10, h - h / 6);
                ActivityOptions o = ActivityOptions.makeBasic();
                o.setLaunchBounds(r);
                opts = o.toBundle();
            } catch (Throwable ignored) {
            }
        }
        hidePanel();
        try {
            startActivity(i, opts);
        } catch (Exception ignored) {
        }
    }
}
