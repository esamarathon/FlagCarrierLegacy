package de.oromit.flagcarrier;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
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
import java.util.HashMap;
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

    private static byte[] extraSignData = null;
    private static byte[] publicKey = null;

    public static void setExtraSignData(byte[] data)
    {
        extraSignData = data;
    }

    public static void setPublicKey(byte[] key) {
        publicKey = key;
    }

    public static void writeToTag(Tag tag, NdefMessage msg) throws TagManagerException {
        if(!isSupported(tag))
            throw new TagManagerException("Tag is not supported");

        if(msg == null || msg.getRecords().length == 0)
            throw new TagManagerException("No data to write");

        if(!writeNdef(tag, msg))
            formatNdef(tag, msg);
    }

    public static NdefMessage generateMessage(Map<String, String> inputData) throws TagManagerException {
        byte[] rawData = generateRawData(inputData);
        byte[] data = compressRawData(rawData);

        return new NdefMessage(new NdefRecord[] {
                NdefRecord.createMime(MIME_TYPE, data),
                NdefRecord.createApplicationRecord(APP_REC)
        });
    }

    public static Map<String, String> parseMessage(NdefMessage msg) throws TagManagerException {
        if(msg == null)
            throw new TagManagerException("No message to parse");

        NdefRecord recs[] = msg.getRecords();

        for(NdefRecord rec: recs) {
            if(rec.getTnf() != NdefRecord.TNF_MIME_MEDIA)
                continue;

            String type = new String(rec.getType(), StandardCharsets.US_ASCII);

            if(type.equals("application/vnd.de.oromit.flagcarrier"))
                return parsePayload(rec.getPayload());
        }

        throw new TagManagerException("No supported record in Ndef message");
    }

    private static boolean isSupported(Tag tag) {
        for(String s: tag.getTechList()) {
            if(s.equals("android.nfc.tech.NdefFormatable"))
                return true;
            else if(s.equals("android.nfc.tech.Ndef"))
                return true;
        }
        return false;
    }

    private static byte[] generateRawData(Map<String, String> kvMap) throws TagManagerException {
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
            e.printStackTrace();
            throw new TagManagerException("Data generation failed");
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

    private static boolean writeNdef(Tag tag, NdefMessage msg) throws TagManagerException {
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

    private static Map<String,String> parsePayload(byte[] payload) throws TagManagerException {
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

    private static Map<String,String> parseData(byte[] data) throws TagManagerException {
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

                if (initPos == 0 && key.equals("sig") && publicKey != null && publicKey.length != 0) {
                    byte[] msg = new byte[dis.available() + (extraSignData != null ? extraSignData.length : 0)];
                    System.arraycopy(data, data.length - dis.available(), msg, 0, dis.available());
                    if (extraSignData != null)
                        System.arraycopy(extraSignData, 0, msg, dis.available(), extraSignData.length);
                    byte[] sig = Base64.decode(value, Base64.DEFAULT);
                    tagDataMap.put("sig_valid", Boolean.toString(CryptoManager.verifyDetached(sig, msg, publicKey)));
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
