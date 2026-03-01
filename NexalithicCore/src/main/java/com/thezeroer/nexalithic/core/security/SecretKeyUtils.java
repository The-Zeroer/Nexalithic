package com.thezeroer.nexalithic.core.security;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;

/**
 * 密钥工具
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/15
 */
public class SecretKeyUtils {
    public static final int ECDH_LENGTH = 32;
    public static final int FINISHED_LENGTH = 32;
    public static final String LABEL_CLIENT = "CLIENT_DATA";
    public static final String LABEL_SERVER = "SERVER_DATA";
    private static final KeyFactory KEY_FACTORY;
    private static final KeyPairGenerator KEY_PAIR_GEN;
    private static final Provider XDH_PROVIDER;

    static {
        try {
            XDH_PROVIDER = KeyAgreement.getInstance("XDH").getProvider();
            KEY_FACTORY = KeyFactory.getInstance("XDH");
            KEY_PAIR_GEN = KeyPairGenerator.getInstance("XDH");
            KEY_PAIR_GEN.initialize(NamedParameterSpec.X25519);
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException("X25519 not supported", e);
        }
    }

    public static KeyPair generateKeyPair() {
        return KEY_PAIR_GEN.generateKeyPair();
    }
    public static byte[] rawPublickey(PublicKey publicKey) {
        byte[] encoded = publicKey.getEncoded();
        if (encoded.length == 44) {
            byte[] raw = new byte[32];
            System.arraycopy(encoded, 12, raw, 0, 32);
            return raw;
        }
        return encoded;
    }

    public static byte[] compactSecret(PrivateKey privateKey, byte[] publicKey) throws NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException {
        byte[] reversedKey = new byte[ECDH_LENGTH];
        for (int i = 0; i < ECDH_LENGTH;) {
            reversedKey[i] = publicKey[ECDH_LENGTH - ++i];
        }
        KeyAgreement ka = KeyAgreement.getInstance("XDH", XDH_PROVIDER);
        ka.init(privateKey);
        ka.doPhase(KEY_FACTORY.generatePublic(new XECPublicKeySpec(NamedParameterSpec.X25519, new BigInteger(1, reversedKey))), true);
        return HKDF.extract(ka.generateSecret());
    }
    public static SessionSecretKey generateSessionSecretKey(byte[] secret, String selfLabel, String peerLabel) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException {
        byte[] writeMaterial = HKDF.expand(secret, selfLabel, 44);
        byte[] readMaterial = HKDF.expand(secret, peerLabel, 44);
        return new SessionSecretKey(
                new SecretKeySpec(writeMaterial, 0, 32, "AES"),
                Arrays.copyOfRange(writeMaterial, 32, 44),
                new SecretKeySpec(readMaterial, 0, 32, "AES"),
                Arrays.copyOfRange(readMaterial, 32, 44)
        );
    }
    public static byte[] generateFinished(byte[] secret, byte[] handshakeHash) throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] finishedKey = HKDF.expand(secret, "finished", 32);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(finishedKey, "HmacSHA256"));
        return mac.doFinal(handshakeHash);
    }

    static class HKDF {
        private static final String HMAC_ALGO = "HmacSHA256";
        public static byte[] extract(byte[] ikm) throws NoSuchAlgorithmException, InvalidKeyException {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(new byte[mac.getMacLength()], HMAC_ALGO));
            return mac.doFinal(ikm);
        }
        public static byte[] expand(byte[] prk, String info, int outLen) throws NoSuchAlgorithmException, InvalidKeyException {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(prk, HMAC_ALGO));
            byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);
            byte[] okm = new byte[outLen];
            byte[] t = new byte[0];
            int generated = 0;
            byte counter = 1;
            while (generated < outLen) {
                mac.update(t);
                mac.update(infoBytes);
                mac.update(counter);
                t = mac.doFinal();
                int copyLen = Math.min(t.length, outLen - generated);
                System.arraycopy(t, 0, okm, generated, copyLen);
                generated += copyLen;
                counter++;
            }
            return okm;
        }
    }
}
