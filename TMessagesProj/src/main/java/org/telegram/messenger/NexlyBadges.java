package org.telegram.messenger;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class NexlyBadges {

    private static final String BASE_URL = "https://firestore.googleapis.com/v1/projects/nexlygram/databases/(default)/documents/badges?pageSize=300";
    private static final ConcurrentHashMap<Long, String> cache = new ConcurrentHashMap<>();
    private static final AtomicBoolean loading = new AtomicBoolean(false);
    private static volatile boolean loaded = false;
    private static volatile long lastAttempt = 0;
    private static volatile long lastSuccess = 0;
    // Как часто перепроверять значки в Firestore (10 минут)
    private static final long REFRESH_INTERVAL = 10 * 60_000L;
    // Через сколько повторить, если загрузка не удалась
    private static final long RETRY_INTERVAL = 30_000L;

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
            case "official":  return "Официальный канал";
            case "tester":    return "Тестер";
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
            case "official":  return "Официальный канал NEXLYGRAM.";
            case "tester":    return "Тестирует новые версии NEXLYGRAM до релиза.";
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
            case "official":  return 0xFF00ACC1; // бирюзовый
            case "tester":    return 0xFFD81B60; // малиновый
            default:          return 0xFF757575; // серый
        }
    }

    /** Окно с описанием значка (по нажатию на кружок). */
    public static void showInfo(Context context, String type) {
        if (context == null || type == null) return;
        try {
            new AlertDialog.Builder(context)
                    .setTitle("official".equals(type) ? getTitle(type) : getTitle(type) + " NEXLYGRAM")
                    .setMessage(getDescription(type))
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // ===================== Отрисовка значка =====================
    // Розетка (как у галочки верификации) с градиентом цвета роли,
    // белый самолётик Telegram внутри и мерцающие звёздочки.
    // Всё рисуется в координатах 0..100, потом масштабируется под размер.

    private static Paint fillPaint;
    private static Paint planePaint;
    private static Paint sparklePaint;
    private static Path rosettePath;
    private static Path planePath;
    private static final Path sparklePath = new Path();
    private static final HashMap<String, LinearGradient> gradients = new HashMap<>();

    // x, y, размер, сдвиг фазы
    private static final float[][] SPARKLES = {
            {24, 28, 7.0f, 0.00f},
            {77, 73, 5.5f, 0.35f},
            {74, 25, 4.5f, 0.70f},
            {27, 74, 4.0f, 0.15f},
    };
    private static final long SPARKLE_PERIOD = 2000;
    // Прозрачность фона значка: 255 = непрозрачный, 0 = невидимый
    private static final int BADGE_ALPHA = 170;

    public static int getLightColor(String type) {
        if (type == null) return 0xFFBDBDBD;
        switch (type) {
            case "creator":   return 0xFFFFE082;
            case "admin":     return 0xFFFF8A80;
            case "moderator": return 0xFF69F0AE;
            case "media":     return 0xFFE1BEE7;
            case "donator":   return 0xFF90CAF9;
            case "official":  return 0xFF80DEEA;
            case "tester":    return 0xFFF48FB1;
            default:          return 0xFFBDBDBD;
        }
    }

    private static void initDrawing() {
        if (fillPaint != null) return;
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        planePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        planePaint.setColor(Color.WHITE);
        sparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sparklePaint.setColor(Color.WHITE);

        // Розетка: круг с 12 мягкими волнами по краю
        rosettePath = new Path();
        int steps = 120;
        for (int i = 0; i <= steps; i++) {
            double th = 2 * Math.PI * i / steps;
            double rr = 50 * (0.92 + 0.08 * Math.cos(12 * th));
            float x = (float) (50 + rr * Math.cos(th));
            float y = (float) (50 + rr * Math.sin(th));
            if (i == 0) rosettePath.moveTo(x, y); else rosettePath.lineTo(x, y);
        }
        rosettePath.close();

        // Самолётик Telegram, центр примерно (50, 50)
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
        maybeRefresh();
        initDrawing();

        LinearGradient gradient = gradients.get(type);
        if (gradient == null) {
            gradient = new LinearGradient(0, 0, 100, 100, getLightColor(type), getColor(type), Shader.TileMode.CLAMP);
            gradients.put(type, gradient);
        }

        canvas.save();
        canvas.translate(cx - r, cy - r);
        canvas.scale(r / 50f, r / 50f);

        // Розетка с градиентом
        fillPaint.setShader(gradient);
        fillPaint.setAlpha(BADGE_ALPHA);
        canvas.drawPath(rosettePath, fillPaint);

        // Белый самолётик
        canvas.save();
        canvas.translate(48.5f, 51.5f);
        canvas.scale(0.58f, 0.58f);
        canvas.translate(-50, -50);
        canvas.drawPath(planePath, planePaint);
        canvas.restore();

        // Мерцающие звёздочки
        float t = (System.currentTimeMillis() % SPARKLE_PERIOD) / (float) SPARKLE_PERIOD;
        for (float[] sp : SPARKLES) {
            float p = (t + sp[3]) % 1f;
            float pulse = (float) Math.sin(Math.PI * p);
            float sx = sp[0];
            float sy = sp[1];
            float sz = sp[2] * (0.35f + 0.65f * pulse);

            sparklePath.reset();
            sparklePath.moveTo(sx, sy - sz);
            sparklePath.quadTo(sx, sy, sx + sz, sy);
            sparklePath.quadTo(sx, sy, sx, sy + sz);
            sparklePath.quadTo(sx, sy, sx - sz, sy);
            sparklePath.quadTo(sx, sy, sx, sy - sz);
            sparklePath.close();

            sparklePaint.setAlpha((int) (255 * (0.25f + 0.75f * pulse)));
            canvas.drawPath(sparklePath, sparklePaint);
        }

        canvas.restore();
    }

    // ===================== Загрузка из Firestore =====================

    /**
     * userId > 0: пользователь. userId < 0: чат или канал (передаётся -chat.id).
     * Для каналов в Firestore ID можно писать в обоих форматах:
     * "-1234567890" или "-1001234567890" (как показывают боты).
     */
    public static String getBadge(long userId) {
        maybeRefresh();
        String badge = cache.get(userId);
        if (badge == null && userId < 0) {
            badge = cache.get(-1000000000000L + userId);
        }
        return badge;
    }

    /** Дешёвая проверка: не пора ли перезагрузить значки. */
    public static void maybeRefresh() {
        if (loading.get()) return;
        long now = System.currentTimeMillis();
        if (loaded ? now - lastSuccess > REFRESH_INTERVAL : now - lastAttempt > RETRY_INTERVAL) {
            refresh();
        }
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

                boolean changed = !cache.equals(newCache);
                if (changed) {
                    cache.clear();
                    cache.putAll(newCache);
                }
                loaded = true;
                lastSuccess = System.currentTimeMillis();
                FileLog.d("NexlyBadges: loaded " + newCache.size() + " badges, changed=" + changed);
                if (!changed) {
                    return;
                }

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
