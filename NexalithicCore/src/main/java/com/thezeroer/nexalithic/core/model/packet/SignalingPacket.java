package com.thezeroer.nexalithic.core.model.packet;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.model.payload.AbstractPayload;

/**
 * 信令包
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
public final class SignalingPacket extends AbstractPacket<AbstractPayload<?>> {
    public static final byte SIGNAL_DH_KEY = 0x00;
    public static final byte SIGNAL_TOKEN = 0x01;

    private byte signal;
    private byte[] content;

    public SignalingPacket(byte signal) {
        this.signal = signal;
    }

    public SignalingPacket(byte signal, byte[] content) {
        if (content.length > Short.MAX_VALUE) {
            throw new IllegalArgumentException("content.length > " + Short.MAX_VALUE);
        }
        this.signal = signal;
        this.content = content;
    }

    public void toBuffer(LoopBuffer buffer) {
        buffer.put(signal);
        if (content != null) {
            buffer.put((short) content.length);
            buffer.put(content);
        } else {
            buffer.put((short) 0);
        }
    }

    public byte getSignal() {
        return signal;
    }

    public byte[] getContent() {
        return content;
    }

    public short getContentLength() {
        return content == null ? 0 : (short) content.length;
    }

    public int getTotalSize() {
        return Byte.BYTES + getContentLength();
    }

    @Override
    public TYPE getType() {
        return TYPE.SIGNALING;
    }
}
