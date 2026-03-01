package com.thezeroer.nexalithic.client.security;

/**
 * 默认客户端安全策略
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/26
 */
public abstract class DefaultClientSecurityPolicy implements ClientSecurityPolicy{
    public static final int SIGNATURE_LENGTH = 64;
    public static final String SIGNATURE_ALGORITHM = "Ed25519";

    public int signatureLength() {
        return SIGNATURE_LENGTH;
    }
}
