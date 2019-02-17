package de.oromit.flagcarrier;

import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.NfcA;
import android.util.Base64;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class LoginActivity extends AppCompatActivity implements Callback {
    private HttpManager httpManager;
    private Map<String, String> tagData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setTitle(R.string.login_title);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        httpManager = new HttpManager(this, this);

        populateButtons();
        parseIntent();
    }

    private void populateButtons() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        LinearLayout layout = findViewById(R.id.buttonLayout);

        layout.removeAllViews();

        String[] pos_avail = prefs.getString("pos_avail", "left,mid,right").split(",");

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        for(String pos: pos_avail) {
            Button button = new Button(this);
            button.setText(pos);
            button.setLayoutParams(params);

            button.setOnClickListener((v)->onDoLogin(pos));

            layout.addView(button);
        }
    }

    private void parseIntent() {
        Intent intent = getIntent();
        if(NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (rawMsgs == null || rawMsgs.length != 1) {
                Toast.makeText(this, "Can't handle this tag", Toast.LENGTH_LONG).show();
                backToMain();
                return;
            }

            NdefMessage msg = (NdefMessage) rawMsgs[0];

            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

            byte[] uid = tag.getId();
            List<String> techs = Arrays.asList(tag.getTechList());

            byte[] nuid = new byte[uid.length + 1];
            System.arraycopy(uid, 0, nuid, 0, uid.length);

            if (techs.contains(MifareUltralight.class.getCanonicalName())) {
                nuid[nuid.length - 1] = (byte)0xAA;
            } else if (techs.contains(MifareClassic.class.getCanonicalName())) {
                nuid[nuid.length - 1] = (byte)0xBB;
            } else {
                nuid = uid;
            }

            TagManager.setExtraSignData(nuid);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String pk = prefs.getString("pub_key", null);
            if (pk != null && pk.length() != 0)
                TagManager.setPublicKey(Base64.decode(pk, Base64.DEFAULT));

            try {
                tagData = TagManager.parseMessage(msg);

                if (TagManager.hasPublicKey() && tagData.containsKey("sig_valid")) {
                    boolean sigValid = Boolean.parseBoolean(tagData.get("sig_valid"));
                    if (!sigValid) {
                        Toast.makeText(this, "Invalid signature!", Toast.LENGTH_LONG).show();
                        backToMain();
                        return;
                    }
                }

                updateTextView();
                checkForSettings();
            } catch (TagManager.TagManagerException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                backToMain();
            } finally {
                TagManager.setExtraSignData(null);
                TagManager.setPublicKey(null);
            }
        } else if("de.oromit.flagcarrier.ManualLoginActivity.Login".equals(intent.getAction())) {
            @SuppressWarnings("unchecked")
            HashMap<String, String> intentData = (HashMap)intent.getSerializableExtra("MANUAL_TAG_LOGIN_DATA");

            tagData = intentData;
            updateTextView();
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
            } else if (!entry.getKey().equals("sig") && !entry.getKey().equals("sig_valid")) {
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
        HashMap<String, String> extraData = new HashMap<>();
        extraData.put("position", pos);

        try {
            httpManager.doRequest("login", tagData, extraData);
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
                    Toast.makeText(this, "Error " + code + ": " + res, Toast.LENGTH_LONG).show()
            );
        } else {
            runOnUiThread(()->{
                Toast.makeText(this, res, Toast.LENGTH_LONG).show();
                backToMain();
            });
        }
    }

    private void checkForSettings() {
        final String display_name = "display_name";
        final String trigger_dsp_name = "set";
        final String trigger_name = "set";

        if(!tagData.containsKey(display_name))
            return;

        if(!tagData.get(display_name).equals(trigger_dsp_name))
            return;

        if(!tagData.containsKey(trigger_name))
            return;

        String set = tagData.get(trigger_name);
        String[] settings = set.split(",");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = prefs.edit();

        StringBuilder resTxt = new StringBuilder();
        resTxt.append("Applied settings:\n");

        for(String setting: settings) {
            if(!tagData.containsKey(setting)) {
                Toast.makeText(this, "Malformed settings: " + setting + " missing on tag", Toast.LENGTH_LONG).show();
                backToMain();
                return;
            }

            if(!prefs.contains(setting)) {
                Toast.makeText(this, "Malformed settings: " + setting + " does not exist", Toast.LENGTH_LONG).show();
                backToMain();
                return;
            }

            String val = tagData.get(setting);
            edit.putString(setting, val);
            resTxt.append(setting).append("=").append(val).append("\n");
        }

        edit.apply();

        Toast.makeText(this, resTxt.toString().trim(), Toast.LENGTH_LONG).show();
        backToMain();
    }

    private void backToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
