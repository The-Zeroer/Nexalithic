package com.thezeroer.nexalithic.core.io.codec;

import com.thezeroer.nexalithic.core.NexalithicEndpoint;
import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.infra.recyclable.PoolStorage;
import com.thezeroer.nexalithic.core.infra.recyclable.PoolStrategy;
import com.thezeroer.nexalithic.core.infra.recyclable.SelfStaticWrapperPool;
import com.thezeroer.nexalithic.core.infra.recyclable.WrapperPool;
import com.thezeroer.nexalithic.core.io.codec.assembler.*;
import com.thezeroer.nexalithic.core.messaging.payload.PayloadRegistry;
import com.thezeroer.nexalithic.core.messaging.task.TaskScheduler;
import com.thezeroer.nexalithic.core.infra.timer.GenericTimeWheel;
import com.thezeroer.nexalithic.core.model.packet.business.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.signaling.SignalingPacket;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
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
        TaskScheduler taskScheduler = context.getModule(NexalithicEndpoint.Modules.TaskScheduler);
        wrapperPool = new SelfStaticWrapperPool<>(
                PoolStorage.of(SpscArrayQueue::new, context.getOption(BusinessPacketsAssembler.OPTIONS.WrapperPool_Capacity)),
                PoolStrategy.alwaysCreate(),
                () -> new BusinessPacketAssemblyWrapper(businessPacketAssemblyConstant, payloadRegistry, new AssemblyCallback(taskScheduler))
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

    public PacketsAssembler<SignalingPacket> createSignaling() {
        return new SignalingPacketsAssembler();
    }

    public PacketsAssembler<BusinessPacket> createBusiness(NexalithicSession<?, ?, ?> session) {
        return new BusinessPacketsAssembler(session, wrapperPool, timeWheel, PacketQueue_Capacity_);
    }
}
