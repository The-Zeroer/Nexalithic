package com.thezeroer.nexalithic.core.security;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;

/**
 * 安全会话
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/12
 */
public class SecuritySession {
    private final SessionSecretKey sessionSecretKey;

    public SecuritySession(SessionSecretKey sessionSecretKey) {
        this.sessionSecretKey = sessionSecretKey;
    }

    /** 加密 */
    public void encrypt(LoopBuffer srcBuffer, LoopBuffer dstBuffer) {

    }

    /** 解密 */
    public void decrypt(LoopBuffer srcBuffer, LoopBuffer dstBuffer) {

    }
}
