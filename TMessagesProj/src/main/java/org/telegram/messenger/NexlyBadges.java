package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class NexlyBadges {

    private static final String BASE_URL = "https://firestore.googleapis.com/v1/projects/nexlygram/databases/(default)/documents/badges?pageSize=300";
    private static final ConcurrentHashMap<Long, String> cache = new ConcurrentHashMap<>();
    private static final AtomicBoolean loading = new AtomicBoolean(false);
    private static volatile boolean loaded = false;
    private static volatile long lastAttempt = 0;

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
                    String url = BASE_URL + (pageToken != null ? "&pageToken=" + pageToken : "");
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
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
                AndroidUtilities.runOnUIThread(() ->
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload));
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                loading.set(false);
            }
        });
    }
}
