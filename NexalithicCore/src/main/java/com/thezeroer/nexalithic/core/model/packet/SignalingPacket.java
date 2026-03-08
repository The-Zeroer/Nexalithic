package com.thezeroer.nexalithic.core.model.packet;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;

/**
 * 信令包
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
public class SignalingPacket extends AbstractPacket {
    public static class Signal {
        public static final byte HeartBeat = 0x00;
        public static final byte BusinessChannelToken = 0x01;
        public static final byte RequestBusinessPort = 0x11;
        public static final byte ResponseBusinessPort = 0x12;
    }
    public static final int HEADER_LENGTH = Byte.BYTES + Short.BYTES;
    public static final int MAX_PACKET_LENGTH = 1024 * 4;

    private byte signal;
    private short length;

    private byte[] content;

    public SignalingPacket(byte signal) {
        this.signal = signal;
    }

    public SignalingPacket(byte signal, byte[] content) {
        if (content.length + HEADER_LENGTH > MAX_PACKET_LENGTH) {
            throw new IllegalArgumentException("Packet length exceeds maximum of " + MAX_PACKET_LENGTH);
        }
        this.signal = signal;
        this.length = (short) content.length;
        this.content = content;
    }

    public void toBuffer(LoopBuffer buffer) {
        buffer.put(signal);
        buffer.put(length);
        if (content != null) {
            buffer.put(content);
        }
    }
    public void unsafeToBuffer(LoopBuffer buffer) {
        buffer.unsafePut(signal);
        buffer.unsafePut(length);
        if (content != null) {
            buffer.unsafePut(content, length);
        }
    }

    public static SignalingPacket fromBuffer(LoopBuffer buffer) {
        byte signal = buffer.getByte();
        short length = buffer.getShort();
        if (length > 0) {
            byte[] content = new byte[length];
            buffer.getBytes(content);
            return new SignalingPacket(signal, content);
        } else {
            return new SignalingPacket(signal);
        }
    }
    public static SignalingPacket unsafeFromBuffer(LoopBuffer buffer) {
        byte signal = buffer.unsafeGetByte();
        short length = buffer.unsafeGetShort();
        if (length > 0) {
            byte[] content = new byte[length];
            buffer.unsafeGetBytes(content, length);
            return new SignalingPacket(signal, content);
        } else {
            return new SignalingPacket(signal);
        }
    }

    public void setSignal(byte signal) {
        this.signal = signal;
    }
    public void setContent(byte[] content) {
        this.content = content;
        if (content != null) {
            length = (short) content.length;
        } else {
            length = 0;
        }
    }

    public byte getSignal() {
        return signal;
    }
    public short getContentLength() {
        return length;
    }
    public byte[] getContent() {
        return content;
    }

    public int getTotalSize() {
        return HEADER_LENGTH + getContentLength();
    }

    @Override
    public PacketType packetType() {
        return PacketType.SIGNALING;
    }
}
