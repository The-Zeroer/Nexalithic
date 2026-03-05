package com.thezeroer.nexalithic.core.io.codec;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import org.jctools.queues.MpscArrayQueue;

import java.io.IOException;

/**
 * 分片器工厂
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/10
 * @version 1.0.0
 */
public class FragmenterFactory {

    @SuppressWarnings("unchecked")
    public static <P extends AbstractPacket> PacketFragmenter<P> create(AbstractPacket.PacketType packetType) {
        return (PacketFragmenter<P>) switch (packetType) {
            case SIGNALING -> new SignalingPacketFragmenter();
            case BUSINESS -> new BusinessPacketFragmenter();
        };
    }

    static class SignalingPacketFragmenter implements PacketFragmenter<SignalingPacket> {
        private final MpscArrayQueue<SignalingPacket> packets = new MpscArrayQueue<>(16);
        private SignalingPacket currentPacket;

        @Override
        public boolean feed(SignalingPacket signalingPacket) {
            return packets.offer(signalingPacket);
        }

        @Override
        public int drain(LoopBuffer target) {
            int total = 0;
            SignalingPacket packet = currentPacket;
            while (packet != null || !packets.isEmpty()) {
                if (packet == null) {
                    packet = packets.poll();
                    if (packet == null) {
                        return -1;
                    }
                }
                int totalRequired = packet.getTotalSize();
                if (target.writableBytes() < totalRequired) {
                    currentPacket = packet;
                    return total;
                }
                packet.unsafeToBuffer(target);
                packet = null;
                total += totalRequired;
            }
            return total;
        }

        @Override
        public boolean isEmpty() {
            return currentPacket == null && packets.isEmpty();
        }

        @Override
        public void clear() {
            packets.clear();
            currentPacket = null;
        }
    }

    static class BusinessPacketFragmenter implements PacketFragmenter<BusinessPacket<?>> {

        @Override
        public boolean feed(BusinessPacket<?> businessPacket) {
            return false;
        }

        @Override
        public int drain(LoopBuffer target) throws IOException {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public void clear() {

        }
    }
}
