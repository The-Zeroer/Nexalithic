package com.thezeroer.nexalithic.core.io.codec;

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
    @SuppressWarnings("unchecked")
    public <W extends FragmentWrapper<?>> PacketsFragmenter<W> create(AbstractPacket.PacketType packetType) {
        return (PacketsFragmenter<W>) switch (packetType) {
            case SIGNALING -> new SignalingPacketsFragmenter();
            case BUSINESS -> new BusinessPacketsFragmenter();
        };
    }
}
