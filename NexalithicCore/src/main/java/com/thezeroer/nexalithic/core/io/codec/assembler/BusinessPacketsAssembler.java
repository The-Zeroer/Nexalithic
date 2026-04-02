package com.thezeroer.nexalithic.core.io.codec.assembler;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.io.codec.PacketFrame;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionValidator;
import com.thezeroer.nexalithic.core.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.recyclable.PoolStorage;
import com.thezeroer.nexalithic.core.recyclable.PoolStrategy;
import com.thezeroer.nexalithic.core.recyclable.SelfStaticWrapperPool;
import com.thezeroer.nexalithic.core.recyclable.WrapperPool;
import com.thezeroer.nexalithic.core.timer.GenericTimeWheel;
import com.thezeroer.nexalithic.core.timer.TimerExecutor;
import org.jctools.queues.MpscArrayQueue;
import org.jctools.queues.SpmcArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 业务包汇编器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/10
 * @version 1.0.0
 */
public class BusinessPacketsAssembler implements PacketsAssembler<BusinessPacket>, TimerExecutor<BusinessPacketAssemblyWrapper> {
    public static final class Options implements OptionsDefinition {
        public static final NexalithicOption<Integer> WrapperPool_Capacity = NexalithicOption.create(
                "BusinessPacketsAssembler_WrapperPool_Capacity", 1024, OptionValidator.positive()
        );
        public static final NexalithicOption<Integer> PacketQueue_Capacity = NexalithicOption.create(
                "BusinessPacketsAssembler_PacketQueue_Capacity", 64, OptionValidator.positive()
        );
        public static final NexalithicOption<Long> MaxWaitTime = NexalithicOption.create(
                "BusinessPacketsAssembler_MaxWaitTime", 30000L, OptionValidator.positive()
        );
        public static final NexalithicOption<Long> TimeWheel_Tick = NexalithicOption.create(
                "BusinessPacketsAssembler_TimeWheel_Tick", 1000L, OptionValidator.positive()
        );
        public static final NexalithicOption<Integer> TimeWheel_WrapperPool_Capacity = NexalithicOption.create(
                "BusinessPacketsAssembler_TimeWheel_WrapperPool_Capacity", 256, OptionValidator.positive()
        );
    }
    private static final Logger logger = LoggerFactory.getLogger(BusinessPacketsAssembler.class);
    private final WrapperPool<BusinessPacketAssemblyWrapper> wrapperPool;
    private final Map<Integer, BusinessPacketAssemblyWrapper> assemblingMap = new HashMap<>();
    private final MpscArrayQueue<BusinessPacket> completedPackets = new MpscArrayQueue<>(Interior.PacketQueue_Capacity);
    private BusinessPacket pendingPacket;

    public BusinessPacketsAssembler(WrapperPool<BusinessPacketAssemblyWrapper> wrapperPool) {
        this.wrapperPool = wrapperPool;
    }

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
        while (source.readableBytes() > PacketFrame.FRAME_HEADER_LENGTH) {
            source.markHead();
            long meta = source.unsafeGetLong();
            int payloadLength = PacketFrame.parsePayloadLength(meta);
            int packetId = PacketFrame.parsePacketId(meta);
            if (source.readableBytes() < payloadLength) {
                source.resetHead();
                break;
            }
            BusinessPacketAssemblyWrapper wrapper = assemblingMap.computeIfAbsent(packetId, id -> wrapperPool.acquire().setPacketId(id));
            read = wrapper.onFrame(source, payloadLength, PacketFrame.isStart(meta));
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
        for (BusinessPacketAssemblyWrapper wrapper : assemblingMap.values()) {
            wrapper.recycle();
        }
        assemblingMap.clear();
    }

    @Override
    public void trigger(BusinessPacketAssemblyWrapper wrapper) {
        assemblingMap.remove(wrapper.getPacketId());
        wrapper.recycle();
    }

    private static class Interior {
        public static final int PacketQueue_Capacity = Options.PacketQueue_Capacity.value();

        public static final GenericTimeWheel timeWheel = new GenericTimeWheel(
                Options.TimeWheel_Tick.value(),
                (int) (Options.MaxWaitTime.value() / Options.TimeWheel_Tick.value()) + 1,
                new SelfStaticWrapperPool<>(
                        PoolStorage.of(new SpmcArrayQueue<>(Options.TimeWheel_WrapperPool_Capacity.value()), Options.TimeWheel_WrapperPool_Capacity.value()),
                        PoolStrategy.alwaysCreate(),
                        GenericTimeWheel.GenericScheduleWrapper<BusinessPacketAssemblyWrapper>::new
                ),
                BusinessPacketsAssembler.class.getSimpleName()
        );

        static {
            timeWheel.start();
        }
    }
}
