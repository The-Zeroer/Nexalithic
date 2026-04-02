package com.thezeroer.nexalithic.core.io.codec.assembler;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;

import java.io.IOException;

/**
 * 分组汇编器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/03
 * @version 1.0.0
 */
public interface PacketsAssembler<P extends AbstractPacket> {
    void feed(LoopBuffer source) throws IOException;
    P drain();
    void clear();
}
