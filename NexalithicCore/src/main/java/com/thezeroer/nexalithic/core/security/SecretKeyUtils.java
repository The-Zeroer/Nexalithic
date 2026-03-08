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
    public static final byte[] LABEL_CLIENT_SIGNALING = "LABEL_CLIENT_SIGNALING".getBytes(StandardCharsets.UTF_8);
    public static final byte[] LABEL_SERVER_SIGNALING = "LABEL_SERVER_SIGNALING".getBytes(StandardCharsets.UTF_8);
    public static final byte[] LABEL_CLIENT_BUSINESS = "LABEL_CLIENT_BUSINESS".getBytes(StandardCharsets.UTF_8);
    public static final byte[] LABEL_SERVER_BUSINESS = "LABEL_SERVER_BUSINESS".getBytes(StandardCharsets.UTF_8);
    private static final byte[] LABEL_FINISHED = "FINISHED".getBytes(StandardCharsets.UTF_8);
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final KeyFactory KEY_FACTORY;
    private static final KeyPairGenerator KEY_PAIR_GEN;
    private static final Provider XDH_PROVIDER;
    private static final ThreadLocal<Mac> MAC_HOLDER = ThreadLocal.withInitial(() -> {
        try {
            return Mac.getInstance(HMAC_ALGO);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HmacSHA256 not supported", e);
        }
    });

    static {
        try {
            XDH_PROVIDER = KeyAgreement.getInstance("XDH").getProvider();
            KEY_FACTORY = KeyFactory.getInstance("XDH");
            KEY_PAIR_GEN = KeyPairGenerator.getInstance("XDH");
            KEY_PAIR_GEN.initialize(NamedParameterSpec.X25519);
            MAC_HOLDER.get();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("SecretKeyUtils init error: " + e.getMessage(), e);
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
        return HKDF.extract(null, ka.generateSecret());
    }
    public static byte[] generateFinished(byte[] secret, byte[] handshakeHash) throws NoSuchAlgorithmException, InvalidKeyException {
        return HKDF.extract(HKDF.expand(secret, LABEL_FINISHED, 32), handshakeHash);
    }

    public static SecretKeyContext generateSessionSecretKey(byte[] secret, byte[] selfLabel, byte[] peerLabel) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException {
        byte[] writeMaterial = HKDF.expand(secret, selfLabel, 44);
        byte[] readMaterial = HKDF.expand(secret, peerLabel, 44);
        return new SecretKeyContext(
                new SecretKeySpec(writeMaterial, 0, 32, "AES"),
                Arrays.copyOfRange(writeMaterial, 32, 44),
                new SecretKeySpec(readMaterial, 0, 32, "AES"),
                Arrays.copyOfRange(readMaterial, 32, 44)
        );
    }

    static class HKDF {
        public static byte[] extract(byte[] salt, byte[] ikm) throws InvalidKeyException {
            Mac mac = MAC_HOLDER.get();
            if (salt == null) {
                salt = new byte[mac.getMacLength()];
            }
            mac.init(new SecretKeySpec(salt, HMAC_ALGO));
            return mac.doFinal(ikm);
        }
        public static byte[] expand(byte[] prk, byte[] label, int outLen) throws InvalidKeyException {
            Mac mac = MAC_HOLDER.get();
            mac.init(new SecretKeySpec(prk, HMAC_ALGO));
            byte[] okm = new byte[outLen];
            byte[] t = new byte[0];
            int generated = 0;
            byte counter = 1;
            while (generated < outLen) {
                mac.update(t);
                mac.update(label);
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
