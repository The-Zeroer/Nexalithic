package com.thezeroer.nexalithic.core.security;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 会话密钥
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/05
 * @version 1.0.0
 */
public class SessionSecretKey {
    public static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    public static final int NONCE_LENGTH = 12;
    public static final int TAG_LENGTH = 16;
    private final SecretKey readKey, writeKey;
    private final byte[] readIV, writeIV;
    private final AtomicInteger readSequence = new AtomicInteger(0);
    private final AtomicInteger writeSequence = new AtomicInteger(0);
    private final Cipher readCipher = Cipher.getInstance(AES_ALGORITHM);
    private final Cipher writeCipher = Cipher.getInstance(AES_ALGORITHM);

    public SessionSecretKey(SecretKey readKey, byte[] readIV, SecretKey writeKey, byte[] writeIV) throws NoSuchPaddingException, NoSuchAlgorithmException {
        this.readKey = readKey;
        this.readIV = readIV;
        this.writeKey = writeKey;
        this.writeIV = writeIV;
    }

    public byte[] nextReadNonce() {
        byte[] nonce = new byte[NONCE_LENGTH];
        System.arraycopy(readIV, 0, nonce, 0, NONCE_LENGTH);
        int seq = readSequence.getAndIncrement();
        nonce[8]  ^= (byte) (seq >>> 24);
        nonce[9]  ^= (byte) (seq >>> 16);
        nonce[10] ^= (byte) (seq >>> 8);
        nonce[11] ^= (byte) seq;
        return nonce;
    }
    public byte[] nextWriteNonce() {
        byte[] nonce = new byte[NONCE_LENGTH];
        System.arraycopy(writeIV, 0, nonce, 0, NONCE_LENGTH);
        int seq = writeSequence.getAndIncrement();
        nonce[8]  ^= (byte) (seq >>> 24);
        nonce[9]  ^= (byte) (seq >>> 16);
        nonce[10] ^= (byte) (seq >>> 8);
        nonce[11] ^= (byte) seq;
        return nonce;
    }

    /**
     * 加密
     */
    public ByteBuffer encrypt(ByteBuffer input, ByteBuffer output) throws InvalidKeyException, InvalidAlgorithmParameterException, ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        writeCipher.init(Cipher.ENCRYPT_MODE, writeKey, new GCMParameterSpec(TAG_LENGTH * Byte.SIZE, nextWriteNonce()));
        writeCipher.doFinal(input, output);
        return output;
    }

    /**
     * 解密
     */
    public ByteBuffer decrypt(ByteBuffer input, ByteBuffer output) throws InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, ShortBufferException {
        readCipher.init(Cipher.DECRYPT_MODE, readKey, new GCMParameterSpec(TAG_LENGTH * Byte.SIZE, nextReadNonce()));
        readCipher.doFinal(input, output);
        return output;
    }

    public ByteBuffer encrypt(byte[] input, ByteBuffer output) throws InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        writeCipher.init(Cipher.ENCRYPT_MODE, writeKey, new GCMParameterSpec(TAG_LENGTH * Byte.SIZE, nextWriteNonce()));
        return output.put(writeCipher.doFinal(input));
    }
    public ByteBuffer decrypt(byte[] input, ByteBuffer output) throws InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        readCipher.init(Cipher.DECRYPT_MODE, readKey, new GCMParameterSpec(TAG_LENGTH * Byte.SIZE, nextReadNonce()));
        return output.put(readCipher.doFinal(input));
    }

    public byte[] encrypt(byte[] input) throws InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        writeCipher.init(Cipher.ENCRYPT_MODE, writeKey, new GCMParameterSpec(TAG_LENGTH * Byte.SIZE, nextWriteNonce()));
        return writeCipher.doFinal(input);
    }
    public byte[] decrypt(byte[] input) throws InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        readCipher.init(Cipher.DECRYPT_MODE, readKey, new GCMParameterSpec(TAG_LENGTH * Byte.SIZE, nextReadNonce()));
        return readCipher.doFinal(input);
    }
}
