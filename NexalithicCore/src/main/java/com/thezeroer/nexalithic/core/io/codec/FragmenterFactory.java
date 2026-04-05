package com.thezeroer.nexalithic.core.io.codec;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.io.codec.fragmenter.BusinessPacketsFragmenter;
import com.thezeroer.nexalithic.core.io.codec.fragmenter.PacketsFragmenter;
import com.thezeroer.nexalithic.core.io.codec.fragmenter.SignalingPacketsFragmenter;
import com.thezeroer.nexalithic.core.io.codec.fragmenter.FragmentWrapper;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;

/**
 * 分片器工厂
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/10
 * @version 1.0.0
 */
public class FragmenterFactory {
    private final int SignalingPacketsFragmenter_WrapperQueue_Capacity_, BusinessPacketsFragmenter_WrapperQueue_Capacity_, WrapperLinked_Capacity_;

    public FragmenterFactory(NexalithicBuilderContext context) {
        SignalingPacketsFragmenter_WrapperQueue_Capacity_ = context.getOption(SignalingPacketsFragmenter.OPTIONS.WrapperQueue_Capacity);
        BusinessPacketsFragmenter_WrapperQueue_Capacity_ = context.getOption(BusinessPacketsFragmenter.OPTIONS.WrapperQueue_Capacity);
        WrapperLinked_Capacity_ = context.getOption(BusinessPacketsFragmenter.OPTIONS.WrapperLinked_Capacity);
    }

    @SuppressWarnings("unchecked")
    public <W extends FragmentWrapper<?>> PacketsFragmenter<W> create(AbstractPacket.PacketType packetType) {
        return (PacketsFragmenter<W>) switch (packetType) {
            case SIGNALING -> new SignalingPacketsFragmenter(SignalingPacketsFragmenter_WrapperQueue_Capacity_);
            case BUSINESS -> new BusinessPacketsFragmenter(BusinessPacketsFragmenter_WrapperQueue_Capacity_, WrapperLinked_Capacity_);
        };
    }
}
