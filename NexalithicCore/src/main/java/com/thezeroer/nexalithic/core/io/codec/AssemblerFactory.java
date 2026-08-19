package com.thezeroer.nexalithic.core.io.codec;

import com.thezeroer.nexalithic.core.NexalithicEndpoint;
import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.infra.recyclable.GenericWrapperPool;
import com.thezeroer.nexalithic.core.infra.recyclable.PoolStorageFactory;
import com.thezeroer.nexalithic.core.infra.recyclable.PoolStrategyFactory;
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
        //noinspection Convert2Diamond
        wrapperPool = new GenericWrapperPool<BusinessPacketAssemblyWrapper, BusinessPacketAssemblyWrapper>(
                PoolStorageFactory.bounded(SpscArrayQueue::new, context.getOption(BusinessPacketsAssembler.OPTIONS.WrapperPool_Capacity)),
                PoolStrategyFactory.alwaysCreate(),
                owner -> new BusinessPacketAssemblyWrapper(owner, businessPacketAssemblyConstant, new AssemblyCallback(taskScheduler), payloadRegistry)
        );
        timeWheel = context.getModule(BusinessPacketsAssembler.Modules.TimeWheel, () -> {
            GenericTimeWheel timeWheel = new GenericTimeWheel(
                    context.getOption(BusinessPacketsAssembler.OPTIONS.TimeWheel.Tick),
                    context.getOption(BusinessPacketsAssembler.OPTIONS.TimeWheel.Slot),
                    context.getOption(BusinessPacketsAssembler.OPTIONS.TimeWheel.TickQuotaShift),
                    context.getOption(BusinessPacketsAssembler.OPTIONS.TimeWheel.WaitQueue_ChunkSize),
                    new GenericWrapperPool<>(
                            PoolStorageFactory.bounded(SpmcArrayQueue::new, context.getOption(BusinessPacketsAssembler.OPTIONS.TimeWheel.WrapperPool_Capacity)),
                            PoolStrategyFactory.alwaysCreate(),
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
