package de.oromit.flagcarrier;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.Deflater;

class TagManager {
    public static class TagManagerException extends Exception {
        TagManagerException(String message) {
            super(message);
        }
    }

    private static final String MIME_TYPE = "application/vnd.de.oromit.flagcarrier";
    private static final String APP_REC = "de.oromit.flagcarrier";

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
}
