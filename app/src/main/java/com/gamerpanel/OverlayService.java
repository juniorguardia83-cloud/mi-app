package com.gamerpanel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.Service;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Burbuja flotante con forma de control de consola. Al tocarla se abre un panel.
 * Desde el panel se abren ventanas chicas (con una X abajo, arrastrables y con el
 * tamaño ajustable) para la música (Lark Player, Spotify...) y los mensajes
 * (WhatsApp, Telegram...), conectadas a las apps del celular sin iniciar sesión.
 */
public class OverlayService extends Service {

    static final String ACTION_STOP = "com.gamerpanel.STOP";
    private static final String CHANNEL_ID = "panel_gamer";
    private static final int NOTIF_ID = 1;

    static volatile boolean running = false;

    /** Un acceso del panel. */
    private static final class Item {
        final String name, letter;
        final int color;
        final String[] pkgs;

        Item(String name, String letter, int color, String... pkgs) {
            this.name = name;
            this.letter = letter;
            this.color = color;
            this.pkgs = pkgs;
        }
    }

    private static final Item[] MUSIC = {
            new Item("Lark Player", "L", 0xFFFF5A5F, "com.dywx.larkplayer"),
            new Item("Spotify", "S", 0xFF1DB954, "com.spotify.music"),
            new Item("YT Music", "Y", 0xFFE62117, "com.google.android.apps.youtube.music"),
    };

    private static final Item[] CHATS = {
            new Item("WhatsApp", "W", 0xFF25D366, "com.whatsapp", "com.whatsapp.w4b"),
            new Item("Telegram", "T", 0xFF2AABEE, "org.telegram.messenger"),
            new Item("Messenger", "M", 0xFF0084FF, "com.facebook.orca"),
            new Item("Instagram", "I", 0xFFE1306C, "com.instagram.android"),
            new Item("Discord", "D", 0xFF5865F2, "com.discord"),
    };

    private WindowManager wm;
    private SharedPreferences prefs;
    private View bubble;
    private View panel;
    private WindowManager.LayoutParams bubbleLp;
    private int bubbleSize;

    // Ventana chica
    private View mini;
    private WindowManager.LayoutParams miniLp;
    private boolean miniFocusable = false;
    private Runnable miniCleanup;
    private String miniKind = "";
    private float[] miniDef = {0.8f, 0.4f, 0.45f, 0.72f};

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        prefs = getSharedPreferences("panel", MODE_PRIVATE);
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
        hideMini();
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
        if (mini != null) layoutMini(true);
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
                .setContentText("Toca la burbuja para abrir el panel")
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

        LinearLayout content = buildPanelContent();
        ScrollView sv = new ScrollView(this);
        GradientDrawable bg = Ui.rounded(0xF0151326, Ui.dp(this, 22));
        bg.setStroke(Ui.dp(this, 1), 0x33FFFFFF);
        sv.setBackground(bg);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(content);

        content.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = Math.min(content.getMeasuredHeight(), (int) (dm.heightPixels * 0.88f));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width, height, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;

        sv.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_OUTSIDE) hidePanel();
                return false;
            }
        });

        panel = sv;
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

    private boolean installed(String... pkgs) {
        for (String p : pkgs) {
            if (getPackageManager().getLaunchIntentForPackage(p) != null) return true;
        }
        return false;
    }

    private LinearLayout buildPanelContent() {
        final Context c = this;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));

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
        close.setOnClickListener(v -> hidePanel());
        header.addView(close, new LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30)));
        root.addView(header);

        // Música
        root.addView(sectionLabel("MÚSICA"));
        List<View> music = new ArrayList<>();
        music.add(tile("Música", "♪", 0xFF7C5CFF, v -> openMusic(null, "Música")));
        for (final Item it : MUSIC) {
            if (!installed(it.pkgs)) continue;
            music.add(tile(it.name, it.letter, it.color, v -> openMusic(it.pkgs[0], it.name)));
        }
        addGrid(root, music);

        // Mensajes
        root.addView(sectionLabel("MENSAJES"));
        List<View> chats = new ArrayList<>();
        chats.add(tile("Todos", "✉", 0xFF7C5CFF, v -> openMessages(null, "Mensajes")));
        for (final Item it : CHATS) {
            if (!installed(it.pkgs)) continue;
            final Set<String> set = new HashSet<>();
            for (String p : it.pkgs) set.add(p);
            chats.add(tile(it.name, it.letter, it.color, v -> openMessages(set, it.name)));
        }
        addGrid(root, chats);

        // Pie
        TextView stop = new TextView(c);
        stop.setText("Detener panel");
        stop.setTextSize(12);
        stop.setTextColor(0xFFFF7A8A);
        stop.setGravity(Gravity.CENTER);
        stop.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 2));
        stop.setOnClickListener(v -> stopSelf());
        root.addView(stop);

        return root;
    }

    private TextView sectionLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(10);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.12f);
        t.setTextColor(0xFF7E7AA3);
        t.setPadding(Ui.dp(this, 2), Ui.dp(this, 14), 0, 0);
        return t;
    }

    private void addGrid(LinearLayout root, List<View> tiles) {
        final int cols = 4;
        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = Ui.dp(this, 6);
                root.addView(row, rlp);
            }
            row.addView(tiles.get(i),
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        if (row != null) {
            int rest = (cols - tiles.size() % cols) % cols;
            for (int i = 0; i < rest; i++) {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
    }

    private View tile(String name, String letter, int color, View.OnClickListener click) {
        final Context c = this;
        LinearLayout tile = new LinearLayout(c);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(Ui.dp(c, 2), Ui.dp(c, 4), Ui.dp(c, 2), Ui.dp(c, 4));

        TextView circle = new TextView(c);
        circle.setText(letter);
        circle.setTextColor(0xFFFFFFFF);
        circle.setTextSize(18);
        circle.setTypeface(Typeface.DEFAULT_BOLD);
        circle.setGravity(Gravity.CENTER);
        circle.setBackground(Ui.oval(color, color));
        tile.addView(circle, new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));

        TextView label = new TextView(c);
        label.setText(name);
        label.setTextSize(10);
        label.setTextColor(0xFFD7D4EE);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dp(c, 4);
        tile.addView(label, llp);

        tile.setOnClickListener(click);
        return tile;
    }

    // ------------------------------------------------------------------ ventana chica

    private void openMusic(String preferPkg, String title) {
        hidePanel();
        hideMini();
        if (!NotifService.isEnabled(this)) {
            openWindow("perm", title, permissionView(), null, 0.8f, 0.36f, 0.45f, 0.72f);
            return;
        }
        final MusicUi ui = new MusicUi(preferPkg);
        openWindow("music", title, ui.view, ui::cleanup, 0.8f, 0.36f, 0.44f, 0.72f);
    }

    private void openMessages(Set<String> pkgs, String title) {
        hidePanel();
        hideMini();
        if (!NotifService.isEnabled(this)) {
            openWindow("perm", title, permissionView(), null, 0.8f, 0.36f, 0.45f, 0.72f);
            return;
        }
        final MessagesUi ui = new MessagesUi(pkgs != null ? pkgs : NotifService.MSG_PKGS);
        openWindow("msg", title, ui.holder, ui::cleanup, 0.8f, 0.42f, 0.46f, 0.74f);
    }

    /** Ventana con barra para arrastrar, contenido, X abajo y esquina para cambiar el tamaño. */
    private void openWindow(String kind, String title, View content, Runnable cleanup,
                            float pw, float ph, float lw, float lh) {
        final Context c = this;
        miniKind = kind;
        miniDef = new float[]{pw, ph, lw, lh};
        miniCleanup = cleanup;

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = Ui.rounded(0xFF151326, Ui.dp(c, 16));
        bg.setStroke(Ui.dp(c, 1), 0x55FFFFFF);
        root.setBackground(bg);

        // Barra de arriba: sirve para arrastrar la ventana
        TextView top = new TextView(c);
        top.setText(title.toUpperCase() + "  •  arrastra aquí");
        top.setTextSize(10);
        top.setTypeface(Typeface.DEFAULT_BOLD);
        top.setLetterSpacing(0.08f);
        top.setTextColor(0xFF9D8CFF);
        top.setGravity(Gravity.CENTER);
        root.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 28)));

        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Barra de abajo: X para cerrar y esquina para ajustar el tamaño
        FrameLayout bottom = new FrameLayout(c);
        Ui.IconView x = new Ui.IconView(c, Ui.IconView.CLOSE);
        x.setBackground(Ui.oval(0xFFE5484D, 0xFFB3263B));
        x.setOnClickListener(v -> hideMini());
        FrameLayout.LayoutParams xlp = new FrameLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32));
        xlp.gravity = Gravity.CENTER;
        bottom.addView(x, xlp);

        Ui.IconView grip = new Ui.IconView(c, Ui.IconView.GRIP);
        grip.setIconColor(0xFFB8B4D8);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34));
        glp.gravity = Gravity.END | Gravity.BOTTOM;
        bottom.addView(grip, glp);
        root.addView(bottom, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 40)));

        miniFocusable = false;
        miniLp = new WindowManager.LayoutParams(
                100, 100, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        miniLp.gravity = Gravity.TOP | Gravity.START;
        miniLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;

        // Tocar fuera: se quita el teclado y el juego vuelve a recibir los toques
        root.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_OUTSIDE) setMiniFocusable(false);
            return false;
        });

        // Arrastrar la ventana desde la barra de arriba
        top.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float downX, downY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = miniLp.x;
                        startY = miniLp.y;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        DisplayMetrics dm = metrics();
                        int nx = startX + (int) (e.getRawX() - downX);
                        int ny = startY + (int) (e.getRawY() - downY);
                        miniLp.x = Math.max(0, Math.min(nx, dm.widthPixels - miniLp.width));
                        miniLp.y = Math.max(0, Math.min(ny, dm.heightPixels - miniLp.height));
                        applyMini();
                        return true;
                    default:
                        return true;
                }
            }
        });

        // Cambiar el tamaño desde la esquina de abajo a la derecha
        grip.setOnTouchListener(new View.OnTouchListener() {
            int startW, startH;
            float downX, downY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                DisplayMetrics dm = metrics();
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startW = miniLp.width;
                        startH = miniLp.height;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int nw = startW + (int) (e.getRawX() - downX);
                        int nh = startH + (int) (e.getRawY() - downY);
                        miniLp.width = Math.max(Ui.dp(OverlayService.this, 190),
                                Math.min(nw, dm.widthPixels - miniLp.x));
                        miniLp.height = Math.max(Ui.dp(OverlayService.this, 170),
                                Math.min(nh, dm.heightPixels - miniLp.y));
                        applyMini();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        boolean land = dm.widthPixels > dm.heightPixels;
                        prefs.edit()
                                .putFloat(prefKey(land, "w"), miniLp.width / (float) dm.widthPixels)
                                .putFloat(prefKey(land, "h"), miniLp.height / (float) dm.heightPixels)
                                .apply();
                        return true;
                    default:
                        return true;
                }
            }
        });

        mini = root;
        layoutMini(false);
        try {
            wm.addView(mini, miniLp);
        } catch (Exception ex) {
            mini = null;
            runCleanup();
        }
    }

    private String prefKey(boolean land, String part) {
        return miniKind + (land ? "_l_" : "_p_") + part;
    }

    /** Tamaño: el que dejó el usuario, o uno chico por defecto (ni mitad ni pantalla entera). */
    private void layoutMini(boolean update) {
        DisplayMetrics dm = metrics();
        boolean land = dm.widthPixels > dm.heightPixels;
        float fw = prefs.getFloat(prefKey(land, "w"), land ? miniDef[2] : miniDef[0]);
        float fh = prefs.getFloat(prefKey(land, "h"), land ? miniDef[3] : miniDef[1]);
        miniLp.width = Math.max(Ui.dp(this, 190), Math.min((int) (dm.widthPixels * fw), dm.widthPixels));
        miniLp.height = Math.max(Ui.dp(this, 170), Math.min((int) (dm.heightPixels * fh), dm.heightPixels));
        miniLp.x = (dm.widthPixels - miniLp.width) / 2;
        miniLp.y = (dm.heightPixels - miniLp.height) / 2;
        if (update) applyMini();
    }

    private void applyMini() {
        if (mini == null) return;
        try {
            wm.updateViewLayout(mini, miniLp);
        } catch (Exception ignored) {
        }
    }

    /** Activa o desactiva el teclado. Sin foco, el juego sigue recibiendo el control. */
    private void setMiniFocusable(boolean focusable) {
        if (mini == null || focusable == miniFocusable) return;
        miniFocusable = focusable;
        if (focusable) {
            miniLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            try {
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mini.getWindowToken(), 0);
            } catch (Exception ignored) {
            }
            miniLp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        applyMini();
    }

    private void hideMini() {
        if (mini != null) {
            try {
                wm.removeView(mini);
            } catch (Exception ignored) {
            }
            mini = null;
        }
        runCleanup();
    }

    private void runCleanup() {
        Runnable r = miniCleanup;
        miniCleanup = null;
        if (r != null) r.run();
    }

    private View permissionView() {
        final Context c = this;
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        v.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));

        TextView msg = new TextView(c);
        msg.setText("Falta dar \"Acceso a notificaciones\" a Panel Gamer. Así lee tu música y "
                + "tus mensajes directo del celular, sin iniciar sesión.");
        msg.setTextSize(12);
        msg.setTextColor(0xFFD7D4EE);
        msg.setGravity(Gravity.CENTER);
        v.addView(msg);

        TextView btn = new TextView(c);
        btn.setText("Dar acceso");
        btn.setTextSize(13);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(Ui.gradient(0xFF7C5CFF, 0xFF4C2FD6, Ui.dp(c, 14)));
        btn.setOnClickListener(x -> {
            hideMini();
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                Ui.dp(c, 150), Ui.dp(c, 40));
        blp.topMargin = Ui.dp(c, 12);
        v.addView(btn, blp);
        return v;
    }

    // ------------------------------------------------------------------ música

    /** Reproductor chico: se conecta a la app que esté sonando (Lark Player, Spotify...). */
    private final class MusicUi {
        final String prefer;
        final MediaSessionManager msm;
        final ComponentName cn;
        MediaController ctrl;
        final LinearLayout view;
        final ImageView art;
        final TextView tTitle, tArtist, tApp;
        final Ui.IconView play;

        final MediaController.Callback cb = new MediaController.Callback() {
            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                bind();
            }

            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                bind();
            }

            @Override
            public void onSessionDestroyed() {
                pick();
            }
        };

        final MediaSessionManager.OnActiveSessionsChangedListener sl =
                new MediaSessionManager.OnActiveSessionsChangedListener() {
                    @Override
                    public void onActiveSessionsChanged(List<MediaController> controllers) {
                        pick();
                    }
                };

        MusicUi(String prefer) {
            this.prefer = prefer;
            final Context c = OverlayService.this;
            msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            cn = new ComponentName(c, NotifService.class);

            view = new LinearLayout(c);
            view.setOrientation(LinearLayout.VERTICAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4));

            LinearLayout info = new LinearLayout(c);
            info.setOrientation(LinearLayout.HORIZONTAL);
            info.setGravity(Gravity.CENTER_VERTICAL);

            art = new ImageView(c);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setBackground(Ui.rounded(0x22FFFFFF, Ui.dp(c, 10)));
            info.addView(art, new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56)));

            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(Ui.dp(c, 10), 0, 0, 0);
            tTitle = text(c, 14, 0xFFFFFFFF, true);
            tArtist = text(c, 12, 0xFFB8B4D8, false);
            tApp = text(c, 10, 0xFF7E7AA3, false);
            texts.addView(tTitle);
            texts.addView(tArtist);
            texts.addView(tApp);
            info.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            view.addView(info);

            LinearLayout controls = new LinearLayout(c);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            controls.setGravity(Gravity.CENTER);
            controls.setBackground(Ui.rounded(0x1AFFFFFF, Ui.dp(c, 18)));
            Ui.IconView prev = new Ui.IconView(c, Ui.IconView.PREV);
            play = new Ui.IconView(c, Ui.IconView.PLAY);
            Ui.IconView next = new Ui.IconView(c, Ui.IconView.NEXT);
            prev.setOnClickListener(v -> {
                if (ctrl != null) ctrl.getTransportControls().skipToPrevious();
                else sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            });
            play.setOnClickListener(v -> {
                if (ctrl == null) {
                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                    return;
                }
                PlaybackState st = ctrl.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                    ctrl.getTransportControls().pause();
                } else {
                    ctrl.getTransportControls().play();
                }
            });
            next.setOnClickListener(v -> {
                if (ctrl != null) ctrl.getTransportControls().skipToNext();
                else sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
            });
            controls.addView(prev, new LinearLayout.LayoutParams(0, Ui.dp(c, 46), 1f));
            controls.addView(play, new LinearLayout.LayoutParams(0, Ui.dp(c, 46), 1f));
            controls.addView(next, new LinearLayout.LayoutParams(0, Ui.dp(c, 46), 1f));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.topMargin = Ui.dp(c, 10);
            view.addView(controls, clp);

            try {
                msm.addOnActiveSessionsChangedListener(sl, cn);
            } catch (Exception ignored) {
            }
            pick();
        }

        private TextView text(Context c, float sp, int color, boolean bold) {
            TextView t = new TextView(c);
            t.setTextSize(sp);
            t.setTextColor(color);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
            return t;
        }

        void pick() {
            MediaController next = null;
            try {
                List<MediaController> l = msm.getActiveSessions(cn);
                if (prefer != null) {
                    for (MediaController m : l) {
                        if (prefer.equals(m.getPackageName())) {
                            next = m;
                            break;
                        }
                    }
                }
                if (next == null) {
                    for (MediaController m : l) {
                        PlaybackState st = m.getPlaybackState();
                        if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                            next = m;
                            break;
                        }
                    }
                }
                if (next == null && !l.isEmpty()) next = l.get(0);
            } catch (Exception ignored) {
            }
            try {
                if (ctrl != null) ctrl.unregisterCallback(cb);
            } catch (Exception ignored) {
            }
            ctrl = next;
            if (ctrl != null) {
                try {
                    ctrl.registerCallback(cb);
                } catch (Exception ignored) {
                }
            }
            bind();
        }

        void bind() {
            if (ctrl == null) {
                tTitle.setText("Sin música activa");
                tArtist.setText("Dale play a tu reproductor una vez");
                tApp.setText("");
                art.setImageDrawable(null);
                play.setType(Ui.IconView.PLAY);
                return;
            }
            String title = null, artist = null;
            Bitmap bmp = null;
            MediaMetadata md = ctrl.getMetadata();
            if (md != null) {
                title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                if (title == null) title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null) artist = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (bmp == null) bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (bmp == null) bmp = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            }
            tTitle.setText(title != null ? title : "Reproduciendo");
            tArtist.setText(artist != null ? artist : "");
            art.setImageBitmap(bmp);

            String pkg = ctrl.getPackageName();
            try {
                PackageManager pm = getPackageManager();
                tApp.setText(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            } catch (Exception e) {
                tApp.setText(pkg);
            }

            PlaybackState st = ctrl.getPlaybackState();
            boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
            play.setType(playing ? Ui.IconView.PAUSE : Ui.IconView.PLAY);
        }

        void cleanup() {
            try {
                if (ctrl != null) ctrl.unregisterCallback(cb);
            } catch (Exception ignored) {
            }
            try {
                msm.removeOnActiveSessionsChangedListener(sl);
            } catch (Exception ignored) {
            }
            ctrl = null;
        }
    }

    private void sendMediaKey(int keyCode) {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ mensajes

    private static int chatColor(String pkg) {
        switch (pkg) {
            case "com.whatsapp":
            case "com.whatsapp.w4b":
                return 0xFF25D366;
            case "org.telegram.messenger":
                return 0xFF2AABEE;
            case "com.facebook.orca":
                return 0xFF0084FF;
            case "com.instagram.android":
                return 0xFFE1306C;
            case "com.discord":
                return 0xFF5865F2;
            default:
                return 0xFF7C5CFF;
        }
    }

    private static String chatLetter(String pkg) {
        switch (pkg) {
            case "com.whatsapp":
            case "com.whatsapp.w4b":
                return "W";
            case "org.telegram.messenger":
                return "T";
            case "com.facebook.orca":
                return "M";
            case "com.instagram.android":
                return "I";
            case "com.discord":
                return "D";
            default:
                return "?";
        }
    }

    /** Lista de chats con mensajes nuevos; al tocar uno se puede leer y responder. */
    private final class MessagesUi {
        final Set<String> pkgs;
        final FrameLayout holder;
        String openKey;
        LinearLayout msgBox;
        ScrollView msgScroll;
        EditText input;

        MessagesUi(Set<String> pkgs) {
            this.pkgs = pkgs;
            holder = new FrameLayout(OverlayService.this);
            NotifService.onChange = this::refresh;
            showList();
        }

        void cleanup() {
            NotifService.onChange = null;
        }

        void refresh() {
            if (mini == null) return;
            if (openKey == null) showList();
            else fillLines();
        }

        void showList() {
            final Context c = OverlayService.this;
            openKey = null;
            holder.removeAllViews();

            ScrollView sv = new ScrollView(c);
            sv.setVerticalScrollBarEnabled(false);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
            sv.addView(col);

            List<NotifService.Conv> list = NotifService.list(pkgs);
            if (list.isEmpty()) {
                TextView none = new TextView(c);
                none.setText("No hay mensajes nuevos.\nCuando te llegue uno aparece aquí y lo puedes responder.");
                none.setTextSize(12);
                none.setTextColor(0xFFB8B4D8);
                none.setGravity(Gravity.CENTER);
                none.setPadding(0, Ui.dp(c, 24), 0, 0);
                col.addView(none);
            }
            for (final NotifService.Conv conv : list) {
                LinearLayout row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
                row.setBackground(Ui.rounded(0x14FFFFFF, Ui.dp(c, 12)));

                TextView badge = new TextView(c);
                badge.setText(chatLetter(conv.pkg));
                badge.setTextColor(0xFFFFFFFF);
                badge.setTextSize(14);
                badge.setTypeface(Typeface.DEFAULT_BOLD);
                badge.setGravity(Gravity.CENTER);
                badge.setBackground(Ui.oval(chatColor(conv.pkg), chatColor(conv.pkg)));
                row.addView(badge, new LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34)));

                LinearLayout texts = new LinearLayout(c);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(Ui.dp(c, 10), 0, 0, 0);
                TextView t1 = new TextView(c);
                t1.setText(conv.title);
                t1.setTextSize(13);
                t1.setTextColor(0xFFFFFFFF);
                t1.setTypeface(Typeface.DEFAULT_BOLD);
                t1.setSingleLine(true);
                t1.setEllipsize(TextUtils.TruncateAt.END);
                TextView t2 = new TextView(c);
                t2.setText(conv.lines.get(conv.lines.size() - 1));
                t2.setTextSize(12);
                t2.setTextColor(0xFFB8B4D8);
                t2.setMaxLines(2);
                t2.setEllipsize(TextUtils.TruncateAt.END);
                texts.addView(t1);
                texts.addView(t2);
                row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                row.setOnClickListener(v -> showChat(conv.key));
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = Ui.dp(c, 6);
                col.addView(row, rlp);
            }
            holder.addView(sv);
        }

        void showChat(String key) {
            final Context c = OverlayService.this;
            final NotifService.Conv conv = NotifService.get(key);
            if (conv == null) {
                showList();
                return;
            }
            openKey = key;
            holder.removeAllViews();

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), Ui.dp(c, 4));

            LinearLayout head = new LinearLayout(c);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            Ui.IconView back = new Ui.IconView(c, Ui.IconView.BACK);
            back.setOnClickListener(v -> showList());
            head.addView(back, new LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32)));
            TextView ttl = new TextView(c);
            ttl.setText(conv.title);
            ttl.setTextSize(13);
            ttl.setTextColor(0xFFFFFFFF);
            ttl.setTypeface(Typeface.DEFAULT_BOLD);
            ttl.setSingleLine(true);
            ttl.setEllipsize(TextUtils.TruncateAt.END);
            head.addView(ttl, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            col.addView(head);

            msgScroll = new ScrollView(c);
            msgScroll.setVerticalScrollBarEnabled(false);
            msgBox = new LinearLayout(c);
            msgBox.setOrientation(LinearLayout.VERTICAL);
            msgScroll.addView(msgBox);
            col.addView(msgScroll, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

            if (conv.reply != null) {
                LinearLayout bar = new LinearLayout(c);
                bar.setOrientation(LinearLayout.HORIZONTAL);
                bar.setGravity(Gravity.CENTER_VERTICAL);

                input = new EditText(c);
                input.setHint("Responder…");
                input.setHintTextColor(0xFF7E7AA3);
                input.setTextColor(0xFFFFFFFF);
                input.setTextSize(13);
                input.setSingleLine(true);
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                input.setBackground(Ui.rounded(0x1AFFFFFF, Ui.dp(c, 18)));
                input.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
                // Al tocar el campo se activa el teclado
                input.setOnTouchListener((v, e) -> {
                    if (e.getAction() == MotionEvent.ACTION_DOWN) {
                        setMiniFocusable(true);
                        input.postDelayed(() -> {
                            try {
                                input.requestFocus();
                                InputMethodManager imm = (InputMethodManager)
                                        getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                            } catch (Exception ignored) {
                            }
                        }, 150);
                    }
                    return false;
                });
                bar.addView(input, new LinearLayout.LayoutParams(0, Ui.dp(c, 40), 1f));

                Ui.IconView send = new Ui.IconView(c, Ui.IconView.SEND);
                send.setBackground(Ui.oval(0xFF7C5CFF, 0xFF4C2FD6));
                send.setOnClickListener(v -> {
                    String t = input.getText().toString().trim();
                    if (t.isEmpty()) return;
                    if (sendReply(conv, t)) {
                        input.setText("");
                        fillLines();
                    }
                });
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38));
                slp.leftMargin = Ui.dp(c, 6);
                bar.addView(send, slp);

                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                blp.topMargin = Ui.dp(c, 6);
                col.addView(bar, blp);
            } else {
                TextView no = new TextView(c);
                no.setText("Esta app no deja responder desde aquí.");
                no.setTextSize(11);
                no.setTextColor(0xFF7E7AA3);
                no.setGravity(Gravity.CENTER);
                no.setPadding(0, Ui.dp(c, 6), 0, 0);
                col.addView(no);
            }
            holder.addView(col);
            fillLines();
        }

        void fillLines() {
            final Context c = OverlayService.this;
            NotifService.Conv conv = openKey != null ? NotifService.get(openKey) : null;
            if (conv == null || msgBox == null) {
                showList();
                return;
            }
            msgBox.removeAllViews();
            for (String line : conv.lines) {
                boolean mine = line.startsWith("Tú: ");
                TextView t = new TextView(c);
                t.setText(line);
                t.setTextSize(12);
                t.setTextColor(0xFFFFFFFF);
                t.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
                t.setBackground(Ui.rounded(mine ? 0x667C5CFF : 0x1AFFFFFF, Ui.dp(c, 12)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Ui.dp(c, 4);
                lp.gravity = mine ? Gravity.END : Gravity.START;
                msgBox.addView(t, lp);
            }
            msgScroll.post(() -> msgScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    /** Responde usando la acción "Responder" de la propia notificación de la app. */
    private boolean sendReply(NotifService.Conv conv, String text) {
        try {
            Notification.Action a = conv.reply;
            RemoteInput[] ris = a.getRemoteInputs();
            Intent i = new Intent();
            Bundle b = new Bundle();
            for (RemoteInput ri : ris) b.putCharSequence(ri.getResultKey(), text);
            RemoteInput.addResultsToIntent(ris, i, b);
            a.actionIntent.send(this, 0, i);
            conv.lines.add("Tú: " + text);
            while (conv.lines.size() > 10) conv.lines.remove(0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
