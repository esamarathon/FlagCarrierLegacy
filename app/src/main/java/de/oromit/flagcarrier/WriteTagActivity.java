package de.oromit.flagcarrier;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.hbb20.CountryCodePicker;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Deflater;

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

    private String getFieldText(int id) {
        EditText e = findViewById(id);
        return e.getText().toString();
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

        kvMap.put("dsplname", dsplname);
        kvMap.put("cntrcode", getCountryCode());
        kvMap.put("srcmname", getFieldText(R.id.speedrunNameText).trim());
        kvMap.put("twchname", getFieldText(R.id.twitchNameText).trim());
        kvMap.put("twtrhndl", getFieldText(R.id.twitterHandleText).trim());

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
            if(parts[0].length() > 16) {
                Toast.makeText(this, "Extra data key length > 16", Toast.LENGTH_SHORT).show();
                return null;
            }
            if(parts[1].length() > 254) {
                Toast.makeText(this, "Extra data value length > 254", Toast.LENGTH_SHORT).show();
                return null;
            }
            kvMap.put(parts[0], parts[1]);
        }

        return kvMap;
    }

    private byte[] generateRawData(Map<String, String> kvMap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        try {
            for (Map.Entry<String, String> entry : kvMap.entrySet()) {
                String k = entry.getKey().trim();
                String v = entry.getValue().trim();
                if(v.isEmpty())
                    continue;
                dos.writeUTF(k);
                dos.writeUTF(v);
            }

            dos.flush();
        } catch (IOException e) {
            Toast.makeText(this, "IOException", Toast.LENGTH_SHORT).show();
            return null;
        }

        return baos.toByteArray();
    }

    private byte[] compressRawData(byte[] rawData) {
        Deflater d = new Deflater(9);
        d.setInput(rawData);
        d.finish();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        byte[] buf = new byte[256];
        while(!d.finished()) {
            int n = d.deflate(buf);
            baos.write(buf, 0, n);
        }

        d.end();

        return baos.toByteArray();
    }

    public void onDoWriteTag(View v) {
        mWriteMsg = null;

        Map<String, String> kvMap = getValidatedDataMap();
        if(kvMap == null)
            return;

        byte[] rawData = generateRawData(kvMap);

        byte[] data = compressRawData(rawData);

        mWriteMsg = new NdefMessage(new NdefRecord[] {
                NdefRecord.createMime("application/vnd.de.oromit.flagcarrier", data),
                NdefRecord.createApplicationRecord("de.oromit.flagcarrier")
        });

        Toast.makeText(this, "Scan tag now!", Toast.LENGTH_LONG).show();
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

    private static int getPreferredTech(Tag tag) {
        for(String s: tag.getTechList()) {
            if(s.equals("android.nfc.tech.NdefFormatable"))
                return 0;
            else if(s.equals("android.nfc.tech.Ndef"))
                return 1;
        }
        return -1;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String a = intent.getAction();
        if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(a)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(a)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(a)) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            int tech = getPreferredTech(tag);
            if(tech < 0) {
                Toast.makeText(this, "Unsupported tag", Toast.LENGTH_LONG).show();
                return;
            } else {
                writeTag(tag);
            }
        }
    }

    private void formatWriteTag(Tag tag) {
        if(mWriteMsg == null) {
            Toast.makeText(this, "No data to write!", Toast.LENGTH_LONG).show();
            return;
        }

        NdefFormatable ndef = NdefFormatable.get(tag);
        if(ndef == null) {
            Toast.makeText(this, "Not Ndef formatable", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            ndef.connect();

            try {
                ndef.format(mWriteMsg);
                Toast.makeText(this, "Success", Toast.LENGTH_LONG).show();
                mWriteMsg = null;
            } catch (Exception e) {
                Toast.makeText(this, "Format failed", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Connect failed", Toast.LENGTH_LONG).show();
        } finally {
            try {
                ndef.close();
            } catch (Exception e) {
            }
        }
    }

    private void writeTag(Tag tag) {
        if(mWriteMsg == null) {
            Toast.makeText(this, "No data to write!", Toast.LENGTH_LONG).show();
            return;
        }

        Ndef ndef = Ndef.get(tag);
        if(ndef == null) {
            formatWriteTag(tag);
            return;
        }

        try {
            ndef.connect();

            if(!ndef.isWritable()) {
                Toast.makeText(this, "Tag is not writable", Toast.LENGTH_LONG).show();
                return;
            }

            int size = mWriteMsg.toByteArray().length;
            if(ndef.getMaxSize() < size) {
                Toast.makeText(this, "Tag is too small: " + size + "/" + ndef.getMaxSize(), Toast.LENGTH_LONG).show();
                return;
            }

            ndef.writeNdefMessage(mWriteMsg);
            Toast.makeText(this, "Success", Toast.LENGTH_LONG).show();
            mWriteMsg = null;
        } catch (Exception e) {
            Toast.makeText(this, "Writing ndef failed", Toast.LENGTH_LONG).show();
        } finally {
            try {
                ndef.close();
            } catch (Exception e) {
            }
        }
    }
}
