package com.gamerpanel;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView status;
    private TextView mainButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            w.setStatusBarColor(0xFF0F0D1C);
            w.setNavigationBarColor(0xFF0F0D1C);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFF0F0D1C);
        int pad = Ui.dp(this, 24);
        root.setPadding(pad, Ui.dp(this, 56), pad, pad);

        // Control de consola grande
        Ui.IconView logo = new Ui.IconView(this, Ui.IconView.PAD);
        int lp = Ui.dp(this, 22);
        logo.setPadding(lp, lp, lp, lp);
        logo.setBackground(Ui.oval(0xFF7C5CFF, 0xFF2B1A66));
        root.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 112)));

        TextView title = text("Panel Gamer", 26, 0xFFFFFFFF, true);
        LinearLayout.LayoutParams tlp = wrap();
        tlp.topMargin = Ui.dp(this, 20);
        root.addView(title, tlp);

        TextView sub = text("Una burbuja flotante para abrir WhatsApp, Spotify y más mientras juegas.",
                14, 0xFFB8B4D8, false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = wrap();
        slp.topMargin = Ui.dp(this, 8);
        root.addView(sub, slp);

        status = text("", 13, 0xFF9D8CFF, true);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stlp = wrap();
        stlp.topMargin = Ui.dp(this, 32);
        root.addView(status, stlp);

        mainButton = button("", 0xFF7C5CFF, 0xFF4C2FD6);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 54));
        mlp.topMargin = Ui.dp(this, 14);
        root.addView(mainButton, mlp);

        TextView battery = button("Evitar que el sistema cierre la app", 0x22FFFFFF, 0x22FFFFFF);
        battery.setTextSize(13);
        battery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBatterySettings();
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        blp.topMargin = Ui.dp(this, 12);
        root.addView(battery, blp);

        TextView tip = text("Tip: en Ajustes > Batería, deja \"Panel Gamer\" sin restricciones "
                + "para que la burbuja no se cierre mientras juegas.", 12, 0xFF7E7AA3, false);
        tip.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tiplp = wrap();
        tiplp.topMargin = Ui.dp(this, 18);
        root.addView(tip, tiplp);

        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (!Settings.canDrawOverlays(this)) {
            status.setText("Falta un permiso: mostrar sobre otras apps");
            mainButton.setText("1. Dar permiso");
            mainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                }
            });
        } else if (!OverlayService.running) {
            status.setText("Todo listo");
            mainButton.setText("Iniciar panel");
            mainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startPanel();
                }
            });
        } else {
            status.setText("Panel activo ✓");
            mainButton.setText("Detener panel");
            mainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopService(new Intent(MainActivity.this, OverlayService.class));
                    // El servicio tarda un instante en marcarse como detenido.
                    status.setText("Todo listo");
                    mainButton.setText("Iniciar panel");
                    mainButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v2) {
                            startPanel();
                        }
                    });
                }
            });
        }
    }

    private void startPanel() {
        Intent i = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        Toast.makeText(this, "Listo. Toca la burbuja para abrir el panel", Toast.LENGTH_LONG).show();
        moveTaskToBack(true);
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    // ------------------------------------------------------------------ helpers de UI

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView button(String s, int c1, int c2) {
        TextView b = new TextView(this);
        b.setText(s);
        b.setTextSize(16);
        b.setTextColor(0xFFFFFFFF);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.gradient(c1, c2, Ui.dp(this, 16)));
        return b;
    }
}
