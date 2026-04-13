import com.thezeroer.nexalithic.client.NexalithicClient;
import com.thezeroer.nexalithic.client.security.DefaultClientSecurityPolicy;
import com.thezeroer.nexalithic.client.security.EmptyClientSecurityPolicy;
import com.thezeroer.nexalithic.core.io.thread.LoopThread;
import com.thezeroer.nexalithic.core.messaging.task.NexalithicTask;
import com.thezeroer.nexalithic.core.messaging.task.TaskFuture;
import com.thezeroer.nexalithic.core.messaging.visual.TransferListener;
import com.thezeroer.nexalithic.core.messaging.visual.TransferListenerGroup;
import com.thezeroer.nexalithic.core.messaging.visual.TransferSnapshot;
import com.thezeroer.nexalithic.core.model.packet.business.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.business.payload.TextPayload;
import com.thezeroer.nexalithic.core.security.DefaultCertificate;
import com.thezeroer.nexalithic.core.util.BinaryStorageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Paths;

public class ClientTest {
    public static final Logger logger = LoggerFactory.getLogger(ClientTest.class);

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 1; i++) {
            NexalithicClient nexalithicClient = NexalithicClient.builder()
                    .apply(LoopThread.OPTIONS.GlobalLoopBufferPool_Capacity, 2)
                    .apply(LoopThread.OPTIONS.LocalLoopBufferPool_Capacity, 2)
                    .securityPolicy(new TestSecurityPolicy())
                    .build();
            nexalithicClient.start();
            nexalithicClient.link(new InetSocketAddress("127.0.0.1", 7709));
            for (int ii = 0; ii < 1; ii++) {
                TaskFuture future = nexalithicClient.submit(
                        NexalithicTask.builder()
                                .onRequest(() -> {
//                                    try {
//                                        return BusinessPacket.create(BusinessPacket.Way.DEFAULT)
//                                                .attach(new TextPayload("Hello Server!"))
//                                                .attach(new FilePayload(new File("D:\\TBRTZ\\OtherData\\ISO\\Win11_24H2_Chinese_Simplified_x64.iso")));
//                                    } catch (FileNotFoundException e) {
//                                        throw new RuntimeException(e);
//                                    }
                                    return BusinessPacket.create(BusinessPacket.Way.DEFAULT, (short) 1, (short) 2).attach(new TextPayload("Hello Server!"));
                                })
                                .onResponse(response -> {
                                    if (response.firstPayload() instanceof TextPayload textPayload) {
                                        logger.debug(textPayload.value());
                                    }
                                })
                                .onTimeout(() -> logger.debug("Timeout!"))
                                .onFinish(() -> logger.debug("Finish!"))
                                .setWaitTime(10),
                        TransferListenerGroup.builder()
                                .onRequest(TransferListener.builder()
                                        .onStarted((snapshot -> {
                                            logger.debug("onRequestStarted[Total: {}]", TransferSnapshot.formatSize(snapshot.getTotal()));
                                        }))
                                        .onUpdated((snapshot) -> {
                                            System.out.print("\r" + snapshot.toString());
                                        }).onFinished(() -> logger.debug("onRequestFinished")))
                                .onResponse(TransferListener.builder()
                                        .onStarted((snapshot -> {
                                            logger.debug("onResponseStarted[Total: {}]", TransferSnapshot.formatSize(snapshot.getTotal()));
                                        }))
                                        .onUpdated((snapshot) -> {
                                            System.out.print("\r" + snapshot.toString());
                                        }).onFinished(() -> logger.debug("onRequestFinished")))
                );
                future.waitFinish();
            }
        }
    }

    public static class TestSecurityPolicy extends DefaultClientSecurityPolicy {

        @Override
        public byte[] rootPublicKey() {
            try {
                return BinaryStorageUtils.loadBytes(Paths.get(this.getClass().getResource("rootPublicKey").toURI()).toString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int certificatesLength() {
            return 272;
        }

        @Override
        protected void loadLocalCertificates() {

        }

        @Override
        protected void saveRemoteCertificates() {

        }
    }
}
