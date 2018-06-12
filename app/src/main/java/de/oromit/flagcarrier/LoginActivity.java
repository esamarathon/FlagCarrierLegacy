package de.oromit.flagcarrier;

import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
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

public class LoginActivity extends AppCompatActivity {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    OkHttpClient httpClient = new OkHttpClient();
    Map<String, String> tagData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setTitle(R.string.login_title);

        Button b = findViewById(R.id.leftButton);
        b.setOnClickListener(v -> onDoLogin("left"));
        b=findViewById(R.id.midButton);
        b.setOnClickListener(v -> onDoLogin("mid"));
        b=findViewById(R.id.rightButton);
        b.setOnClickListener(v -> onDoLogin("right"));

        parseIntent();
    }

    private void parseIntent() {
        Intent intent = getIntent();
        if(NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if(rawMsgs == null || rawMsgs.length != 1) {
                Toast.makeText(this, "Can't handle this tag", Toast.LENGTH_LONG).show();
                backToMain();
                return;
            }

            NdefMessage msg = (NdefMessage)rawMsgs[0];

            try {
                tagData = TagManager.parseMessage(msg);
                updateTextView();
            } catch(TagManager.TagManagerException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                backToMain();
            }
        } else {
            Toast.makeText(this, "Give ma a tag!", Toast.LENGTH_LONG).show();
            backToMain();
        }
    }

    private void updateTextView() {
        StringBuilder bldr = new StringBuilder();
        StringBuilder other = new StringBuilder();

        for (Map.Entry<String, String> entry : tagData.entrySet()) {
            switch(entry.getKey()) {
                case "display_name":
                    bldr.append("Display Name: ");
                    bldr.append(entry.getValue());
                    bldr.append("\n");
                    break;
                case "country_code":
                    bldr.append("Country Code: ");
                    bldr.append(entry.getValue());
                    bldr.append("\n");
                    break;
                case "speedruncom_name":
                    bldr.append("speedrun.com Name: ");
                    bldr.append(entry.getValue());
                    bldr.append("\n");
                    break;
                case "twitch_name":
                    bldr.append("Twitch Name: ");
                    bldr.append(entry.getValue());
                    bldr.append("\n");
                    break;
                case "twitter_handle":
                    bldr.append("Twitter Handle: ");
                    bldr.append(entry.getValue());
                    bldr.append("\n");
                    break;
                default:
                    other.append(entry.getKey());
                    other.append("=");
                    other.append(entry.getValue());
                    other.append("\n");
            }
        }

        if(other.length() > 0) {
            bldr.append("\nExtra Data:\n");
            bldr.append(other.toString());
        }

        TextView v = findViewById(R.id.loginTextView);
        v.setText(bldr.toString());
    }

    private void onDoLogin(String pos) {
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
            JSONObject tag_data = new JSONObject();
            json.put("device_id", device_id);
            json.put("group_id", group_id);
            json.put("position", pos);
            json.put("action", "login");

            for (Map.Entry<String, String> entry : tagData.entrySet()) {
                tag_data.put(entry.getKey(), entry.getValue());
            }

            json.put("tag_data", tag_data);

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
                        runOnUiThread(()->
                            Toast.makeText(LoginActivity.this, "Request failed", Toast.LENGTH_LONG).show()
                        );
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        ResponseBody body = response.body();
                        if(body == null)
                            return;

                        String res = body.string().trim();
                        int code = response.code();

                        if(code != 200) {
                            runOnUiThread(()->
                                Toast.makeText(LoginActivity.this, "Error " + code + ": " + res, Toast.LENGTH_LONG).show()
                            );
                            return;
                        }

                        runOnUiThread(()->{
                            Toast.makeText(LoginActivity.this, res, Toast.LENGTH_LONG).show();
                            backToMain();
                        });
                    }
                });
    }

    private void backToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
