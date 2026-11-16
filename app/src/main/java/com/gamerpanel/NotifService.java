package com.gamerpanel;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lee las notificaciones de mensajes (WhatsApp, Telegram...) para poder mostrarlas
 * y responderlas desde la ventana chica, sin iniciar sesión en nada.
 * También es lo que permite controlar el reproductor (Lark Player, Spotify...).
 * Todo se queda en la memoria del celular: la app ni siquiera tiene permiso de internet.
 */
public class NotifService extends NotificationListenerService {

    static final Set<String> MSG_PKGS = new HashSet<>();

    static {
        MSG_PKGS.add("com.whatsapp");
        MSG_PKGS.add("com.whatsapp.w4b");
        MSG_PKGS.add("org.telegram.messenger");
        MSG_PKGS.add("com.facebook.orca");
        MSG_PKGS.add("com.instagram.android");
        MSG_PKGS.add("com.discord");
    }

    /** Una conversación (una notificación de chat). */
    static final class Conv {
        String key, pkg, title;
        long time;
        final List<String> lines = new ArrayList<>();
        Notification.Action reply;
    }

    // Todo corre en el hilo principal, no hace falta sincronizar.
    private static final Map<String, Conv> CONVS = new HashMap<>();
    static Runnable onChange;

    private final Handler main = new Handler(Looper.getMainLooper());

    static boolean isEnabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(c.getPackageName());
    }

    static Conv get(String key) {
        return CONVS.get(key);
    }

    /** Conversaciones de esas apps, la más reciente primero. */
    static List<Conv> list(Set<String> pkgs) {
        List<Conv> out = new ArrayList<>();
        for (Conv c : CONVS.values()) {
            if (pkgs == null || pkgs.contains(c.pkg)) out.add(c);
        }
        Collections.sort(out, new Comparator<Conv>() {
            @Override
            public int compare(Conv a, Conv b) {
                return Long.compare(b.time, a.time);
            }
        });
        return out;
    }

    @Override
    public void onListenerConnected() {
        try {
            StatusBarNotification[] all = getActiveNotifications();
            if (all != null) {
                for (StatusBarNotification s : all) handle(s);
            }
        } catch (Exception ignored) {
        }
        changed();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        handle(sbn);
        changed();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && CONVS.remove(sbn.getKey()) != null) changed();
    }

    private void changed() {
        final Runnable r = onChange;
        if (r != null) main.post(r);
    }

    private void handle(StatusBarNotification sbn) {
        if (sbn == null || !MSG_PKGS.contains(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Bundle ex = n.extras;
        if (ex == null) return;
        CharSequence title = ex.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = ex.getCharSequence(Notification.EXTRA_TEXT);

        List<String> lines = new ArrayList<>();
        Parcelable[] msgs = ex.getParcelableArray("android.messages");
        if (msgs != null) {
            for (Parcelable p : msgs) {
                if (!(p instanceof Bundle)) continue;
                Bundle b = (Bundle) p;
                CharSequence t = b.getCharSequence("text");
                if (t == null) continue;
                CharSequence who = b.getCharSequence("sender");
                lines.add(who != null ? who + ": " + t : t.toString());
            }
        }
        if (lines.isEmpty()) {
            CharSequence[] tl = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (tl != null) {
                for (CharSequence t : tl) lines.add(t.toString());
            } else if (text != null) {
                lines.add(text.toString());
            }
        }
        if (lines.isEmpty()) return;

        Notification.Action reply = null;
        if (n.actions != null) {
            for (Notification.Action a : n.actions) {
                if (a != null && a.actionIntent != null
                        && a.getRemoteInputs() != null && a.getRemoteInputs().length > 0) {
                    reply = a;
                    break;
                }
            }
        }

        Conv c = new Conv();
        c.key = sbn.getKey();
        c.pkg = sbn.getPackageName();
        c.title = title != null ? title.toString() : c.pkg;
        c.time = sbn.getPostTime();
        int from = Math.max(0, lines.size() - 8);
        c.lines.addAll(lines.subList(from, lines.size()));
        c.reply = reply;
        CONVS.put(c.key, c);
    }
}
