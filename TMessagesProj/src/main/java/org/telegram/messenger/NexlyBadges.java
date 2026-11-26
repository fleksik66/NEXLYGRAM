package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import org.telegram.messenger.NexlyBadges;

public class NexlyBadges {

    private static final String URL = "https://firestore.googleapis.com/v1/projects/nexlygram/databases/(default)/documents/badges";
    private static final ConcurrentHashMap<Long, String> cache = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;

    public static String getBadge(long userId) {
        if (!loaded) {
            refresh();
        }
        return cache.get(userId);
    }

    public static void refresh() {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(URL).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                StringBuilder sb = new StringBuilder();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONArray documents = new JSONObject(sb.toString()).optJSONArray("documents");
                if (documents == null) return;
                ConcurrentHashMap<Long, String> newCache = new ConcurrentHashMap<>();
                for (int i = 0; i < documents.length(); i++) {
                    JSONObject doc = documents.getJSONObject(i);
                    String name = doc.getString("name");
                    long userId = Long.parseLong(name.substring(name.lastIndexOf('/') + 1));
                    String type = doc.getJSONObject("fields").getJSONObject("type").getString("stringValue");
                    newCache.put(userId, type);
                }
                cache.clear();
                cache.putAll(newCache);
                loaded = true;
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }
}
