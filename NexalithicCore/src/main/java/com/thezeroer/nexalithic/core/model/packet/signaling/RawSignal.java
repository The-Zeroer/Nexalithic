package com.thezeroer.nexalithic.core.model.packet.signaling;

import com.thezeroer.nexalithic.core.infra.buffer.LoopBuffer;

/**
 * 原始信号
 *
 * @author tbrtz647@outlook.com
 * @since 2026/04/11
 * @version 1.0.0
 */
public class RawSignal extends SignalingPacket {
    private byte[] content;

    public RawSignal(byte signal, byte[] content) {
        super(signal);
        if (content == null) {
            return;
        }
        if (content.length + HEADER_LENGTH > MAX_PACKET_LENGTH) {
            throw new IllegalArgumentException("Packet length exceeds maximum of " + MAX_PACKET_LENGTH);
        }
        this.length = (short) content.length;
        this.content = content;
    }

    public void onToBuffer(LoopBuffer buffer) {
        if (content != null) {
            buffer.unsafePut(content, length);
        }
    }

    public short getContentLength() {
        return length;
    }
    public byte[] getContent() {
        return content;
    }
}
