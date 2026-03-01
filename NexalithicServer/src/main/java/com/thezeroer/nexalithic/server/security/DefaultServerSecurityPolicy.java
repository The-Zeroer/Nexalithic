package com.thezeroer.nexalithic.server.security;

import com.thezeroer.nexalithic.core.security.Certificate;

import java.nio.ByteBuffer;
import java.security.*;

public abstract class DefaultServerSecurityPolicy implements ServerSecurityPolicy {
    public static final int SIGNATURE_LENGTH = 64;
    public static final String SIGNATURE_ALGORITHM = "Ed25519";
    private static final ThreadLocal<Signature> SIGNATURE_CACHE = ThreadLocal.withInitial(() -> {
        try {
            return Signature.getInstance(SIGNATURE_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    });

    protected DefaultServerSecurityPolicy() throws NoSuchAlgorithmException {
        Signature.getInstance(SIGNATURE_ALGORITHM);
    }

    public int signatureLength() {
        return SIGNATURE_LENGTH;
    }

    public int getAllCertificateLength () {
        Certificate issuingCertificate = getIssuingCertificate();
        Certificate leafCertificate = getLeafCertificate();
        return Certificate.BASE_LENGTH * 2 + issuingCertificate.publicKeyLength() + issuingCertificate.signatureLength() +
                leafCertificate.publicKeyLength() + leafCertificate.signatureLength();
    }

    public void CertificatesToBuffer(ByteBuffer buffer) {
        Certificate issuingCertificate = getIssuingCertificate();
        Certificate leafCertificate = getLeafCertificate();
        buffer.putInt(leafCertificate.version()).putLong(leafCertificate.creationTime()).putLong(leafCertificate.expirationTime())
                .put(leafCertificate.publicKey()).put(leafCertificate.signature())
                .putInt(issuingCertificate.version()).putLong(issuingCertificate.creationTime()).putLong(issuingCertificate.expirationTime())
                .put(issuingCertificate.publicKey()).put(issuingCertificate.signature());
    }

    public ByteBuffer signatureOfLeafCertificate(ByteBuffer buffer) throws InvalidKeyException, SignatureException {
        Signature signature = SIGNATURE_CACHE.get();
        signature.initSign(getLeafCertificatePrivateKey());
        signature.update(buffer.duplicate().flip());
        buffer.put(signature.sign());
        return buffer.flip();
    }

    protected abstract Certificate getIssuingCertificate();
    protected abstract Certificate getLeafCertificate();
    protected abstract PrivateKey getLeafCertificatePrivateKey();
}
