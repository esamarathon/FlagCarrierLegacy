package de.oromit.flagcarrier;

import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.tech.Ndef;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class LoginActivity extends AppCompatActivity {
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
            parseNdefMsg((NdefMessage)rawMsgs[0]);
        } else {
            Toast.makeText(this, "Give ma a tag!", Toast.LENGTH_LONG).show();
            backToMain();
        }
    }

    private void parseNdefMsg(NdefMessage msg) {
        NdefRecord recs[] = msg.getRecords();

        for(NdefRecord rec: recs) {
            if(rec.getTnf() != NdefRecord.TNF_MIME_MEDIA)
                continue;

            String type = new String(rec.getType(), StandardCharsets.US_ASCII);

            if(type.equals("application/vnd.de.oromit.flagcarrier")) {
                handlePayload(rec.getPayload());
                return;
            }
        }
    }

    private void handlePayload(byte[] payload) {
        try {
            Inflater infl = new Inflater();
            infl.setInput(payload);

            byte[] buf = new byte[256];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            while (!infl.finished()) {
                int n = infl.inflate(buf);
                baos.write(buf, 0, n);
            }

            infl.end();

            handleData(baos.toByteArray());
        } catch (DataFormatException e) {
            Toast.makeText(this, "Invalid deflate data", Toast.LENGTH_LONG).show();
            backToMain();
        }
    }

    private void handleData(byte[] data) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            DataInputStream dis = new DataInputStream(bais);
            Map<String, String> newTagData = new HashMap<>();

            while(dis.available() > 0) {
                String key = dis.readUTF();
                String value = dis.readUTF();
                newTagData.put(key, value);
            }

            if (dis.available() != 0) {
                Toast.makeText(this, "Leftover data", Toast.LENGTH_LONG).show();
                backToMain();
                return;
            }

            tagData = newTagData;

            updateTextView();
        } catch(IOException e) {
            Toast.makeText(this, "Malformed data", Toast.LENGTH_LONG).show();
            backToMain();
        }
    }

    private void updateTextView() {
        StringBuilder bldr = new StringBuilder();
        StringBuilder other = new StringBuilder();

        for (Map.Entry<String, String> entry : tagData.entrySet()) {
            switch(entry.getKey()) {
                case "dsplname":
                    bldr.append("Display Name: ");
                    bldr.append(entry.getValue());
                    bldr.append("\n");
                    break;
                case "cntrcode":
                    bldr.append("Country Code: ");
                    bldr.append(entry.getValue());
                    bldr.append("\n");
                    break;
                case "srcmname":
                    bldr.append("speedrun.com Name: ");
                    bldr.append(entry.getValue());
                    bldr.append("\n");
                    break;
                case "twchname":
                    bldr.append("Twitch Name: ");
                    bldr.append(entry.getValue());
                    bldr.append("\n");
                    break;
                case "twtrhndl":
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
        Toast.makeText(this, "TEST", Toast.LENGTH_LONG).show();
        backToMain();
    }

    private void backToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
