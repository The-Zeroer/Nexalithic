package com.thezeroer.nexalithic.core.model.packet;

import com.thezeroer.nexalithic.core.model.payload.AbstractPayload;

/**
 * 流式数据包
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
public final class StreamingPacket extends AbstractPacket<AbstractPayload<?>> {
    @Override
    public TYPE getType() {
        return TYPE.STREAMING;
    }
}
