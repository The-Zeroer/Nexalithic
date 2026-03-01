package com.thezeroer.nexalithic.core.security;

/**
 * 证书
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/23
 */
public interface Certificate {
    int BASE_LENGTH = Integer.BYTES + Long.BYTES * 2;
    int version();
    long creationTime();
    long expirationTime();
    int publicKeyLength();
    int signatureLength();
    byte[] publicKey();
    byte[] signature();
}
