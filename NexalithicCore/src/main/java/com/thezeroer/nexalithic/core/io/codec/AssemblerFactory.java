package com.thezeroer.nexalithic.core.io.codec;

import com.thezeroer.nexalithic.core.io.codec.assembler.BusinessPacketsAssembler;
import com.thezeroer.nexalithic.core.io.codec.assembler.PacketsAssembler;
import com.thezeroer.nexalithic.core.io.codec.assembler.SignalingPacketsAssembler;
import com.thezeroer.nexalithic.core.io.codec.assembler.BusinessPacketAssemblyWrapper;
import com.thezeroer.nexalithic.core.messaging.payload.PayloadRegistry;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.recyclable.*;
import org.jctools.queues.SpscArrayQueue;

/**
 * 汇编器工厂
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/10
 * @version 1.0.0
 */
public class AssemblerFactory {
    private final WrapperPool<BusinessPacketAssemblyWrapper> wrapperPool;

    public AssemblerFactory(PayloadRegistry payloadRegistry) {
        wrapperPool = new SelfStaticWrapperPool<>(
                PoolStorage.of(new SpscArrayQueue<>(Interior.WrapperPool_Capacity), Interior.WrapperPool_Capacity),
                PoolStrategy.alwaysCreate(),
                () -> new BusinessPacketAssemblyWrapper(payloadRegistry)
        );
    }

    @SuppressWarnings("unchecked")
    public <P extends AbstractPacket> PacketsAssembler<P> create(AbstractPacket.PacketType packetType) {
        return (PacketsAssembler<P>) switch (packetType) {
            case SIGNALING -> new SignalingPacketsAssembler();
            case BUSINESS -> new BusinessPacketsAssembler(wrapperPool);
        };
    }

    private static class Interior {
        public static final int WrapperPool_Capacity = BusinessPacketsAssembler.Options.WrapperPool_Capacity.value();
    }
}
