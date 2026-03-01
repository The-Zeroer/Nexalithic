package com.thezeroer.nexalithic.core.io.codec;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;

/**
 * 分组汇编器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/03
 * @version 1.0.0
 */
public interface PacketAssembler {
    AbstractPacket<?> dispatch(LoopBuffer buffer);
    void clear();
}
