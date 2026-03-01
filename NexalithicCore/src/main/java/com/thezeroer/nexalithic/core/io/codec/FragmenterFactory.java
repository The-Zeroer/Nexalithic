package com.thezeroer.nexalithic.core.io.codec;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.model.packet.TransactionPacket;
import org.jctools.queues.SpscArrayQueue;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 分片器工厂
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/10
 * @version 1.0.0
 */
public class FragmenterFactory {

    @SuppressWarnings("unchecked")
    public static <P extends AbstractPacket<?>> PacketFragmenter<P> create(AbstractPacket.TYPE type) {
        return (PacketFragmenter<P>) switch (type) {
            case SIGNALING -> new SignalingPacketFragmenter();
            case TRANSACTION -> new TransactionPacketFragmenter();
            case STREAMING -> null;
        };
    }

    static class SignalingPacketFragmenter implements PacketFragmenter<SignalingPacket> {
        private final SpscArrayQueue<SignalingPacket> packets = new SpscArrayQueue<>(16);
        private SignalingPacket currentPacket;
        private LoopBuffer.Recyclable recyclable;
        private LoopBuffer loopBuffer;

        @Override
        public boolean dispatch(SignalingPacket signalingPacket) {
            return packets.offer(signalingPacket);
        }

        @Override
        public void bindBuffer(LoopBuffer.Recyclable recyclable) {
            this.recyclable = recyclable;
            loopBuffer = recyclable.unwrap();
        }

        @Override
        public int drain() {
            if (currentPacket == null) {
                currentPacket = packets.poll();
                if (currentPacket == null) {
                    recyclable.recycle();
                    recyclable = null;
                    loopBuffer = null;
                    return -1;
                }
            }
            int contentLength = currentPacket.getContentLength();
            int totalRequired = Byte.BYTES + contentLength;
            if (loopBuffer.writableBytes() < totalRequired) {
                return 0;
            }
            ByteBuffer[] writableViews = loopBuffer.put(currentPacket.getSignal()).writableViews();
            int view0Length = writableViews[0].remaining();
            byte[] content = currentPacket.getContent();
            if (view0Length >= contentLength) {
                writableViews[0].put(content);
            } else {
                writableViews[0].put(content, 0, view0Length);
                writableViews[1].put(content, view0Length, contentLength - view0Length);
            }
            loopBuffer.advanceTail(contentLength);
            currentPacket = null;
            return totalRequired;
        }

        @Override
        public void clear() {
            packets.clear();
            currentPacket = null;
            if (recyclable != null) {
                recyclable.recycle();
                recyclable = null;
                loopBuffer = null;
            }
        }
    }

    static class TransactionPacketFragmenter implements PacketFragmenter<TransactionPacket> {
        private LoopBuffer.Recyclable recyclable;
        private LoopBuffer loopBuffer;

        @Override
        public boolean dispatch(TransactionPacket transactionPacket) {
            return false;
        }

        @Override
        public void bindBuffer(LoopBuffer.Recyclable recyclable) {
            this.recyclable = recyclable;
            loopBuffer = recyclable.unwrap();
        }

        @Override
        public int drain() throws IOException {
            return 0;
        }

        @Override
        public void clear() {
            if (recyclable != null) {
                recyclable.recycle();
                recyclable = null;
                loopBuffer = null;
            }
        }
    }
}
