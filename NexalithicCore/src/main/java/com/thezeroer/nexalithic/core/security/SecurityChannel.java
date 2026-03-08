package com.thezeroer.nexalithic.core.security;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.session.channel.NexalithicChannel;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

/**
 * 安全会话
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/12
 */
public abstract class SecurityChannel {
    public static final int CHANNEL_TOKEN_LENGTH = SecretKeyUtils.ECDH_LENGTH;
    public static final int MAX_PAYLOAD_SIZE = 1024 * 16;
    public static final int FRAME_HEAD_LENGTH = Short.BYTES;
    private final SecretKeyContext secretKeyContext;

    public SecurityChannel(SecretKeyContext secretKeyContext) {
        this.secretKeyContext = secretKeyContext;
    }

    /** 加密 */
    protected final void encrypt(LoopBuffer srcBuffer, LoopBuffer dstBuffer) throws InvalidAlgorithmParameterException, ShortBufferException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        while (srcBuffer.readableBytes() > 0) {
            int payloadLength = Math.min(srcBuffer.readableBytes(), MAX_PAYLOAD_SIZE);
            int cipherLength = payloadLength + SecretKeyContext.TAG_LENGTH;
            if (dstBuffer.writableBytes() < cipherLength) {
                break;
            }
            dstBuffer.put((short) payloadLength);
            ByteBuffer[] srcs = srcBuffer.readableViews();
            ByteBuffer[] dsts = dstBuffer.writableViews();
            if (srcs[0].remaining() >= payloadLength) {
                srcs[0].limit(srcs[0].position() + payloadLength);
                if (dsts[0].remaining() >= payloadLength) {
                    dsts[0].limit(dsts[0].position() + cipherLength);
                    secretKeyContext.encrypt(srcs[0], dsts[0]);
                    dstBuffer.advanceTail(cipherLength);
                } else {
                    dstBuffer.unsafePut(secretKeyContext.encrypt(srcs[0]), cipherLength);
                }
                srcBuffer.advanceHead(payloadLength);
            } else {
                byte[] payload = new byte[payloadLength];
                srcBuffer.unsafeGetBytes(payload, payloadLength);
                dstBuffer.unsafePut(secretKeyContext.encrypt(payload), cipherLength);
            }
        }
    }

    /** 解密 */
    protected final void decrypt(LoopBuffer srcBuffer, LoopBuffer dstBuffer) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, ShortBufferException, BadPaddingException, InvalidKeyException {
        while (srcBuffer.readableBytes() > FRAME_HEAD_LENGTH) {
            srcBuffer.mark();
            int payloadLength = srcBuffer.getShort();
            int cipherLength = payloadLength + SecretKeyContext.TAG_LENGTH;
            if (srcBuffer.readableBytes() < cipherLength || dstBuffer.writableBytes() < payloadLength) {
                srcBuffer.reset();
                break;
            }
            ByteBuffer[] srcs = srcBuffer.readableViews();
            ByteBuffer[] dsts = dstBuffer.writableViews();
            if (srcs[0].remaining() >= cipherLength) {
                srcs[0].limit(srcs[0].position() + cipherLength);
                if (dsts[0].remaining() >= payloadLength) {
                    dsts[0].limit(dsts[0].position() + payloadLength);
                    secretKeyContext.decrypt(srcs[0], dsts[0]);
                    dstBuffer.advanceTail(payloadLength);
                } else {
                    dstBuffer.unsafePut(secretKeyContext.decrypt(srcs[0]), payloadLength);
                }
                srcBuffer.advanceHead(cipherLength);
            } else {
                byte[] cipher = new byte[cipherLength];
                srcBuffer.unsafeGetBytes(cipher, cipherLength);
                dstBuffer.unsafePut(secretKeyContext.decrypt(cipher), payloadLength);
            }
        }
    }
}
