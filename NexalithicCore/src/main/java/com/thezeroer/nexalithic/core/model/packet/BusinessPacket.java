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
    public static final int MAX_PATH_DEPTH = Byte.MAX_VALUE;
    /** 优先级紧急 */
    public static final byte PRIORITY_URGENT = -128;
    /** 优先级高 */
    public static final byte PRIORITY_HIGH = -64;
    /** 优先级普通 */
    public static final byte PRIORITY_NORMAL = 0;
    /** 优先级低 */
    public static final byte PRIORITY_LOW = 127;
    private byte suggestedPriority = PRIORITY_NORMAL;
    public enum Way {
        DEFAULT,

        REQUEST_Post,
        REQUEST_Delete,
        REQUEST_Update,
        REQUEST_Get,

        RESPONSE_Ok,
        RESPONSE_Error,
        RESPONSE_Succeed,
        RESPONSE_Failed,
        RESPONSE_NotHandler,
        RESPONSE_NotResource,
        RESPONSE_MethodNotAllowed,
        RESPONSE_BadRequest,
        RESPONSE_Unauthorized,
        RESPONSE_Forbidden,
        RESPONSE_Busy,
    }

    private short way;
    private long taskId;
    private byte packetIndex;
    private long totalSize;
    private byte pathDepth;
    private short[] path;
    private byte payloadCount;
    private List<P> payloads;

    private BusinessPacket() {

    }
    private BusinessPacket(Way way, short... path) {
        this.way = (short) way.ordinal();
        if (path != null && path.length > 0) {
            if (path.length > MAX_PATH_DEPTH) {
                throw new IllegalArgumentException("path length exceeds maximum of " + MAX_PATH_DEPTH);
            }
            this.path = path;
            this.pathDepth = (byte) path.length;
        }
    }

    public static BusinessPacket<?> build() {
        return new BusinessPacket<>();
    }
    public static BusinessPacket<?> build(Way way) {
        return new BusinessPacket<>(way);
    }
    public static BusinessPacket<?> build(Way way, short... path) {
        return new BusinessPacket<>(way, path);
    }

    @SafeVarargs
    public final AbstractPacket attach(P... payloads) {
        if (this.payloads == null) {
            this.payloads = new ArrayList<>();
        }
        if (payloads != null && payloads.length > 0) {
            if (payloadCount + payloads.length > MAX_PAYLOAD_COUNT) {
                throw new PayloadOverflowException(payloadCount, payloads.length, MAX_PAYLOAD_COUNT);
            }
            this.payloads.addAll(List.of(payloads));
            this.payloadCount = (byte) this.payloads.size();
        }
        return this;
    }

    public BusinessPacket<P> setPacketIndex(byte packetIndex) {
        this.packetIndex = packetIndex;
        return this;
    }

    public final List<P> payloads() {
        return payloads;
    }
    public final P payload(int index) {
        if (payloads == null || payloads.isEmpty() || index >= payloadCount) {
            return null;
        }
        return payloads.get(index);
    }
    public final P FirstPayload() {
        if (payloads == null || payloads.isEmpty()) {
            return null;
        }
        return payloads.getFirst();
    }
    public final P LastPayload() {
        if (payloads == null || payloads.isEmpty()) {
            return null;
        }
        return payloads.getLast();
    }
    public final byte getPayloadCount() {
        return payloadCount;
    }
    public final byte getPacketIndex() {
        return packetIndex;
    }

    /**
     * 设置建议优先级，小值优先
     *
     * @param suggestedPriority 建议优先级
     * @return {@link BusinessPacket }
     */
    public final BusinessPacket<?> setSuggestedPriority(byte suggestedPriority) {
        this.suggestedPriority = suggestedPriority;
        return this;
    }
    public final byte getSuggestedPriority() {
        return suggestedPriority;
    }

    @Override
    public final PacketType packetType() {
        return PacketType.BUSINESS;
    }
}
