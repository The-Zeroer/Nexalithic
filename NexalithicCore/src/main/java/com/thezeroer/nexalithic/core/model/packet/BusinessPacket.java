package com.thezeroer.nexalithic.core.model.packet;

import com.thezeroer.nexalithic.core.exception.PayloadOverflowException;
import com.thezeroer.nexalithic.core.model.packet.payload.AbstractPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务包
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
public class BusinessPacket<P extends AbstractPayload<?>> extends AbstractPacket {
    /** 优先级紧急 */
    public static final byte PRIORITY_URGENT = -128;
    /** 优先级高 */
    public static final byte PRIORITY_HIGH = -64;
    /** 优先级普通 */
    public static final byte PRIORITY_NORMAL = 0;
    /** 优先级低 */
    public static final byte PRIORITY_LOW = 127;
    private byte suggestedPriority = PRIORITY_NORMAL;
    public static enum WAY {

    }
    public static class Metadata {
        private long taskId;
        private byte packetIndex;
        private long totalSize;
    }
    private final List<P> payloads;

    private BusinessPacket() {
        payloads = new ArrayList<>();
    }

    public static BusinessPacket<?> build() {
        return new BusinessPacket<>();
    }

    @SafeVarargs
    public final AbstractPacket attach(P... payloads) {
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
    public final byte getPayloadCount() {
        if (payloads == null) {
            return 0;
        }
        return (byte) payloads.size();
    }

    /**
     * 设置建议优先级，小值优先
     *
     * @param suggestedPriority 建议优先级
     * @return {@link BusinessPacket }
     */
    public BusinessPacket<?> setSuggestedPriority(byte suggestedPriority) {
        this.suggestedPriority = suggestedPriority;
        return this;
    }
    public byte getSuggestedPriority() {
        return suggestedPriority;
    }

    @Override
    public PacketType packetType() {
        return PacketType.BUSINESS;
    }
}
