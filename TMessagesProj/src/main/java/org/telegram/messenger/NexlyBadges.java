package org.telegram.messenger;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class NexlyBadges {

    private static final String BASE_URL = "https://firestore.googleapis.com/v1/projects/nexlygram/databases/(default)/documents/badges?pageSize=300";
    private static final ConcurrentHashMap<Long, String> cache = new ConcurrentHashMap<>();
    private static final AtomicBoolean loading = new AtomicBoolean(false);
    private static volatile boolean loaded = false;
    private static volatile long lastAttempt = 0;

    // ===================== Тексты ролей =====================
    // Меняй здесь названия и описания.

    public static String getTitle(String type) {
        if (type == null) return "";
        switch (type) {
            case "creator":   return "Создатель";
            case "admin":     return "Администратор";
            case "moderator": return "Модератор";
            case "media":     return "Медиа";
            case "donator":   return "Донатер";
            default:          return type;
        }
    }

    public static String getDescription(String type) {
        if (type == null) return "";
        switch (type) {
            case "creator":   return "Один из создателей NEXLYGRAM.";
            case "admin":     return "Управляет NEXLYGRAM и помогает развивать проект.";
            case "moderator": return "Следит за порядком в сообществе NEXLYGRAM и помогает пользователям.";
            case "media":     return "Снимает видео и делает контент про NEXLYGRAM.";
            case "donator":   return "Поддержал NEXLYGRAM донатом.";
            default:          return "Участник NEXLYGRAM.";
        }
    }

    public static int getColor(String type) {
        if (type == null) return 0xFF757575;
        switch (type) {
            case "creator":   return 0xFFFFB300; // золотой
            case "admin":     return 0xFFE53935; // красный
            case "moderator": return 0xFF43A047; // зелёный
            case "media":     return 0xFF8E24AA; // фиолетовый
            case "donator":   return 0xFF1E88E5; // синий
            default:          return 0xFF757575; // серый
        }
    }

    /** Окно с описанием значка (по нажатию на кружок). */
    public static void showInfo(Context context, String type) {
        if (context == null || type == null) return;
        try {
            new AlertDialog.Builder(context)
                    .setTitle(getTitle(type) + " NEXLYGRAM")
                    .setMessage(getDescription(type))
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // ===================== Отрисовка значка =====================
    // Белый кружок, рамка цвета роли, чёрный самолётик Telegram
    // и звёздочки цвета роли, которые разлетаются от центра.

    private static Paint fillPaint;
    private static Paint ringPaint;
    private static Paint planePaint;
    private static Paint sparklePaint;
    private static Path planePath;
    private static final Path sparklePath = new Path();
    private static final int SPARKLES = 5;
    private static final long SPARKLE_PERIOD = 1800;

    private static void initDrawing() {
        if (fillPaint != null) return;
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(Color.WHITE);
        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        planePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        planePaint.setColor(Color.BLACK);
        sparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Самолётик в координатах 0..100, центр примерно (50, 50)
        planePath = new Path();
        planePath.moveTo(8, 47);
        planePath.lineTo(86, 17);
        planePath.quadTo(93, 14.5f, 92, 22);
        planePath.lineTo(80, 81);
        planePath.quadTo(78.5f, 88, 72.5f, 84);
        planePath.lineTo(53, 70);
        planePath.lineTo(44, 78.5f);
        planePath.quadTo(41, 81.5f, 40.5f, 77);
        planePath.lineTo(41.5f, 62);
        planePath.lineTo(73, 33);
        planePath.quadTo(75.5f, 30.5f, 72, 31.5f);
        planePath.lineTo(32, 56.5f);
        planePath.lineTo(11, 50.5f);
        planePath.quadTo(4, 48.5f, 8, 47);
        planePath.close();
    }

    /**
     * Рисует значок с центром (cx, cy) и радиусом r.
     * Вызывать только из UI-потока (из onDraw).
     */
    public static void draw(Canvas canvas, float cx, float cy, float r, String type) {
        if (canvas == null || type == null) return;
        initDrawing();
        int color = getColor(type);

        // Кружок и рамка
        canvas.drawCircle(cx, cy, r, fillPaint);
        float ring = Math.max(1f, r * 0.14f);
        ringPaint.setStrokeWidth(ring);
        ringPaint.setColor(color);
        canvas.drawCircle(cx, cy, r - ring / 2f, ringPaint);

        // Самолётик
        float k = r * 0.0129f;
        canvas.save();
        canvas.translate(cx - r * 0.036f, cy + r * 0.036f);
        canvas.scale(k, k);
        canvas.translate(-50, -50);
        canvas.drawPath(planePath, planePaint);
        canvas.restore();

        // Звёздочки
        float t = (System.currentTimeMillis() % SPARKLE_PERIOD) / (float) SPARKLE_PERIOD;
        for (int i = 0; i < SPARKLES; i++) {
            float p = (t + i / (float) SPARKLES) % 1f;
            float fade = (float) Math.sin(Math.PI * p);
            double angle = i * (2 * Math.PI / SPARKLES) + 0.4 + i * 1.3;
            float dist = r * (0.30f + 0.48f * p);
            float sx = cx + (float) (Math.cos(angle) * dist);
            float sy = cy + (float) (Math.sin(angle) * dist);
            float s = r * 0.24f * (0.4f + 0.6f * fade);

            sparklePath.reset();
            sparklePath.moveTo(sx, sy - s);
            sparklePath.quadTo(sx, sy, sx + s, sy);
            sparklePath.quadTo(sx, sy, sx, sy + s);
            sparklePath.quadTo(sx, sy, sx - s, sy);
            sparklePath.quadTo(sx, sy, sx, sy - s);
            sparklePath.close();

            sparklePaint.setColor(color);
            sparklePaint.setAlpha((int) (255 * fade));
            canvas.drawPath(sparklePath, sparklePaint);
        }
    }

    // ===================== Загрузка из Firestore =====================

    public static String getBadge(long userId) {
        if (!loaded && !loading.get() && System.currentTimeMillis() - lastAttempt > 30_000) {
            refresh();
        }
        return cache.get(userId);
    }

    public static void refresh() {
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        lastAttempt = System.currentTimeMillis();
        Utilities.globalQueue.postRunnable(() -> {
            try {
                ConcurrentHashMap<Long, String> newCache = new ConcurrentHashMap<>();
                String pageToken = null;
                do {
                    String url = BASE_URL + (pageToken != null ? "&pageToken=" + URLEncoder.encode(pageToken, "UTF-8") : "");
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    StringBuilder sb = new StringBuilder();
                    try {
                        int code = conn.getResponseCode();
                        if (code != 200) {
                            FileLog.e("NexlyBadges: HTTP " + code);
                            return;
                        }
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                            String line;
                            while ((line = reader.readLine()) != null) sb.append(line);
                        }
                    } finally {
                        conn.disconnect();
                    }
                    JSONObject json = new JSONObject(sb.toString());
                    JSONArray documents = json.optJSONArray("documents");
                    if (documents != null) {
                        for (int i = 0; i < documents.length(); i++) {
                            try {
                                JSONObject doc = documents.getJSONObject(i);
                                String name = doc.getString("name");
                                long userId = Long.parseLong(name.substring(name.lastIndexOf('/') + 1));
                                String type = doc.getJSONObject("fields").getJSONObject("type").getString("stringValue");
                                newCache.put(userId, type);
                            } catch (Exception ignore) {
                                // пропускаем кривой документ
                            }
                        }
                    }
                    pageToken = json.optString("nextPageToken", null);
                    if (pageToken != null && pageToken.isEmpty()) pageToken = null;
                } while (pageToken != null);

                cache.clear();
                cache.putAll(newCache);
                loaded = true;
                FileLog.d("NexlyBadges: loaded " + newCache.size() + " badges");

                // Список чатов слушает NotificationCenter аккаунта, а не глобальный
                AndroidUtilities.runOnUIThread(() -> {
                    for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                        if (UserConfig.getInstance(a).isClientActivated()) {
                            NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.dialogsNeedReload);
                            NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                        }
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                loading.set(false);
            }
        });
    }
}
