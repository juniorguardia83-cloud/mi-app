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
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Burbuja flotante con forma de control de consola. Al tocarla se abre un panel.
 * Desde el panel se abren las apps en una VENTANA CHICA flotante (con una X abajo
 * para cerrarla), arrastrable, encima del juego.
 * Pensado para gastar poca RAM: el panel y la ventana se crean al abrirlos y se
 * destruyen al cerrarlos.
 */
public class OverlayService extends Service {

    static final String ACTION_STOP = "com.gamerpanel.STOP";
    private static final String CHANNEL_ID = "panel_gamer";
    private static final int NOTIF_ID = 1;

    private static final String UA_DESKTOP =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/140.0.0.0 Safari/537.36";

    static volatile boolean running = false;

    /** App que se abre en pantalla completa (la app instalada). */
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

    /** App que se abre en la ventana chica (versión web). */
    private static final class Web {
        final String name, url, letter;
        final int color;
        final boolean desktop;

        Web(String name, String url, String letter, int color, boolean desktop) {
            this.name = name;
            this.url = url;
            this.letter = letter;
            this.color = color;
            this.desktop = desktop;
        }
    }

    private static final Web[] WEBS = {
            new Web("WhatsApp", "https://web.whatsapp.com/", "W", 0xFF25D366, true),
            new Web("YouTube", "https://m.youtube.com/", "Y", 0xFFE62117, false),
            new Web("Spotify", "https://open.spotify.com/", "S", 0xFF1DB954, true),
            new Web("Instagram", "https://www.instagram.com/", "I", 0xFFE1306C, false),
            new Web("TikTok", "https://www.tiktok.com/", "T", 0xFF3A3A4A, false),
            new Web("Telegram", "https://web.telegram.org/k/", "T", 0xFF2AABEE, false),
            new Web("Discord", "https://discord.com/app", "D", 0xFF5865F2, true),
            new Web("Facebook", "https://m.facebook.com/", "F", 0xFF1877F2, false),
            new Web("Buscar", "https://www.google.com/", "G", 0xFF4285F4, false),
    };

    private static final App[] APPS = {
            new App("Lark Player", "com.dywx.larkplayer", "L", 0xFFFF5A5F),
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

    // Ventana chica
    private View mini;
    private WebView miniWeb;
    private WindowManager.LayoutParams miniLp;
    private boolean miniFocusable = false;

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
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hidePanel();
            }
        });
        header.addView(close, new LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30)));
        root.addView(header);

        // Controles de música (funcionan con Lark Player, Spotify y cualquier reproductor)
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

        // Ventana chica
        root.addView(sectionLabel("EN VENTANA CHICA"));
        List<View> webTiles = new ArrayList<>();
        for (final Web w : WEBS) {
            webTiles.add(tile(w.name, w.letter, w.color, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showMini(w);
                }
            }));
        }
        addGrid(root, webTiles);

        // Apps instaladas (pantalla completa)
        List<View> appTiles = new ArrayList<>();
        for (final App a : APPS) {
            if (getPackageManager().getLaunchIntentForPackage(a.pkg) == null) continue;
            appTiles.add(tile(a.name, a.letter, a.color, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launch(a.pkg);
                }
            }));
        }
        if (!appTiles.isEmpty()) {
            root.addView(sectionLabel("APP COMPLETA"));
            addGrid(root, appTiles);
        }

        // Pie
        TextView stop = new TextView(c);
        stop.setText("Detener panel");
        stop.setTextSize(12);
        stop.setTextColor(0xFFFF7A8A);
        stop.setGravity(Gravity.CENTER);
        stop.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 2));
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSelf();
            }
        });
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

    private View mediaButton(int iconType, final int keyCode) {
        Ui.IconView v = new Ui.IconView(this, iconType);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMediaKey(keyCode);
            }
        });
        v.setLayoutParams(new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));
        return v;
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

    private void showMini(Web web) {
        hidePanel();
        hideMini();
        final Context c = this;

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = Ui.rounded(0xFF151326, Ui.dp(c, 16));
        bg.setStroke(Ui.dp(c, 1), 0x55FFFFFF);
        root.setBackground(bg);

        // Barra de arriba: sirve para arrastrar la ventana
        TextView top = new TextView(c);
        top.setText(web.name.toUpperCase() + "  •  arrastra aquí");
        top.setTextSize(10);
        top.setTypeface(Typeface.DEFAULT_BOLD);
        top.setLetterSpacing(0.08f);
        top.setTextColor(0xFF9D8CFF);
        top.setGravity(Gravity.CENTER);
        root.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 28)));

        // Contenido
        miniWeb = new WebView(c);
        WebSettings s = miniWeb.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        if (web.desktop) s.setUserAgentString(UA_DESKTOP);
        try {
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(miniWeb, true);
        } catch (Exception ignored) {
        }
        miniWeb.setBackgroundColor(0xFF0F0D1C);
        miniWeb.setWebViewClient(new WebViewClient());
        miniWeb.setFocusable(true);
        miniWeb.setFocusableInTouchMode(true);
        // Al tocar la página se activa el teclado; al tocar fuera, vuelve al juego.
        miniWeb.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) setMiniFocusable(true);
                return false;
            }
        });
        miniWeb.loadUrl(web.url);
        root.addView(miniWeb, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Barra de abajo con la X para cerrar
        LinearLayout bottom = new LinearLayout(c);
        bottom.setGravity(Gravity.CENTER);
        Ui.IconView x = new Ui.IconView(c, Ui.IconView.CLOSE);
        x.setBackground(Ui.oval(0xFFE5484D, 0xFFB3263B));
        x.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideMini();
            }
        });
        bottom.addView(x, new LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34)));
        root.addView(bottom, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 46)));

        miniFocusable = false;
        miniLp = new WindowManager.LayoutParams(
                100, 100, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        miniLp.gravity = Gravity.TOP | Gravity.START;
        miniLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;

        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_OUTSIDE) setMiniFocusable(false);
                return false;
            }
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
                        try {
                            wm.updateViewLayout(mini, miniLp);
                        } catch (Exception ignored) {
                        }
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
            destroyMiniWeb();
        }
    }

    /**
     * Tamaño de la ventana: ni mitad ni diminuta.
     * Vertical: casi todo el ancho y poco más de la mitad del alto.
     * Horizontal (jugando): un poco más de la mitad del ancho y casi todo el alto.
     */
    private void layoutMini(boolean update) {
        DisplayMetrics dm = metrics();
        boolean land = dm.widthPixels > dm.heightPixels;
        miniLp.width = (int) (dm.widthPixels * (land ? 0.55f : 0.92f));
        miniLp.height = (int) (dm.heightPixels * (land ? 0.82f : 0.55f));
        miniLp.x = (dm.widthPixels - miniLp.width) / 2;
        miniLp.y = (dm.heightPixels - miniLp.height) / 2;
        if (update) {
            try {
                wm.updateViewLayout(mini, miniLp);
            } catch (Exception ignored) {
            }
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
        try {
            wm.updateViewLayout(mini, miniLp);
        } catch (Exception ignored) {
        }
    }

    private void hideMini() {
        if (mini != null) {
            try {
                wm.removeView(mini);
            } catch (Exception ignored) {
            }
            mini = null;
        }
        destroyMiniWeb();
    }

    private void destroyMiniWeb() {
        if (miniWeb != null) {
            try {
                miniWeb.stopLoading();
                miniWeb.loadUrl("about:blank");
                if (miniWeb.getParent() instanceof ViewGroup) {
                    ((ViewGroup) miniWeb.getParent()).removeView(miniWeb);
                }
                miniWeb.destroy();
            } catch (Exception ignored) {
            }
            miniWeb = null;
        }
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
     * Abre la app instalada a pantalla completa. Si el sistema soporta ventanas
     * flotantes (modo freeform) la abre en una ventana de ese tamaño.
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
