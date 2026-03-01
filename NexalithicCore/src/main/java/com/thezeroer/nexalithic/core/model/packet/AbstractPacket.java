package com.thezeroer.nexalithic.core.model.packet;

import com.thezeroer.nexalithic.core.exception.PayloadOverflowException;
import com.thezeroer.nexalithic.core.model.payload.AbstractPayload;
import com.thezeroer.nexalithic.core.util.SteppedSequenceGenerator;

import java.util.List;

/**
 * 抽象包
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
public abstract sealed class AbstractPacket<P extends AbstractPayload<?>> permits SignalingPacket, TransactionPacket, StreamingPacket {
    public static final int MAGIC_NUMBER = 0x494D5450;
    public static final int MAX_PAYLOAD_COUNT = Byte.MAX_VALUE;
    public static enum TYPE {
        /** 信令包 */ SIGNALING,
        /** 事务包 */ TRANSACTION,
        /** 流媒体 */ STREAMING,
    }

    private static final SteppedSequenceGenerator sequenceGenerator = new SteppedSequenceGenerator();
    protected long packetId;
    protected List<P> payloads;

    protected AbstractPacket() {
        packetId = sequenceGenerator.nextId();
    }

    @SafeVarargs
    public final AbstractPacket<P> attach(P... payloads) {
        if (payloads != null && payloads.length > 0) {
            if (MAX_PAYLOAD_COUNT - this.payloads.size() < payloads.length) {
                throw new PayloadOverflowException(this.payloads.size(), payloads.length, MAX_PAYLOAD_COUNT);
            }
            this.payloads.addAll(List.of(payloads));
        }
        return this;
    }
    public final List<P> payloads() {
        return payloads;
    }
    public final P payload(int index) {
        return payloads.get(index);
    }
    public final P FirstPayload() {
        if (payloads.isEmpty()) {
            return null;
        }
        return payloads.getFirst();
    }
    public final P LastPayload() {
        if (payloads.isEmpty()) {
            return null;
        }
        return payloads.getLast();
    }

    /**
     * 获取包类型
     *
     * @return {@link TYPE }
     */
    public abstract TYPE getType();
    public final long getPacketId() {
        return packetId;
    }
    public final byte getPayloadCount() {
        if (payloads == null) {
            return 0;
        }
        return (byte) payloads.size();
    }
}
