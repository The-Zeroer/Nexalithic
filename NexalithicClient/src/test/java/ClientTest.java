import com.thezeroer.nexalithic.client.NexalithicClient;
import com.thezeroer.nexalithic.client.security.DefaultClientSecurityPolicy;
import com.thezeroer.nexalithic.core.io.thread.LoopThread;
import com.thezeroer.nexalithic.core.messaging.task.NexalithicTask;
import com.thezeroer.nexalithic.core.messaging.task.TaskFuture;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.payload.TextPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class ClientTest {
    public static final Logger logger = LoggerFactory.getLogger(ClientTest.class);

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 1; i++) {
            System.out.println(i);
            NexalithicClient nexalithicClient = NexalithicClient.builder()
                    .apply(LoopThread.Options.GlobalLoopBufferPool_Capacity, 2)
                    .apply(LoopThread.Options.LocalLoopBufferPool_Capacity, 2)
                    .securityPolicy(new DefaultClientSecurityPolicy() {
                        @Override
                        public int getServerCertificatesLength() {
                            return 40;
                        }

                        @Override
                        public void CertificatesFormBuffer(ByteBuffer buffer) {

                        }

                        @Override
                        public boolean verifyOfLeafCertificate(ByteBuffer buffer) {
                            return true;
                        }
                    })
                    .build();
            nexalithicClient.start();
            nexalithicClient.link(new InetSocketAddress("127.0.0.1", 7709));
            for (int ii = 0; ii < 1; ii++) {
                TaskFuture future = nexalithicClient.submit(NexalithicTask.builder()
                        .onRequest(() -> BusinessPacket.create(BusinessPacket.Way.DEFAULT).attach(new TextPayload("Hello Server!")))
                        .onResponse(response -> {
                            if (response.firstPayload() instanceof TextPayload textPayload) {
                                logger.debug(textPayload.value());
                            }
                        })
                        .onTimeout(() -> logger.debug("Timeout!"))
                        .onFinish(() -> logger.debug("Finish!"))
                        .setWaitTime(100)
                );
                future.waitFinish();
            }
        }
    }
}
