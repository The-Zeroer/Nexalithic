package com.thezeroer.nexalithic.core.model.packet.business;

import com.thezeroer.nexalithic.core.messaging.visual.TransferSnapshot;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.business.payload.AbstractPayload;
import com.thezeroer.nexalithic.core.model.packet.business.payload.TextPayload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务包
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
public class BusinessPacket extends AbstractPacket {
    public static final int MAX_PATH_DEPTH = Byte.MAX_VALUE;
    public static final int BASE_HEADER_SIZE = Byte.BYTES * 2 + Short.BYTES + Long.BYTES * 2;

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

    private static final Way[] WAYS = Way.values();
    private static final AtomicInteger counter = new AtomicInteger(0);
    private final int packetId = counter.getAndIncrement();
    private volatile boolean sealed = false;

    private long taskId = Long.MIN_VALUE;
    private long packetSize;
    private short way;
    private byte pathDepth;
    private short[] path;
    private byte payloadCount;
    private long[] payloadsMeta;
    private List<AbstractPayload<?>> payloads;

    private BusinessPacket(Way way, short... path) {
        this.way = (short) way.ordinal();
        if (path != null && path.length > 0) {
            if (path.length > MAX_PATH_DEPTH) {
                throw new IllegalArgumentException("path length exceeds maximum of " + MAX_PATH_DEPTH);
            }
            this.path = path;
        }
    }
    public static BusinessPacket create(Way way, short... path) {
        return new BusinessPacket(way, path);
    }

    public final BusinessPacket attach(AbstractPayload<?> payload) {
        if (sealed) {
            throw new IllegalStateException("Cannot attach payload to a sealed packet.");
        }
        if (payload != null) {
            if (this.payloads == null) {
                this.payloads = new ArrayList<>();
            }
            if (payloadCount + 1 > MAX_PAYLOAD_COUNT) {
                throw new PayloadOverflowException(payloadCount, 1, MAX_PAYLOAD_COUNT);
            }
            this.payloads.add(payload);
        }
        return this;
    }
    public final BusinessPacket attach(AbstractPayload<?>... payloads) {
        if (sealed) {
            throw new IllegalStateException("Cannot attach payload to a sealed packet.");
        }
        if (payloads != null && payloads.length > 0) {
            if (this.payloads == null) {
                this.payloads = new ArrayList<>();
            }
            int newCount = payloads.length;
            if (payloadCount + newCount > MAX_PAYLOAD_COUNT) {
                throw new PayloadOverflowException(payloadCount, newCount, MAX_PAYLOAD_COUNT);
            }
            for (AbstractPayload<?> payload : payloads) {
                if (payload != null) {
                    this.payloads.add(payload);
                }
            }
        }
        return this;
    }
    public final BusinessPacket attach(Collection<AbstractPayload<?>> payloads) {
        if (sealed) {
            throw new IllegalStateException("Cannot attach payload to a sealed packet.");
        }
        if (payloads != null && !payloads.isEmpty()) {
            if (this.payloads == null) {
                this.payloads = new ArrayList<>();
            }
            int newCount = payloads.size();
            if (payloadCount + newCount > MAX_PAYLOAD_COUNT) {
                throw new PayloadOverflowException(payloadCount, newCount, MAX_PAYLOAD_COUNT);
            }
            for (AbstractPayload<?> payload : payloads) {
                if (payload != null) {
                    this.payloads.add(payload);
                }
            }
        }
        return this;
    }

    /**
     * 封存报文：执行最后的一次性大小计算，并禁止后续修改。
     */
    public final BusinessPacket seal() {
        if (sealed) {
            return this;
        }
        long packetSize = BASE_HEADER_SIZE;
        if (path != null) {
            pathDepth = (byte) path.length;
            packetSize += (long) pathDepth * Short.BYTES;
        } else {
            pathDepth = 0;
        }
        if (payloads != null && !payloads.isEmpty()) {
            int count = payloads.size();
            payloadCount = (byte) count;
            payloadsMeta = new long[count * 2];
            for (int i = 0; i < count; i++) {
                AbstractPayload<?> payload = payloads.get(i);
                long payloadSize = payload.getTotalSize();
                payloadsMeta[i * 2] = payload.getPayloadUID();
                payloadsMeta[i * 2 + 1] = payloadSize;
                packetSize += payloadSize;
            }
            packetSize += (long) payloadsMeta.length * Long.BYTES;
        } else {
            payloadCount = 0;
        }
        this.packetSize = packetSize;
        this.sealed = true;
        return this;
    }

    /**
     * 深度克隆报文结构，但保持 Payload 数据的引用。
     * 专门用于 pushToAll 场景。
     */
    public BusinessPacket duplicate() {
        if (!this.sealed) {
            throw new IllegalStateException("Only sealed packets can be duplicated for broadcast.");
        }
        BusinessPacket clone = new BusinessPacket(this.getWay(), this.path);
        clone.taskId = this.taskId;
        clone.packetSize = this.packetSize;
        clone.pathDepth = this.pathDepth;
        clone.payloadCount = this.payloadCount;
        clone.payloadsMeta = this.payloadsMeta;
        if (this.payloads != null) {
            clone.payloads = new ArrayList<>(this.payloadCount);
            for (AbstractPayload<?> p : this.payloads) {
                clone.payloads.add(p.duplicate());
            }
        }
        clone.sealed = true;
        return clone;
    }

    public final List<AbstractPayload<?>> payloads() {
        return payloads;
    }
    @SuppressWarnings("unchecked")
    public final <P extends AbstractPayload<?>> P payload(int index) {
        if (payloads == null || payloads.isEmpty() || index >= payloadCount) {
            return null;
        }
        return (P) payloads.get(index);
    }
    public final AbstractPayload<?> firstPayload() {
        if (payloads == null || payloads.isEmpty()) {
            return null;
        }
        return payloads.getFirst();
    }
    public final AbstractPayload<?> lastPayload() {
        if (payloads == null || payloads.isEmpty()) {
            return null;
        }
        return payloads.getLast();
    }
    public final Way getWay() {
        return WAYS[way];
    }

    public final int getHeaderSize() {
        return BASE_HEADER_SIZE + pathDepth * Short.BYTES + payloadCount * Long.BYTES * 2;
    }
    public final int getPacketId() {
        return packetId;
    }
    public final long getTaskId() {
        return taskId;
    }
    public final long getPacketSize() {
        return packetSize;
    }
    public final short getWayCode() {
        return way;
    }
    public final short[] getPath() {
        return path;
    }
    public final byte getPathDepth() {
        return pathDepth;
    }
    public final byte getPayloadCount() {
        return payloadCount;
    }
    public final long[] getPayloadsMeta() {
        return payloadsMeta;
    }
    public final long getTotalPayloadSize() {
        return getPacketSize() - getHeaderSize();
    }

    public final BusinessPacket setTaskId(long taskId) {
        this.taskId = taskId;
        return this;
    }

    @Override
    public final PacketType packetType() {
        return PacketType.BUSINESS;
    }

    public String getDisplayMessage() {
        return switch (firstPayload()) {
            case null -> "";
            case TextPayload tp -> tp.value();
            case Object other -> other.toString();
        };
    }

    @Override
    public final String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Way: ").append(WAYS[way]).append(", Path: ").append(Arrays.toString(path)).append(", TaskId: ").append(taskId)
                .append(", PacketSize: ").append(TransferSnapshot.formatSize(packetSize)).append(", PayloadCount: ").append(payloadCount);
        if (payloads != null) {
            sb.append(", Payloads: { ");
            for (AbstractPayload<?> payload : payloads) {
                sb.append(payload).append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
            sb.append(" }");
        }
        return sb.toString();
    }
}
