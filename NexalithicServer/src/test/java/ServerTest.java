import com.thezeroer.nexalithic.core.messaging.handler.HandlerRegistry;
import com.thezeroer.nexalithic.core.messaging.handler.NexalithicHandler;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.payload.TextPayload;
import com.thezeroer.nexalithic.core.security.Certificate;
import com.thezeroer.nexalithic.server.NexalithicServer;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceUnit;
import com.thezeroer.nexalithic.server.lifecycle.service.StewardLoop;
import com.thezeroer.nexalithic.server.security.DefaultServerSecurityPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;

public class ServerTest {
    public static final Logger logger = LoggerFactory.getLogger(ServerTest.class);
    public static void main(String[] args) throws Exception {
        NexalithicServer nexalithicServer = NexalithicServer.builder()
                .apply(ServiceUnit.Count, 4)
                .apply(ServiceUnit.WorkerLoop_Count, 8)
//                .apply(StewardLoop.HeartBeat_MaxInterval, 3000L)
//                .apply(StewardLoop.TimeWheel_Tick, 100L)
                .securityPolicy(new DefaultServerSecurityPolicy() {
                    final KeyPair signingKeyPair = KeyPairGenerator.getInstance(SIGNATURE_ALGORITHM).generateKeyPair();
                    final Certificate certificate = new Certificate() {

                        @Override
                        public int version() {
                            return 0;
                        }

                        @Override
                        public long creationTime() {
                            return 0;
                        }

                        @Override
                        public long expirationTime() {
                            return 0;
                        }

                        @Override
                        public int publicKeyLength() {
                            return 0;
                        }

                        @Override
                        public int signatureLength() {
                            return 0;
                        }

                        @Override
                        public byte[] publicKey() {
                            return new byte[0];
                        }

                        @Override
                        public byte[] signature() {
                            return new byte[0];
                        }
                    };

                    @Override
                    protected Certificate getIssuingCertificate() {
                        return certificate;
                    }

                    @Override
                    protected Certificate getLeafCertificate() {
                        return certificate;
                    }

                    @Override
                    protected PrivateKey getLeafCertificatePrivateKey() {
                        return signingKeyPair.getPrivate();
                    }
                })
                .registerHandler(new HandlerRegistry.PathMatcher(), new NexalithicHandler<>((context -> {
                    if (context.getRequest().firstPayload() instanceof TextPayload textPayload) {
                        logger.debug(textPayload.value());
                    }
                    context.pushResponse(BusinessPacket.create(BusinessPacket.Way.DEFAULT).attach(new TextPayload("Hello Client!")));
                }), false))
                .build();
        nexalithicServer.start();
        nexalithicServer.getNetworkRouter().addRoute(AbstractPacket.PacketType.BUSINESS, "0.0.0.0/0", 7710);
        nexalithicServer.open(AbstractPacket.PacketType.SIGNALING, new InetSocketAddress("0.0.0.0", 7709));
        nexalithicServer.open(AbstractPacket.PacketType.BUSINESS, new InetSocketAddress("0.0.0.0", 7710));
//        while (true) {
//            nexalithicServer.push("1", BusinessPacket.build());
//            Thread.sleep(1000);
//        }
    }
}
