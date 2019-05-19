package de.oromit.flagcarrier;

import android.nfc.NdefMessage;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class HCEService extends HostApduService {
    private static final String TAG = "HCE";

    private static final byte[] STATUS_SUCCESS = new byte[] { (byte)0x90, (byte)0x00 };
    private static final byte[] STATUS_FAILED = new byte[] { (byte)0x6F, (byte)0x00 };
    private static final byte[] CLA_NOT_SUPPORTED  = new byte[] { (byte)0x6E, (byte)0x00 };
    private static final byte[] INS_NOT_SUPPORTED  = new byte[] { (byte)0x6D, (byte)0x00 };
    private static final byte[] FILE_NOT_FOUND  = new byte[] { (byte)0x6A, (byte)0x82 };
    private static final byte[] WRONG_PARAMETERS  = new byte[] { (byte)0x6B, (byte)0x00 };

    private static final byte DEFAULT_CLA  = (byte)0x00;

    private static final byte SELECT_INS = (byte)0xA4;
    private static final byte UPDATEBINARY_INS = (byte)0xD6;
    private static final byte READBINARY_INS = (byte)0xB0;

    private static final byte[] FLAGCARRIER_AID
            = new byte[] { (byte)0xf0, (byte)0x5a, (byte)0x25, (byte)0x58,
                           (byte)0x83, (byte)0x6e, (byte)0x09, (byte)0x66,
                           (byte)0xae, (byte)0xd5, (byte)0x27, (byte)0xce };

    private static Map<String, String> dataToPublish = null;
    private static Map<String, String> dataToPublishOnce = null;

    public static void publishData(Map<String, String> data)
    {
        dataToPublish = data;
    }

    public static void publishOneTimeData(Map<String, String> data)
    {
        dataToPublishOnce = data;
    }

    private byte[] ndefData = null;
    private int highestReadEnd = 0;

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        if (commandApdu == null || commandApdu.length < 5)
            return STATUS_FAILED;

        Log.d(TAG, "Handling APDU " + String.format("%02x", commandApdu[1]));

        if (commandApdu[0] != DEFAULT_CLA)
            return CLA_NOT_SUPPORTED;

        switch (commandApdu[1])
        {
            case SELECT_INS:
                return processSelect(commandApdu);
            case UPDATEBINARY_INS:
                return processUpdate(commandApdu);
            case READBINARY_INS:
                return processRead(commandApdu);
            default:
                return INS_NOT_SUPPORTED;
        }
    }

    private byte[] processSelect(byte[] commandApdu) {
        if (commandApdu[2] != 0x04 || commandApdu[3] != 0x00)
            return STATUS_FAILED;

        if (commandApdu[4] != FLAGCARRIER_AID.length)
            return FILE_NOT_FOUND;

        for (int i = 0; i < FLAGCARRIER_AID.length; ++i)
            if (commandApdu[5 + i] != FLAGCARRIER_AID[i])
                return FILE_NOT_FOUND;

        return STATUS_SUCCESS;
    }

    private byte[] processUpdate(byte[] commandApdu) {
        int address = ((commandApdu[2] & 0xFF) << 8) | (commandApdu[3] & 0xFF);
        if (address < 0 || address >= 1024)
            return WRONG_PARAMETERS;

        if (address != 0)
            return WRONG_PARAMETERS;

        int length = commandApdu[4] & 0xFF;
        if (commandApdu.length < length + 5)
            return STATUS_FAILED;

        Map<String, String> kvData = dataToPublish;
        if (dataToPublishOnce != null)
            kvData = dataToPublishOnce;

        if (kvData == null)
            return msgResponse(FILE_NOT_FOUND, "no data");

        byte[] challenge = Arrays.copyOfRange(commandApdu, 5, 5 + length);

        try {
            TagManager.setExtraSignData(challenge);
            NdefMessage msg = TagManager.generateMessage(kvData);
            TagManager.setExtraSignData(null);

            ndefData = msg.toByteArray();
        } catch(TagManager.TagManagerException e) {
            Log.e(TAG, e.getMessage());
            return STATUS_FAILED;
        }

        highestReadEnd = 0;

        Log.d(TAG, "Generated data of " + ndefData.length + " bytes");

        return STATUS_SUCCESS;
    }

    private byte[] processRead(byte[] commandApdu) {
        int offset = ((commandApdu[2] & 0xFF) << 8) | (commandApdu[3] & 0xFF);
        if (offset < 0 || offset >= 0x8000)
            return WRONG_PARAMETERS;

        int length = commandApdu[4] & 0xFF;
        if (length <= 0 || length > 253)
            length = 253;

        Log.d(TAG, "Got read request at " + offset + " for " + length + " bytes.");

        if (ndefData == null)
            return FILE_NOT_FOUND;

        if (offset >= ndefData.length)
            return STATUS_SUCCESS;

        int end = offset + length;
        if (end > ndefData.length)
            end = ndefData.length;

        if (end > highestReadEnd)
            highestReadEnd = end;

        byte[] res = Arrays.copyOfRange(ndefData, offset, end);
        return dataResponse(STATUS_SUCCESS, res);
    }

    @Override
    public void onDeactivated(int reason) {
        Log.d(TAG, "Deactivate: " + reason);

        if (ndefData != null && highestReadEnd >= ndefData.length) {
            Log.d(TAG, "Clearing one-time data.");
            dataToPublishOnce = null;
        }

        ndefData = null;
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] msgResponse(byte[] status, String msg)
    {
        return dataResponse(status, msg.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] dataResponse(byte[] status, byte[] data)
    {
        byte[] res = new byte[data.length + status.length];
        System.arraycopy(data, 0, res, 0, data.length);
        System.arraycopy(status, 0, res, data.length, status.length);
        return res;
    }
}
