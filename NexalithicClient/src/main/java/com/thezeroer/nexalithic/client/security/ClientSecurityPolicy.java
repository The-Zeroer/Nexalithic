package com.thezeroer.nexalithic.client.security;

import com.thezeroer.nexalithic.core.security.SecurityPolicy;

import java.nio.ByteBuffer;

/**
 * 客户端安全策略
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/17
 */
public interface ClientSecurityPolicy extends SecurityPolicy {
    int getServerCertificatesLength();
    void CertificatesFormBuffer(ByteBuffer buffer);
    boolean verifyOfLeafCertificate(ByteBuffer buffer);
}
