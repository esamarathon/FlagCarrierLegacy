package de.oromit.flagcarrier;

import android.content.Context;
import android.content.SharedPreferences;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

class TagManager {
    public static class TagManagerException extends Exception {
        TagManagerException(String message) {
            super(message);
        }
    }

    private static final String MIME_TYPE = "application/vnd.de.oromit.flagcarrier";
    private static final String APP_REC = "de.oromit.flagcarrier";

    private byte[] extraSignData = null;
    private byte[] publicKey = null;
    private byte[] privateKey = null;

    public void setExtraSignData(byte[] data) {
        extraSignData = data;
    }

    public boolean hasExtraSignData() {
        return extraSignData != null && extraSignData.length != 0;
    }

    public void setPublicKey(byte[] key) {
        publicKey = key;
    }

    public boolean hasPublicKey() {
        return publicKey != null && publicKey.length != 0;
    }

    public void setPrivateKey(byte[] data) {
        privateKey = data;
    }

    public boolean hasPrivateKey() {
        return privateKey != null && privateKey.length != 0;
    }

    public void loadKeysFromPrefs(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        String pk = prefs.getString("pub_key", null);
        if (pk != null && pk.length() != 0)
            setPublicKey(Base64.decode(pk, Base64.DEFAULT));

        String sk = prefs.getString("priv_key", null);
        if (sk != null && sk.length() != 0)
            setPrivateKey(Base64.decode(sk, Base64.DEFAULT));
    }

    public void setExtraSignDataFromTag(Tag tag) {
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

        setExtraSignData(nuid);
    }

    public void writeToTag(Tag tag, NdefMessage msg) throws TagManagerException {
        if(!isSupported(tag))
            throw new TagManagerException("Tag is not supported");

        if(msg == null || msg.getRecords().length == 0)
            throw new TagManagerException("No data to write");

        if(!writeNdef(tag, msg))
            formatNdef(tag, msg);
    }

    public NdefMessage generateMessage(Map<String, String> inputData) throws TagManagerException {
        byte[] rawData = generateRawData(inputData);
        byte[] data = compressRawData(rawData);

        return new NdefMessage(new NdefRecord[] {
                NdefRecord.createMime(MIME_TYPE, data),
                NdefRecord.createApplicationRecord(APP_REC)
        });
    }

    public Map<String, String> parseMessage(NdefMessage msg) throws TagManagerException {
        if(msg == null)
            throw new TagManagerException("No message to parse");

        NdefRecord[] recs = msg.getRecords();

        for(NdefRecord rec: recs) {
            if(rec.getTnf() != NdefRecord.TNF_MIME_MEDIA)
                continue;

            String type = new String(rec.getType(), StandardCharsets.US_ASCII);

            if(type.equals("application/vnd.de.oromit.flagcarrier"))
                return parsePayload(rec.getPayload());
        }

        throw new TagManagerException("No supported record in Ndef message");
    }

    public static boolean isSupported(Tag tag) {
        for(String s: tag.getTechList()) {
            if(s.equals("android.nfc.tech.NdefFormatable"))
                return true;
            else if(s.equals("android.nfc.tech.Ndef"))
                return true;
        }
        return false;
    }

    private byte[] generateRawData(Map<String, String> kvMap) throws TagManagerException {
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
            throw new TagManagerException("Data generation failed");
        }

        byte[] rawData = baos.toByteArray();

        if (!hasPrivateKey())
            return rawData;

        byte[] signData = rawData;
        if (hasExtraSignData())
        {
            signData = new byte[rawData.length + extraSignData.length];
            System.arraycopy(rawData, 0, signData, 0, rawData.length);
            System.arraycopy(extraSignData, 0, signData, rawData.length, extraSignData.length);
        }

        byte[] sig;

        try {
            sig = CryptoManager.signDetached(signData, privateKey);
        } catch(CryptoManager.CryptoManagerException e) {
            throw new TagManagerException("Crypto signing error: " + e.getMessage());
        }

        String sigStr = Base64.encodeToString(sig, Base64.NO_WRAP);

        baos.reset();
        dos = new DataOutputStream(baos);

        try {
            dos.writeUTF("sig");
            dos.writeUTF(sigStr);
            dos.write(rawData);

            dos.flush();
        } catch (IOException e) {
            throw new TagManagerException("Sig writeout failed");
        }

        return baos.toByteArray();
    }

    private static byte[] compressRawData(byte[] rawData) {
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

    private boolean writeNdef(Tag tag, NdefMessage msg) throws TagManagerException {
        Ndef ndef = Ndef.get(tag);
        if(ndef == null)
            return false;

        boolean closeFailed = false;

        try {
            ndef.connect();

            if(!ndef.isWritable())
                throw new TagManagerException("Tag is not writable");

            int size = msg.toByteArray().length;
            int maxSize = ndef.getMaxSize();
            if(maxSize < size)
                throw new TagManagerException("Tag is too small: " + size + "/" + maxSize);

            ndef.writeNdefMessage(msg);
        } catch(TagLostException e) {
            throw new TagManagerException("Lost tag connection");
        } catch(FormatException e) {
            throw new TagManagerException("Malformed Ndef message");
        } catch(IOException e) {
            throw new TagManagerException("Tag IO failed: " + e.getMessage());
        } catch(UnsupportedOperationException e) {
            throw new TagManagerException("Write eperation not supported: " + e.getMessage());
        } finally {
            try {
                ndef.close();
            } catch (Exception e) {
                closeFailed = true;
            }
        }

        if(closeFailed)
            throw new TagManagerException("Ndef connection failed to close");

        return true;
    }

    private static void formatNdef(Tag tag, NdefMessage msg) throws TagManagerException {
        NdefFormatable ndef = NdefFormatable.get(tag);
        if(ndef == null)
            throw new TagManagerException("Tag not Ndef formatable");

        boolean closeFailed = false;

        try {
            ndef.connect();
            ndef.format(msg);
        } catch(TagLostException e) {
            throw new TagManagerException("Lost tag connection");
        } catch(FormatException e) {
            throw new TagManagerException("Malformed Ndef message");
        } catch(IOException e) {
            throw new TagManagerException("Tag IO failed: " + e.getMessage());
        } catch(UnsupportedOperationException e) {
            throw new TagManagerException("Write eperation not supported: " + e.getMessage());
        } finally {
            try {
                ndef.close();
            } catch (Exception e) {
                closeFailed = true;
            }
        }

        if(closeFailed)
            throw new TagManagerException("NdefFormatable connection failed to close");
    }

    private Map<String,String> parsePayload(byte[] payload) throws TagManagerException {
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

            return parseData(baos.toByteArray());
        } catch (DataFormatException e) {
            throw new TagManagerException("Malformed deflate data");
        }
    }

    private Map<String,String> parseData(byte[] data) throws TagManagerException {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            DataInputStream dis = new DataInputStream(bais);
            Map<String, String> tagDataMap = new HashMap<>();

            while (dis.available() > 0) {
                int initPos = data.length - dis.available();
                String key = dis.readUTF();
                String value = dis.readUTF();

                if (key.equals("sig_valid"))
                    continue;

                if (initPos == 0 && key.equals("sig") && hasPublicKey()) {
                    byte[] msg = new byte[dis.available() + (hasExtraSignData() ? extraSignData.length : 0)];

                    System.arraycopy(data, data.length - dis.available(), msg, 0, dis.available());
                    if (hasExtraSignData())
                        System.arraycopy(extraSignData, 0, msg, dis.available(), extraSignData.length);

                    byte[] sig = Base64.decode(value, Base64.DEFAULT);
                    boolean sigValid = CryptoManager.verifyDetached(sig, msg, publicKey);

                    tagDataMap.put("sig_valid", Boolean.toString(sigValid));
                }

                tagDataMap.put(key, value);
            }

            return tagDataMap;
        } catch(UTFDataFormatException e) {
            throw new TagManagerException("Malformed data on tag");
        } catch(EOFException e) {
            throw new TagManagerException("Incomplete data on tag");
        } catch(IOException e) {
            throw new TagManagerException("Parser IO error: " + e.getMessage());
        } catch(CryptoManager.CryptoManagerException e) {
            throw new TagManagerException("Crypto error: " + e.getMessage());
        }
    }

}
