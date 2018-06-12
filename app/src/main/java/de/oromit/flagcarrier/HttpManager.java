package de.oromit.flagcarrier;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

class HttpManager {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    static class MissingSettingException extends Exception {
        MissingSettingException(String which) {
            super("Missing setting: " + which);
        }
    }

    static class HttpManagerException extends Exception {
        HttpManagerException(String msg) {
            super(msg);
        }
    }

    private OkHttpClient mHttpClient = new OkHttpClient();
    private Callback mCallback;
    private Context mContext;

    public HttpManager(Context context, Callback callback) {
        mContext = context;
        mCallback = callback;
    }

    public void doRequest(String action, Map<String, String> tagData) throws MissingSettingException, HttpManagerException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        String url = prefs.getString("target_url", null);

        if(url == null || url.trim().isEmpty() || url.equals("unset"))
            throw new MissingSettingException("URL");

        JSONObject json = buildJson(action, tagData, prefs);

        postJson(url, json);
    }

    private JSONObject buildJson(String action, Map<String, String> tagData, SharedPreferences prefs) throws HttpManagerException {
        String device_id = prefs.getString("device_id", "");
        String group_id = prefs.getString("group_id", "");

        try {
            JSONObject json = new JSONObject();
            JSONObject tag_data = new JSONObject();
            json.put("action", action);
            json.put("device_id", device_id);
            json.put("group_id", group_id);

            if(tagData != null) {
                for (Map.Entry<String, String> entry : tagData.entrySet()) {
                    tag_data.put(entry.getKey(), entry.getValue());
                }

                json.put("tag_data", tag_data);
            }

            return json;
        } catch(JSONException e) {
            throw new HttpManagerException("JSON error: " + e.getMessage());
        }
    }

    private void postJson(String url, JSONObject json) {
        RequestBody body = RequestBody.create(JSON, json.toString());
        Request req = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        mHttpClient.newCall(req).enqueue(mCallback);
    }
}
