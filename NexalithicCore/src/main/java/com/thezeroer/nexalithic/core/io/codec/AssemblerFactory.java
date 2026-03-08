package com.thezeroer.nexalithic.core.io.codec;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import org.jctools.queues.SpscArrayQueue;

import java.io.IOException;

/**
 * 汇编器工厂
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/10
 * @version 1.0.0
 */
public class AssemblerFactory {

    @SuppressWarnings("unchecked")
    public static <P extends AbstractPacket> PacketAssembler<P> create(AbstractPacket.PacketType packetType) {
        return (PacketAssembler<P>) switch (packetType) {
            case SIGNALING -> new SignalingPacketAssembler();
            case BUSINESS -> new BusinessPacketAssembler();
        };
    }

    static class SignalingPacketAssembler implements PacketAssembler<SignalingPacket> {
        private final SpscArrayQueue<SignalingPacket> packets = new SpscArrayQueue<>(16);
        private SignalingPacket currentPacket;

        @Override
        public int feed(LoopBuffer source) {
            int count = 0;
            if (currentPacket != null) {
                if (packets.offer(currentPacket)) {
                    count++;
                    currentPacket = null;
                } else {
                    return count;
                }
            }
            while (true) {
                int readable = source.readableBytes();
                if (readable < SignalingPacket.HEADER_LENGTH) {
                    break;
                }
                source.mark();
                byte signal = source.unsafeGetByte();
                short length = source.unsafeGetShort();
                if (source.readableBytes() < length) {
                    source.reset();
                    break;
                }
                byte[] content = new byte[length];
                source.unsafeGetBytes(content, length);
                SignalingPacket packet = new SignalingPacket(signal, content);
                if (packets.offer(packet)) {
                    count++;
                    source.dropMark();
                } else {
                    this.currentPacket = packet;
                    break;
                }
            }
            return count;
        }

        @Override
        public SignalingPacket drain() {
            return packets.poll();
        }

        @Override
        public void clear() {
            packets.clear();
            currentPacket = null;
        }
    }

    static class BusinessPacketAssembler implements PacketAssembler<BusinessPacket<?>> {

        @Override
        public int feed(LoopBuffer source) throws IOException {
            return 0;
        }

        @Override
        public BusinessPacket<?> drain() {
            return null;
        }

        @Override
        public void clear() {

        }
    }
}
