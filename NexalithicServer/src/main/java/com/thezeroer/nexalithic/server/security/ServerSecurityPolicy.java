package com.thezeroer.nexalithic.server.security;

import com.thezeroer.nexalithic.core.security.SecurityPolicy;

import java.nio.ByteBuffer;

/**
 * 服务端安全策略
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/17
 */
public interface ServerSecurityPolicy extends SecurityPolicy {
    int getAllCertificateLength();
    void CertificatesToBuffer(ByteBuffer buffer);
    ByteBuffer signatureOfLeafCertificate(ByteBuffer buffer) throws Exception;
}
