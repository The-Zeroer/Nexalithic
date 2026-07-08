import com.thezeroer.nexalithic.core.messaging.handler.HandlerRegistry;
import com.thezeroer.nexalithic.core.messaging.handler.NexalithicHandler;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.business.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.business.payload.FilePayload;
import com.thezeroer.nexalithic.core.model.packet.business.payload.SerializablePayload;
import com.thezeroer.nexalithic.core.model.packet.business.payload.TextPayload;
import com.thezeroer.nexalithic.core.security.DefaultCertificate;
import com.thezeroer.nexalithic.core.security.NexalithicCertificate;
import com.thezeroer.nexalithic.core.util.BinaryStorageUtils;
import com.thezeroer.nexalithic.server.NexalithicServer;
import com.thezeroer.nexalithic.server.lifecycle.LifecycleManager;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceUnit;
import com.thezeroer.nexalithic.server.lifecycle.service.WorkerLoop;
import com.thezeroer.nexalithic.server.security.DefaultServerSecurityPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Objects;

public class ServerTest {
    public static final Logger logger = LoggerFactory.getLogger(ServerTest.class);
    public static void main(String[] args) throws Exception {
        NexalithicServer nexalithicServer = NexalithicServer.builder()
                .apply(LifecycleManager.OPTIONS.ServiceUnit_Count, 2)
                .apply(ServiceUnit.OPTIONS.WorkerLoop_Count, 2)
                .apply(WorkerLoop.OPTIONS.DynamicRateController.MaxBps, 1024 * 1024 * 1024L)
//                .apply(StewardLoop.OPTIONS.HeartBeat_MaxInterval, 1000000L)
//                .apply(WorkerLoop.OPTIONS.MaxIdleTime,  1000000L)
                .addRoute(AbstractPacket.PacketType.BUSINESS, "0.0.0.0/0", 7710)
                .securityPolicy(new TestSecurityPolicy())
                .registerHandler(new HandlerRegistry.PathMatcher(), new NexalithicHandler<>((context -> {
                    if (context.getRequest().firstPayload() instanceof TextPayload textPayload) {
                        logger.debug(String.valueOf(textPayload.getTotalSize()));
                    }
                    try {
                        context.pushResponse(BusinessPacket.create(BusinessPacket.Way.DEFAULT)
                                .attach(new TextPayload("Hello Client!"))
                                .attach(new FilePayload(new File(Objects.requireNonNull(ServerTest.class
                                        .getResource(ServerTest.class.getSimpleName() + ".class")).getFile())))
                                .attach(new SerializablePayload(BusinessPacket.class)));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }), false))
                .build();
        nexalithicServer.start();
        nexalithicServer.open(AbstractPacket.PacketType.SIGNALING, new InetSocketAddress("0.0.0.0", 7709));
        nexalithicServer.open(AbstractPacket.PacketType.BUSINESS, new InetSocketAddress("0.0.0.0", 7710));
    }

    private static class TestSecurityPolicy extends DefaultServerSecurityPolicy {
        private PrivateKey privateKey;

        protected TestSecurityPolicy() throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, URISyntaxException {
            privateKey = KeyFactory.getInstance(SIGNATURE_ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(
                    BinaryStorageUtils.loadBytes(Paths.get(this.getClass().getResource("leafPrivateKey").toURI()).toString())));
        }

        @Override
        protected NexalithicCertificate getIssuingCertificate() {
            try {
                return DefaultCertificate.fromBuffer(ByteBuffer.wrap(BinaryStorageUtils.loadBytes(Paths.get(this.getClass().getResource("issuingCertificate").toURI()).toString())));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected NexalithicCertificate getLeafCertificate() {
            try {
                return DefaultCertificate.fromBuffer(ByteBuffer.wrap(BinaryStorageUtils.loadBytes(Paths.get(this.getClass().getResource("leafCertificate").toURI()).toString())));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected PrivateKey getLeafCertificatePrivateKey() {
            return privateKey;
        }

        @Override
        public int certificatesLength() {
            return 272;
        }
    }
}
