package com.thezeroer.nexalithic.core.io.codec;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.io.codec.wrapper.BusinessPacketAssemblyWrapper;
import com.thezeroer.nexalithic.core.io.codec.wrapper.BusinessPacketFragmentWrapper;
import com.thezeroer.nexalithic.core.messaging.payload.PayloadRegistry;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.recyclable.*;
import com.thezeroer.nexalithic.core.timer.TimerExecutor;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.MpscArrayQueue;
import org.jctools.queues.SpscArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 汇编器工厂
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/10
 * @version 1.0.0
 */
public class AssemblerFactory {
    public static final NexalithicOption<Integer> WrapperPool_Capacity = NexalithicOption.create("BusinessPacketsAssembler_WrapperPool_Capacity", 4096);
    public static final NexalithicOption<Integer> PacketQueue_Capacity = NexalithicOption.create("BusinessPacketsAssembler_PacketQueue_Capacity", 64);
    private static final Logger logger = LoggerFactory.getLogger(AssemblerFactory.class);

    private final WrapperPool<BusinessPacketAssemblyWrapper> WRAPPER_POOL;

    public AssemblerFactory(PayloadRegistry payloadRegistry) {
        WRAPPER_POOL = new SelfStaticWrapperPool<>(
                PoolStorage.of(new MpmcArrayQueue<>(WrapperPool_Capacity.value()), WrapperPool_Capacity.value()),
                PoolStrategy.alwaysCreate(),
                () -> new BusinessPacketAssemblyWrapper(payloadRegistry)
        );
    }

    @SuppressWarnings("unchecked")
    public <P extends AbstractPacket> PacketsAssembler<P> create(AbstractPacket.PacketType packetType) {
        return (PacketsAssembler<P>) switch (packetType) {
            case SIGNALING -> new SignalingPacketsAssembler();
            case BUSINESS -> new BusinessPacketsAssembler();
        };
    }

    static class SignalingPacketsAssembler implements PacketsAssembler<SignalingPacket> {
        private final SpscArrayQueue<SignalingPacket> packets = new SpscArrayQueue<>(128);
        private SignalingPacket pendingPacket;

        @Override
        public void feed(LoopBuffer source) {
            if (pendingPacket != null) {
                if (packets.offer(pendingPacket)) {
                    pendingPacket = null;
                } else {
                    return;
                }
            }
            while (true) {
                int readable = source.readableBytes();
                if (readable < SignalingPacket.HEADER_LENGTH) {
                    break;
                }
                source.markHead();
                byte signal = source.unsafeGetByte();
                short length = source.unsafeGetShort();
                if (source.readableBytes() < length) {
                    source.resetHead();
                    break;
                }
                byte[] content = new byte[length];
                source.unsafeGetBytes(content, length);
                SignalingPacket packet = new SignalingPacket(signal, content);
                if (logger.isTraceEnabled()) {
                    logger.trace("[{}] received SIGNALING packet", packet);
                }
                if (packets.offer(packet)) {
                    source.dropMarkHead();
                } else {
                    this.pendingPacket = packet;
                    break;
                }
            }
        }

        @Override
        public SignalingPacket drain() {
            if (pendingPacket != null && packets.offer(pendingPacket)) {
                pendingPacket = null;
            }
            return packets.poll();
        }

        @Override
        public void clear() {
            packets.clear();
            pendingPacket = null;
        }
    }

    class BusinessPacketsAssembler implements PacketsAssembler<BusinessPacket>, TimerExecutor<BusinessPacketAssemblyWrapper> {
        private final Map<Long, BusinessPacketAssemblyWrapper> assemblingMap = new HashMap<>();
        private final MpscArrayQueue<BusinessPacket> completedPackets = new MpscArrayQueue<>(PacketQueue_Capacity.value());
        private BusinessPacket pendingPacket;

        @Override
        public void feed(LoopBuffer source) throws IOException {
            if (pendingPacket != null) {
                if (completedPackets.offer(pendingPacket)) {
                    pendingPacket = null;
                } else {
                    return;
                }
            }
            int read;
            while (source.readableBytes() > BusinessPacketFragmentWrapper.FRAME_HEADER_LENGTH) {
                source.markHead();
                short payloadLength = source.unsafeGetShort();
                if (source.readableBytes() < payloadLength) {
                    source.resetHead();
                    break;
                }
                long packetId = source.unsafeGetLong();
                BusinessPacketAssemblyWrapper wrapper = assemblingMap.computeIfAbsent(packetId, id -> WRAPPER_POOL.acquire().setPacketId(id));
                LoopBuffer.LimitedReadableView readableView = source.unsafeLimitedReadableView(payloadLength);
                read = wrapper.onFrame(readableView);
                if (!wrapper.hasFrame()) {
                    assemblingMap.remove(packetId);
                    BusinessPacket packet = wrapper.getPacket();
                    if (logger.isTraceEnabled()) {
                        logger.trace("[{}] received BUSINESS packet", packet);
                    }
                    wrapper.recycle();
                    if (!completedPackets.offer(packet)) {
                        pendingPacket = packet;
                        break;
                    }
                }
                if (read == 0) {
                    break;
                }
            }
        }

        @Override
        public BusinessPacket drain() {
            if (pendingPacket != null && completedPackets.offer(pendingPacket)) {
                pendingPacket = null;
            }
            return completedPackets.poll();
        }

        @Override
        public void clear() {
            pendingPacket = null;
            completedPackets.clear();
            for(BusinessPacketAssemblyWrapper wrapper : assemblingMap.values()) {
                wrapper.recycle();
            }
            assemblingMap.clear();
        }

        @Override
        public void trigger(BusinessPacketAssemblyWrapper wrapper) {
            assemblingMap.remove(wrapper.getPacketId());
            wrapper.recycle();
        }
    }
}
