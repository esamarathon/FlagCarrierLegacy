package de.oromit.flagcarrier;

import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class LoginActivity extends AppCompatActivity implements Callback {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    HttpManager httpManager;
    Map<String, String> tagData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setTitle(R.string.login_title);

        httpManager = new HttpManager(this, this);

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

        HashMap<String, String> knownIdx = new HashMap<>();
        knownIdx.put("display_name", "Display Name: ");
        knownIdx.put("country_code", "Country Code: ");
        knownIdx.put("speedruncom_name", "speedrun.com Name: ");
        knownIdx.put("twitch_name", "Twitch Name: ");
        knownIdx.put("twitter_handle", "Twitter Handle: ");

        for (Map.Entry<String, String> entry : tagData.entrySet()) {
            if(knownIdx.containsKey(entry.getKey())) {
                bldr.append(knownIdx.get(entry.getKey()));
                bldr.append(entry.getValue());
                bldr.append("\n");
            } else {
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
        tagData.put("position", pos);

        try {
            httpManager.doRequest("login", tagData);
        } catch(HttpManager.HttpManagerException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        } catch(HttpManager.MissingSettingException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }
    }

    @Override
    public void onFailure(Call call, IOException e) {
        runOnUiThread(()->
                Toast.makeText(LoginActivity.this, "Request failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
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
        } else {
            runOnUiThread(()->{
                Toast.makeText(LoginActivity.this, res, Toast.LENGTH_LONG).show();
                backToMain();
            });
        }
    }

    private void backToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
