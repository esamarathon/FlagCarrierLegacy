package de.oromit.flagcarrier;

import org.bouncycastle.math.ec.rfc8032.Ed25519;
import java.security.SecureRandom;

public class CryptoManager {
    static class CryptoManagerException extends Exception {
        CryptoManagerException(String msg) {
            super(msg);
        }
    }

    public static class KeyPair {
        public byte[] PublicKey;
        public byte[] PrivateKey;
    }

    private static final SecureRandom random = new SecureRandom();

    public static KeyPair genKeyPair() {
        KeyPair res = new KeyPair();
        res.PrivateKey = new byte[Ed25519.SECRET_KEY_SIZE + Ed25519.PUBLIC_KEY_SIZE];
        res.PublicKey = new byte[Ed25519.PUBLIC_KEY_SIZE];

        byte[] sk = new byte[Ed25519.SECRET_KEY_SIZE];
        Ed25519.generatePrivateKey(random, sk);
        Ed25519.generatePublicKey(sk, 0, res.PublicKey, 0);

        System.arraycopy(sk, 0, res.PrivateKey, 0, Ed25519.SECRET_KEY_SIZE);
        System.arraycopy(res.PublicKey, 0, res.PrivateKey, Ed25519.SECRET_KEY_SIZE, Ed25519.PUBLIC_KEY_SIZE);

        return res;
    }

    public static byte[] signDetached(byte[] msg, byte[] privateKey) throws CryptoManagerException {
        if (privateKey.length != Ed25519.SECRET_KEY_SIZE && privateKey.length != Ed25519.SECRET_KEY_SIZE + Ed25519.PUBLIC_KEY_SIZE)
            throw new CryptoManagerException("Invalid private key size for signing.");

        byte[] res = new byte[Ed25519.SIGNATURE_SIZE];
        Ed25519.sign(privateKey, 0, msg, 0, msg.length, res, 0);
        return res;
    }

    public static boolean verifyDetached(byte[] sig, byte[] msg, byte[] publicKey) throws CryptoManagerException {
        if (sig.length != Ed25519.SIGNATURE_SIZE)
            throw new CryptoManagerException("Signature size is invalid for verification.");
        if (publicKey.length != Ed25519.PUBLIC_KEY_SIZE)
            throw new CryptoManagerException("Public key size is invalid for verification.");

        return Ed25519.verify(sig, 0, publicKey, 0, msg, 0, msg.length);
    }
}
