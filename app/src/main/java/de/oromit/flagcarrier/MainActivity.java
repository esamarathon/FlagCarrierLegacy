package de.oromit.flagcarrier;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    OkHttpClient httpClient = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button clearButton = findViewById(R.id.clearButton);
        clearButton.setOnClickListener(v->onClear());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        int adb = Settings.Secure.getInt(this.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);
        if(adb == 0)
            return super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.settings_item:
                showSettings();
                return true;
            case R.id.write_tag_item:
                showWriteTag();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void showWriteTag() {
        Intent intent = new Intent(this, WriteTagActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void onClear() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setMessage(R.string.clear_confirmation);
        b.setTitle(R.string.clear_conf_title);
        b.setPositiveButton(android.R.string.yes, (d,i)->onDoClear(i));
        b.setNegativeButton(android.R.string.no, (d,i)->onDoClear(i));
        b.show();
    }

    private void onDoClear(int i) {
        if(i != DialogInterface.BUTTON_POSITIVE)
            return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String url = prefs.getString("target_url", null);
        String device_id = prefs.getString("device_id", "");
        String group_id = prefs.getString("group_id", "");

        if(url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "No URL set!", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return;
        }

        String jsonText;

        try {
            JSONObject json = new JSONObject();
            json.put("device_id", device_id);
            json.put("group_id", group_id);
            json.put("position", "all");
            json.put("action", "clear");

            jsonText = json.toString();
        } catch (JSONException e) {
            Toast.makeText(this, "JSONException", Toast.LENGTH_LONG).show();
            return;
        }

        postJson(url, jsonText);
    }

    void postJson(String url, String json) {
        RequestBody body = RequestBody.create(JSON, json);
        Request req = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        httpClient.newCall(req)
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Request failed", Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        ResponseBody body = response.body();
                        if (body == null)
                            return;

                        final String res;
                        int code = response.code();

                        if (code != 200) {
                            res = "Error " + code + ": " + body.string().trim();
                        } else {
                            res = body.string().trim();
                        }

                        runOnUiThread(() -> Toast.makeText(MainActivity.this, res, Toast.LENGTH_LONG).show());
                    }
                });
    }
}
