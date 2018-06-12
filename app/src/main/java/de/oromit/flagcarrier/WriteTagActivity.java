package de.oromit.flagcarrier;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.hbb20.CountryCodePicker;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class WriteTagActivity extends AppCompatActivity {
    private NfcAdapter mAdapter;
    private PendingIntent mPendingIntent;
    private IntentFilter[] mWriteTagFilters;
    private NdefMessage mWriteMsg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_write_tag);
        setTitle(R.string.write_tag_title);

        Button writeButton = findViewById(R.id.writeTagButton);
        writeButton.setOnClickListener(this::onDoWriteTag);

        mAdapter = NfcAdapter.getDefaultAdapter(this);

        mPendingIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, getClass())
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                0);
        mWriteTagFilters = new IntentFilter[] {
                new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
                new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        };
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.write_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.fillSetOption:
                fillWithSettings();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void fillWithSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        setFieldText(R.id.displayNameText, "set");
        setFieldText(R.id.speedrunNameText, "");
        setFieldText(R.id.twitchNameText, "");
        setFieldText(R.id.twitterHandleText, "");

        StringBuilder bldr = new StringBuilder();

        Set<String> keys = prefs.getAll().keySet();
        keys.remove("device_id");

        bldr.append("set=");
        bldr.append(TextUtils.join(",", keys));

        for(Map.Entry<String, ?> entry: prefs.getAll().entrySet()) {
            if(entry.getKey().equals("device_id"))
                continue;

            bldr.append("\n");
            bldr.append(entry.getKey());
            bldr.append("=");
            bldr.append(entry.getValue().toString());
        }

        setFieldText(R.id.extraDataText, bldr.toString());
    }

    private String getFieldText(int id) {
        EditText e = findViewById(id);
        return e.getText().toString();
    }

    private void setFieldText(int id, String text) {
        EditText e = findViewById(id);
        e.setText(text);
    }

    private String getCountryCode() {
        CountryCodePicker ccp = findViewById(R.id.countryCodePicker);
        return ccp.getSelectedCountryNameCode();
    }

    private Map<String, String> getValidatedDataMap() {
        Map<String, String> kvMap = new HashMap<>();

        String dsplname = getFieldText(R.id.displayNameText).trim();

        if(dsplname.isEmpty()) {
            Toast.makeText(this, "A display name is required", Toast.LENGTH_SHORT).show();
            mWriteMsg = null;
            return null;
        }

        kvMap.put("display_name", dsplname);
        kvMap.put("country_code", getCountryCode());
        kvMap.put("speedruncom_name", getFieldText(R.id.speedrunNameText).trim());
        kvMap.put("twitch_name", getFieldText(R.id.twitchNameText).trim());
        kvMap.put("twitter_handle", getFieldText(R.id.twitterHandleText).trim());

        String extra = getFieldText(R.id.extraDataText);
        for(String line: extra.split("\n")) {
            line = line.trim();
            if(line.isEmpty())
                continue;
            String[] parts = line.split("=", 2);
            if(parts.length != 2) {
                Toast.makeText(this, "Invalid extra data", Toast.LENGTH_SHORT).show();
                return null;
            }
            if(parts[0].length() > 32) {
                Toast.makeText(this, "Extra data key length > 32", Toast.LENGTH_SHORT).show();
                return null;
            }
            if(parts[1].length() > 255) {
                Toast.makeText(this, "Extra data value length > 255", Toast.LENGTH_SHORT).show();
                return null;
            }
            kvMap.put(parts[0], parts[1]);
        }

        return kvMap;
    }

    public void onDoWriteTag(View v) {
        mWriteMsg = null;

        Map<String, String> kvMap = getValidatedDataMap();
        if(kvMap == null)
            return;

        try {
            mWriteMsg = TagManager.generateMessage(kvMap);
            Toast.makeText(this, "Scan tag now!", Toast.LENGTH_SHORT).show();
        } catch(TagManager.TagManagerException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(mAdapter == null) {
            Toast.makeText(this, "No NFC adapter found", Toast.LENGTH_LONG).show();
            return;
        }

        if(!mAdapter.isEnabled()) {
            Toast.makeText(this, "NFC Adapter is disabled", Toast.LENGTH_LONG).show();
            return;
        }

        mAdapter.enableForegroundDispatch(this, mPendingIntent, mWriteTagFilters, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mAdapter != null)
            mAdapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String a = intent.getAction();
        if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(a)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(a)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(a)) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            try {
                TagManager.writeToTag(tag, mWriteMsg);
                mWriteMsg = null;
                Toast.makeText(this, "Success", Toast.LENGTH_LONG).show();
            } catch(TagManager.TagManagerException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }
}
