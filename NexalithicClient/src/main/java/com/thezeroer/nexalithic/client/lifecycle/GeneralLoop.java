package com.thezeroer.nexalithic.client.lifecycle;

import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.core.security.SecretKeyUtils;
import com.thezeroer.nexalithic.core.security.SessionSecretKey;
import com.thezeroer.nexalithic.client.manager.SessionManager;
import com.thezeroer.nexalithic.client.security.ClientSecurityPolicy;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;

/**
 * 通用选择器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/06
 * @version 1.0.0
 */
public class GeneralLoop extends AbstractLoop<GeneralLoop.DispatchWrapper> {
    private static final Logger logger = LoggerFactory.getLogger(GeneralLoop.class);
    private final SessionManager sessionManager;
    private final ClientSecurityPolicy securityPolicy;

    public GeneralLoop(OptionMap options, ClientSecurityPolicy securityPolicy) throws IOException {
        this.securityPolicy = securityPolicy;
        sessionManager = new SessionManager();
    }

    @Override
    public void dispatch(DispatchWrapper dispatchWrapper) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, NoSuchPaddingException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        AbstractPacket.TYPE type = dispatchWrapper.type;
        SocketChannel socketChannel = dispatchWrapper.socketChannel;
        if (type == AbstractPacket.TYPE.SIGNALING) {
            MessageDigest transcriptHash = MessageDigest.getInstance("SHA-256");
            ByteBuffer buffer1 = ByteBuffer.allocate(securityPolicy.getServerCertificatesLength());
            ByteBuffer buffer2 = ByteBuffer.allocate(SecretKeyUtils.ECDH_LENGTH + securityPolicy.signatureLength());
            if (socketChannel.read(new ByteBuffer[]{buffer1, buffer2}) == -1) {
                return;
            }
            transcriptHash.update(buffer1.flip());
            transcriptHash.update(buffer2.flip());
            securityPolicy.CertificatesFormBuffer(buffer1.flip());
            if (!securityPolicy.verifyOfLeafCertificate(buffer2.flip())) {
                return;
            }
            KeyPair keyPair = SecretKeyUtils.generateKeyPair();
            ByteBuffer buffer3 = ByteBuffer.allocate(SecretKeyUtils.ECDH_LENGTH).put(SecretKeyUtils.rawPublickey(keyPair.getPublic()));
            socketChannel.write(buffer3.flip());
            transcriptHash.update(buffer3.flip());
            byte[] secret = SecretKeyUtils.compactSecret(keyPair.getPrivate(), buffer2.array());
            byte[] localFinished = SecretKeyUtils.generateFinished(secret, transcriptHash.digest());
            SessionSecretKey sessionSecretKey = SecretKeyUtils.generateSessionSecretKey(secret, SecretKeyUtils.LABEL_CLIENT, SecretKeyUtils.LABEL_SERVER);
            ByteBuffer buffer4 = ByteBuffer.wrap(sessionSecretKey.encrypt(localFinished));
            ByteBuffer buffer5 = ByteBuffer.allocate(NexalithicSession.SESSION_ID_LENGTH + SessionSecretKey.TAG_LENGTH);
            socketChannel.write(buffer4);
            if (socketChannel.read(new ByteBuffer[]{buffer4.clear(), buffer5}) == -1) {
                return;
            }
            byte[] remoteFinished = sessionSecretKey.decrypt(buffer4.array());
            if (!MessageDigest.isEqual(localFinished, remoteFinished)) {
                return;
            }
            String sessionId = HexFormat.of().formatHex(sessionSecretKey.decrypt(buffer5.array()));
            System.out.println(sessionId);
        } else {

        }
    }

    @Override
    public void onAsyncEvent() {

    }

    @Override
    public void onReadyEvent(SelectionKey selectionKey) throws IOException {

    }

    @Override
    public void onShutdown() {

    }

    public record DispatchWrapper(AbstractPacket.TYPE type, SocketChannel socketChannel) {}
}
