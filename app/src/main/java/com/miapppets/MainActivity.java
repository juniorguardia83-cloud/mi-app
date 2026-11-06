package com.miapppets;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

// Primera pantalla funcional: mascota, cuidados, misión diaria y progreso local.\npublic class MainActivity extends Activity {

    private static final String PREFS = "mi_app_save";
    private static final String[] MISSIONS = {
            "Dale una comida a Coco",
            "Juega un ratito con Coco",
            "Dale un baño para que esté limpio",
            "Acaricia a Coco y hazlo feliz",
            "Cuida todas sus necesidades"
    };

    private SharedPreferences prefs;
    private int coins;
    private int streak;
    private int food;
    private int fun;
    private int clean;
    private int missionIndex;
    private boolean missionDone;
    private String lastMissionDate;
    private String today;

    private ScrollView scrollView;
    private LinearLayout content;
    private LinearLayout streakPill;
    private TextView coinsText;
    private TextView streakText;
    private TextView messageText;
    private TextView missionButton;
    private ProgressBar foodBar;
    private ProgressBar funBar;
    private ProgressBar cleanBar;
    private LinearLayout missionCard;

    private final int ink = Color.rgb(57, 74, 67);
    private final int muted = Color.rgb(117, 137, 127);
    private final int mint = Color.rgb(225, 247, 235);
    private final int pink = Color.rgb(255, 231, 239);
    private final int cream = Color.rgb(255, 249, 241);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(250, 253, 250));
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR |
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadProgress();
        buildHome();
    }

    private void loadProgress() {
        coins = prefs.getInt("coins", 35);
        streak = prefs.getInt("streak", 0);
        food = prefs.getInt("food", 76);
        fun = prefs.getInt("fun", 82);
        clean = prefs.getInt("clean", 90);
        lastMissionDate = prefs.getString("lastMissionDate", "");

        today = dateKey(Calendar.getInstance());
        missionDone = today.equals(lastMissionDate);
        missionIndex = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % MISSIONS.length;
    }

    private String dateKey(Calendar calendar) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    private void buildHome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(250, 253, 250));

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        addHeader();
        addPetCard();
        addCareSection();
        addNeedsCard();
        addMissionCard();

        root.addView(buildBottomNavigation(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(66)
        ));
        setContentView(root);
        updateUi();
    }

    private void addHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView appName = makeText("Mi App 🐾", 25, ink, true);
        TextView subtitle = makeText("Un hogar lleno de cariño", 13, muted, false);
        titles.addView(appName);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView coinBadge = makeText("🪙 " + coins, 15, ink, true);
        coinBadge.setGravity(Gravity.CENTER);
        coinBadge.setPadding(dp(13), dp(10), dp(13), dp(10));
        coinBadge.setBackground(rounded(Color.WHITE, Color.rgb(238, 229, 191), 24));
        header.addView(coinBadge);
        coinsText = coinBadge;

        content.addView(header, marginParams(-1, -2, 0, 0, 0, 16));

        LinearLayout streakCard = new LinearLayout(this);
        streakCard.setOrientation(LinearLayout.HORIZONTAL);
        streakCard.setGravity(Gravity.CENTER_VERTICAL);
        streakCard.setPadding(dp(14), dp(11), dp(14), dp(11));
        streakCard.setBackground(rounded(Color.rgb(235, 248, 240), Color.rgb(221, 239, 227), 18));
        TextView flame = makeText("🔥", 21, ink, true);
        streakCard.addView(flame);
        streakText = makeText("  Racha: " + streak + " días  ·  ¡Sigue cuidando!", 14, ink, true);
        streakCard.addView(streakText);
        streakPill = streakCard;
        content.addView(streakCard, marginParams(-1, -2, 0, 0, 0, 16));
    }

    private void addPetCard() {
        LinearLayout petCard = card(Color.rgb(229, 247, 236), Color.rgb(218, 238, 225), 24, 16);
        TextView heading = makeText("¡Coco está en casa!", 19, ink, true);
        petCard.addView(heading);
        TextView hint = makeText("Toca a tu mascota para saludarla", 13, muted, false);
        petCard.addView(hint, marginParams(-1, -2, 0, 3, 0, 0));

        messageText = makeText("¡Hola! ¿Me das un poquito de cariño?", 14, ink, true);
        messageText.setGravity(Gravity.CENTER);
        messageText.setPadding(dp(12), dp(9), dp(12), dp(9));
        messageText.setBackground(rounded(Color.WHITE, Color.rgb(239, 247, 240), 18));
        petCard.addView(messageText, marginParams(-1, -2, 0, 12, 0, 4));

        PetView petView = new PetView();
        petView.setContentDescription("Dibujo de Coco, un perrito virtual");
        petView.setOnClickListener(v -> {
            messageText.setText("¡Guau, guau! Me encanta que me acaricies 💗");
            v.animate().scaleX(1.07f).scaleY(1.07f).setDuration(130)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(130).start())
                    .start();
        });
        petCard.addView(petView, new LinearLayout.LayoutParams(-1, dp(190)));

        TextView name = makeText("Coco  ·  Nivel 1", 15, ink, true);
        name.setGravity(Gravity.CENTER);
        petCard.addView(name, marginParams(-1, -2, 0, 1, 0, 0));

        content.addView(petCard, marginParams(-1, -2, 0, 0, 0, 18));
    }

    private void addCareSection() {
        TextView title = makeText("¿Qué hacemos hoy?", 18, ink, true);
        content.addView(title, marginParams(-1, -2, 0, 0, 0, 10));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.addView(actionButton("🍎", "Comer", Color.rgb(255, 239, 211), v -> care(0)),
                new LinearLayout.LayoutParams(0, dp(76), 1f));
        actions.addView(actionButton("🎾", "Jugar", Color.rgb(231, 239, 255), v -> care(1)),
                new LinearLayout.LayoutParams(0, dp(76), 1f));
        actions.addView(actionButton("🛁", "Bañar", Color.rgb(229, 247, 247), v -> care(2)),
                new LinearLayout.LayoutParams(0, dp(76), 1f));
        content.addView(actions, marginParams(-1, -2, 0, 0, 0, 15));
    }

    private void addNeedsCard() {
        LinearLayout needs = card(Color.WHITE, Color.rgb(238, 243, 239), 20, 15);
        TextView title = makeText("Cómo se siente Coco", 16, ink, true);
        needs.addView(title, marginParams(-1, -2, 0, 0, 0, 11));
        foodBar = addProgressRow(needs, "🍎 Comida", food, Color.rgb(242, 177, 91));
        funBar = addProgressRow(needs, "🎾 Diversión", fun, Color.rgb(133, 176, 235));
        cleanBar = addProgressRow(needs, "🫧 Limpieza", clean, Color.rgb(119, 198, 169));
        content.addView(needs, marginParams(-1, -2, 0, 0, 0, 18));
    }

    private ProgressBar addProgressRow(LinearLayout parent, String label, int value, int tint) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = makeText(label, 13, muted, false);
        line.addView(name, new LinearLayout.LayoutParams(dp(104), -2));

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(value);
        bar.setProgressTintList(ColorStateList.valueOf(tint));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(239, 243, 240)));
        line.addView(bar, new LinearLayout.LayoutParams(0, dp(8), 1f));

        TextView percent = makeText(value + "%", 12, ink, true);
        percent.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        line.addView(percent, new LinearLayout.LayoutParams(dp(42), -2));
        parent.addView(line, marginParams(-1, dp(22), 0, 3, 0, 3));
        return bar;
    }

    private void addMissionCard() {
        missionCard = card(Color.rgb(255, 239, 245), Color.rgb(250, 222, 233), 22, 16);
        TextView tag = makeText("✨ MISIÓN DEL DÍA", 12, Color.rgb(179, 91, 126), true);
        missionCard.addView(tag);
        TextView mission = makeText(MISSIONS[missionIndex], 17, ink, true);
        missionCard.addView(mission, marginParams(-1, -2, 0, 7, 0, 5));
        TextView reward = makeText("Recompensa: +10 monedas y progreso de racha", 12, muted, false);
        missionCard.addView(reward);

        missionButton = makeText(missionDone ? "¡Misión completada! ✓" : "Completar misión  ·  +10 🪙", 14, Color.WHITE, true);
        missionButton.setGravity(Gravity.CENTER);
        missionButton.setPadding(dp(12), dp(13), dp(12), dp(13));
        missionButton.setBackground(rounded(Color.rgb(229, 139, 171), Color.rgb(229, 139, 171), 16));
        missionButton.setOnClickListener(v -> completeMission());
        missionCard.addView(missionButton, marginParams(-1, -2, 0, 13, 0, 0));

        content.addView(missionCard, marginParams(-1, -2, 0, 0, 0, 14));
    }

    private LinearLayout buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(5), dp(8), dp(5));
        nav.setBackgroundColor(Color.WHITE);

        nav.addView(navItem("🏠", "Inicio", true, v -> scrollView.smoothScrollTo(0, 0)),
                new LinearLayout.LayoutParams(0, -1, 1f));
        nav.addView(navItem("⭐", "Misiones", false, v -> {
            if (missionCard != null) scrollView.smoothScrollTo(0, missionCard.getTop());
        }), new LinearLayout.LayoutParams(0, -1, 1f));
        nav.addView(navItem("🛍️", "Tienda", false, v ->
                Toast.makeText(this, "La tienda será la siguiente pantalla que construiremos.", Toast.LENGTH_SHORT).show()),
                new LinearLayout.LayoutParams(0, -1, 1f));
        nav.addView(navItem("🏡", "Casa", false, v ->
                Toast.makeText(this, "La casa y la zona de juegos vienen en el siguiente paso.", Toast.LENGTH_SHORT).show()),
                new LinearLayout.LayoutParams(0, -1, 1f));
        return nav;
    }

    private TextView navItem(String icon, String label, boolean selected, View.OnClickListener listener) {
        TextView item = makeText(icon + "\n" + label, 12,
                selected ? Color.rgb(83, 158, 119) : muted, selected);
        item.setGravity(Gravity.CENTER);
        item.setOnClickListener(listener);
        return item;
    }

    private TextView actionButton(String emoji, String label, int color, View.OnClickListener listener) {
        TextView button = makeText(emoji + "\n" + label, 14, ink, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(color, color, 18));
        button.setOnClickListener(listener);
        return button;
    }

    private void care(int action) {
        if (action == 0) {
            food = Math.min(100, food + 24);
            messageText.setText("¡Qué rico! Gracias por darme de comer 🍎");
        } else if (action == 1) {
            fun = Math.min(100, fun + 22);
            messageText.setText("¡Yupi! Me encanta jugar contigo 🎾");
        } else {
            clean = Math.min(100, clean + 25);
            messageText.setText("¡Ahora estoy limpito y feliz! 🫧");
        }
        saveProgress();
        updateUi();
    }

    private void completeMission() {
        if (missionDone) {
            Toast.makeText(this, "¡Ya hiciste la misión de hoy! Vuelve mañana 💚", Toast.LENGTH_SHORT).show();
            return;
        }

        if (missionIndex == 0) {
            food = Math.min(100, food + 20);
            messageText.setText("¡Gracias por mi comida! 🍎");
        } else if (missionIndex == 1) {
            fun = Math.min(100, fun + 20);
            messageText.setText("¡Qué divertido jugar contigo! 🎾");
        } else if (missionIndex == 2) {
            clean = Math.min(100, clean + 20);
            messageText.setText("¡Baño terminado! Estoy precioso 🫧");
        } else if (missionIndex == 3) {
            fun = Math.min(100, fun + 10);
            messageText.setText("¡Tus caricias son las mejores! 💗");
        } else {
            food = Math.min(100, food + 10);
            fun = Math.min(100, fun + 10);
            clean = Math.min(100, clean + 10);
            messageText.setText("¡Me siento genial! Gracias por cuidarme 🐾");
        }

        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        String yesterdayKey = dateKey(yesterday);
        streak = yesterdayKey.equals(lastMissionDate) ? streak + 1 : 1;
        coins += 10;
        lastMissionDate = today;
        missionDone = true;
        saveProgress();
        updateUi();
        Toast.makeText(this, "¡Ganaste 10 monedas! 🎉", Toast.LENGTH_LONG).show();
    }

    private void saveProgress() {
        prefs.edit()
                .putInt("coins", coins)
                .putInt("streak", streak)
                .putInt("food", food)
                .putInt("fun", fun)
                .putInt("clean", clean)
                .putString("lastMissionDate", lastMissionDate)
                .apply();
    }

    private void updateUi() {
        if (coinsText != null) coinsText.setText("🪙 " + coins);
        if (streakText != null) streakText.setText("  Racha: " + streak + (streak == 1 ? " día" : " días") + "  ·  ¡Sigue cuidando!");
        if (streakPill != null) {
            int streakColor = streak >= 14 ? Color.rgb(255, 239, 204)
                    : streak >= 7 ? Color.rgb(243, 231, 255)
                    : streak >= 3 ? Color.rgb(255, 231, 239)
                    : Color.rgb(235, 248, 240);
            streakPill.setBackground(rounded(streakColor, streakColor, 18));
        }
        if (foodBar != null) foodBar.setProgress(food);
        if (funBar != null) funBar.setProgress(fun);
        if (cleanBar != null) cleanBar.setProgress(clean);
        if (missionButton != null) {
            missionButton.setText(missionDone ? "¡Misión completada! ✓" : "Completar misión  ·  +10 🪙");
            missionButton.setAlpha(missionDone ? 0.72f : 1f);
        }
    }

    private LinearLayout card(int color, int stroke, int radius, int padding) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        layout.setBackground(rounded(color, stroke, radius));
        return layout;
    }

    private GradientDrawable rounded(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != color) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private TextView makeText(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private LinearLayout.LayoutParams marginParams(int width, int height,
                                                    int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class PetView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PetView() {
            super(MainActivity.this);
        }

        private void fill(Canvas canvas, int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float sx = getWidth() / 300f;
            float sy = getHeight() / 250f;
            canvas.save();
            canvas.scale(sx, sy);

            // Sombra suave y cola.
            fill(canvas, Color.rgb(202, 224, 209));
            canvas.drawOval(new RectF(83, 218, 218, 240), paint);

            paint.setColor(Color.rgb(190, 128, 78));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(15);
            paint.setStrokeCap(Paint.Cap.ROUND);
            Path tail = new Path();
            tail.moveTo(205, 171);
            tail.cubicTo(246, 167, 252, 133, 232, 125);
            canvas.drawPath(tail, paint);
            paint.setStyle(Paint.Style.FILL);

            // Cuerpo y patitas.
            fill(canvas, Color.rgb(205, 145, 91));
            canvas.drawOval(new RectF(82, 142, 220, 224), paint);
            canvas.drawRoundRect(new RectF(99, 188, 126, 228), 13, 13, paint);
            canvas.drawRoundRect(new RectF(177, 188, 204, 228), 13, 13, paint);

            // Orejas.
            fill(canvas, Color.rgb(174, 112, 69));
            canvas.drawOval(new RectF(62, 55, 116, 139), paint);
            canvas.drawOval(new RectF(184, 55, 238, 139), paint);
            fill(canvas, Color.rgb(231, 166, 151));
            canvas.drawOval(new RectF(76, 72, 104, 126), paint);
            canvas.drawOval(new RectF(196, 72, 224, 126), paint);

            // Cabeza.
            fill(canvas, Color.rgb(218, 161, 106));
            canvas.drawCircle(150, 116, 76, paint);

            // Hocico y mejillas.
            fill(canvas, Color.rgb(255, 231, 207));
            canvas.drawOval(new RectF(119, 132, 181, 177), paint);
            fill(canvas, Color.rgb(247, 177, 181));
            canvas.drawCircle(108, 143, 10, paint);
            canvas.drawCircle(192, 143, 10, paint);

            // Ojos.
            fill(canvas, Color.rgb(57, 49, 45));
            canvas.drawCircle(126, 111, 6, paint);
            canvas.drawCircle(174, 111, 6, paint);
            fill(canvas, Color.WHITE);
            canvas.drawCircle(128, 109, 2, paint);
            canvas.drawCircle(176, 109, 2, paint);

            // Nariz y sonrisa.
            fill(canvas, Color.rgb(73, 55, 48));
            canvas.drawOval(new RectF(142, 143, 158, 154), paint);
            paint.setColor(Color.rgb(92, 65, 54));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setStrokeCap(Paint.Cap.ROUND);
            Path smile = new Path();
            smile.moveTo(150, 153);
            smile.quadTo(143, 165, 135, 158);
            canvas.drawPath(smile, paint);
            smile.reset();
            smile.moveTo(150, 153);
            smile.quadTo(157, 165, 165, 158);
            canvas.drawPath(smile, paint);
            paint.setStyle(Paint.Style.FILL);

            // Collar rosado y placa.
            fill(canvas, Color.rgb(239, 139, 171));
            canvas.drawRoundRect(new RectF(111, 181, 189, 194), 6, 6, paint);
            fill(canvas, Color.rgb(255, 218, 129));
            canvas.drawCircle(150, 195, 7, paint);

            // Brillitos.
            fill(canvas, Color.WHITE);
            canvas.drawCircle(77, 35, 4, paint);
            canvas.drawCircle(226, 45, 3, paint);
            canvas.drawCircle(236, 188, 4, paint);

            canvas.restore();
        }
    }
}
