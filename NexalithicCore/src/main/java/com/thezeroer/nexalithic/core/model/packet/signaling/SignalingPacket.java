package com.thezeroer.nexalithic.core.model.packet.signaling;

import com.thezeroer.nexalithic.core.infra.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.io.codec.fragmenter.FragmentWrapper;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;

/**
 * 信令包
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
public abstract class SignalingPacket extends AbstractPacket implements FragmentWrapper<SignalingPacket> {
    public static class Signal {
        public static final byte HeartBeat = 0x00;
        public static final byte BusinessChannelToken = 0x01;
        public static final byte RequestBusinessPort = 0x11;
        public static final byte ResponseBusinessPort = 0x12;
    }
    public static final int HEADER_LENGTH = Byte.BYTES + Short.BYTES;
    public static final int MAX_PACKET_LENGTH = 1024 * 4;

    protected final byte signal;
    protected short length;

    protected SignalingPacket(byte signal) {
        this.signal = signal;
    }

    public final boolean toBuffer(LoopBuffer buffer) {
        if (buffer.writableBytes() < getTotalSize()) {
            return false;
        }
        buffer.unsafePut(signal);
        buffer.unsafePut(length);
        onToBuffer(buffer);
        return true;
    }
    public static SignalingPacket fromBuffer(LoopBuffer buffer) {
        buffer.markHead();
        byte signal = buffer.unsafeGetByte();
        short length = buffer.unsafeGetShort();
        if (buffer.readableBytes() < length) {
            buffer.resetHead();
            return null;
        }
        return switch (signal) {
            case Signal.BusinessChannelToken -> new TokenSignal(buffer);
            case Signal.ResponseBusinessPort -> new ScalarSignal(signal, buffer.unsafeGetLong());
            default -> {
                if (length == 0) {
                    BareSignal bare = BareSignal.find(signal);
                    yield bare != null ? bare : new RawSignal(signal, null);
                } else {
                    byte[] content = new byte[length];
                    buffer.unsafeGetBytes(content, length);
                    yield new RawSignal(signal, content);
                }
            }
        };
    }

    public byte getSignal() {
        return signal;
    }
    public int getTotalSize() {
        return HEADER_LENGTH + getContentLength();
    }

    protected abstract void onToBuffer(LoopBuffer buffer);
    public abstract byte[] getContent();
    public abstract short getContentLength();

    @Override
    public PacketType packetType() {
        return PacketType.SIGNALING;
    }
}
