package com.thezeroer.nexalithic.core.security;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;

import java.nio.ByteBuffer;

/**
 * 安全会话
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/12
 */
public class SecuritySession {
    public static final int FRAME_LENGTH = 1024 * 64;
    private final SessionSecretKey sessionSecretKey;

    public SecuritySession(SessionSecretKey sessionSecretKey) {
        this.sessionSecretKey = sessionSecretKey;
    }

    /** 加密 */
    public void encrypt(LoopBuffer srcBuffer, LoopBuffer dstBuffer) {
        ByteBuffer[] srcBuffers = srcBuffer.readableViews();
        ByteBuffer[] dstBuffers = dstBuffer.writableViews();
        int length = srcBuffers[0].remaining() + srcBuffers[1].remaining();
        dstBuffers[0].put(srcBuffers[0]);
        dstBuffers[1].put(srcBuffers[1]);
        srcBuffer.advanceHead(length);
        dstBuffer.advanceTail(length);
    }

    /** 解密 */
    public void decrypt(LoopBuffer srcBuffer, LoopBuffer dstBuffer) {
        ByteBuffer[] srcBuffers = srcBuffer.readableViews();
        ByteBuffer[] dstBuffers = dstBuffer.writableViews();
        int length = srcBuffers[0].remaining() + srcBuffers[1].remaining();
        dstBuffers[0].put(srcBuffers[0]);
        dstBuffers[1].put(srcBuffers[1]);
        srcBuffer.advanceHead(length);
        dstBuffer.advanceTail(length);
    }
}
