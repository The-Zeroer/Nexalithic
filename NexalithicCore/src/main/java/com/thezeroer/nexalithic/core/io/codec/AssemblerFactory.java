package com.thezeroer.nexalithic.core.io.codec;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.io.codec.assembler.BusinessPacketsAssembler;
import com.thezeroer.nexalithic.core.io.codec.assembler.PacketsAssembler;
import com.thezeroer.nexalithic.core.io.codec.assembler.SignalingPacketsAssembler;
import com.thezeroer.nexalithic.core.io.codec.assembler.BusinessPacketAssemblyWrapper;
import com.thezeroer.nexalithic.core.messaging.payload.PayloadRegistry;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.recyclable.*;
import com.thezeroer.nexalithic.core.timer.GenericTimeWheel;
import org.jctools.queues.SpmcArrayQueue;
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
    private final GenericTimeWheel timeWheel;
    private final int PacketQueue_Capacity_;

    public AssemblerFactory(NexalithicBuilderContext context) {
        PacketQueue_Capacity_ = context.getOption(BusinessPacketsAssembler.OPTIONS.PacketQueue_Capacity);
        BusinessPacketAssemblyWrapper.Constant businessPacketAssemblyConstant = new BusinessPacketAssemblyWrapper.Constant(
                context.getOption(BusinessPacketsAssembler.OPTIONS.MaxIdleTime)
        );
        PayloadRegistry payloadRegistry = context.getModule(BusinessPacketsAssembler.Modules.PayloadRegistry);
        wrapperPool = new SelfStaticWrapperPool<>(
                PoolStorage.of(SpscArrayQueue::new, context.getOption(BusinessPacketsAssembler.OPTIONS.WrapperPool_Capacity)),
                PoolStrategy.alwaysCreate(),
                () -> new BusinessPacketAssemblyWrapper(businessPacketAssemblyConstant, payloadRegistry)
        );
        timeWheel = context.getModule(BusinessPacketsAssembler.Modules.TimeWheel, () -> {
            GenericTimeWheel timeWheel = new GenericTimeWheel(
                    context.getOption(BusinessPacketsAssembler.OPTIONS.TimeWheel.Tick),
                    context.getOption(BusinessPacketsAssembler.OPTIONS.TimeWheel.Slot),
                    context.getOption(BusinessPacketsAssembler.OPTIONS.TimeWheel.TickQuotaShift),
                    context.getOption(BusinessPacketsAssembler.OPTIONS.TimeWheel.WaitQueue_ChunkSize),
                    new SelfStaticWrapperPool<>(
                            PoolStorage.of(SpmcArrayQueue::new, context.getOption(BusinessPacketsAssembler.OPTIONS.TimeWheel.WrapperPool_Capacity)),
                            PoolStrategy.alwaysCreate(),
                            GenericTimeWheel.GenericScheduleWrapper<BusinessPacketAssemblyWrapper>::new
                    ),
                    BusinessPacketsAssembler.class.getSimpleName()
            );
            timeWheel.start();
            return timeWheel;
        });
    }

    @SuppressWarnings("unchecked")
    public <P extends AbstractPacket> PacketsAssembler<P> create(AbstractPacket.PacketType packetType) {
        return (PacketsAssembler<P>) switch (packetType) {
            case SIGNALING -> new SignalingPacketsAssembler();
            case BUSINESS -> new BusinessPacketsAssembler(wrapperPool, timeWheel, PacketQueue_Capacity_);
        };
    }
}
