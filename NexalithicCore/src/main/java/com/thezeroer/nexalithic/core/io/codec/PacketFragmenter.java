package com.thezeroer.nexalithic.core.io.codec;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;

import java.io.IOException;

/**
 * 包分片器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/03
 * @version 1.0.0
 */
public interface PacketFragmenter<P extends AbstractPacket<?>> {
    boolean dispatch(P p);
    void bindBuffer(LoopBuffer.Recyclable recyclable);
    int drain() throws IOException;
    void clear();
}
