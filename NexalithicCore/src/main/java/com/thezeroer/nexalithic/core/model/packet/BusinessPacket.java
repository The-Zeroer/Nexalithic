package com.thezeroer.nexalithic.core.model.packet;

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

    private long taskId;
    private long packetSize;
    private short way;
    private byte pathDepth;
    private short[] path;
    private byte payloadCount;
    private long[] payloadsMeta;
    private List<AbstractPayload<?>> payloads;

    private BusinessPacket() {}
    private BusinessPacket(Way way, short... path) {
        this.way = (short) way.ordinal();
        if (path != null && path.length > 0) {
            if (path.length > MAX_PATH_DEPTH) {
                throw new IllegalArgumentException("path length exceeds maximum of " + MAX_PATH_DEPTH);
            }
            this.path = path;
            this.pathDepth = (byte) path.length;
        }
        packetSize = BASE_HEADER_SIZE + pathDepth * Short.BYTES;
    }
    public static BusinessPacket build(Way way) {
        return new BusinessPacket(way);
    }
    public static BusinessPacket build(Way way, short... path) {
        return new BusinessPacket(way, path);
    }

    public final BusinessPacket attach(AbstractPayload<?>... payloads) {
        if (this.payloads == null) {
            this.payloads = new ArrayList<>();
        }
        if (payloads != null && payloads.length > 0) {
            int newCount = payloads.length;
            if (payloadCount + newCount > MAX_PAYLOAD_COUNT) {
                throw new PayloadOverflowException(payloadCount, newCount, MAX_PAYLOAD_COUNT);
            }
            long[] tmp = new long[(payloadCount + newCount) * 2];
            if (payloadsMeta != null) {
                System.arraycopy(payloadsMeta, 0, tmp, 0, payloadsMeta.length);
            }
            for (int i = 0; i < newCount; i++) {
                AbstractPayload<?> p = payloads[i];
                int index = payloadCount + i;
                tmp[index * 2] = p.getPayloadUID();
                tmp[index * 2 + 1] = p.getTotalSize();
                this.payloads.add(p);
                this.packetSize += (Long.BYTES * 2 + p.getTotalSize());
            }
            this.payloadsMeta = tmp;
            this.payloadCount = (byte) this.payloads.size();
        }
        return this;
    }
    public final BusinessPacket attach(AbstractPayload<?> payload) {
        if (this.payloads == null) {
            this.payloads = new ArrayList<>();
        }
        if (payload != null) {
            if (payloadCount + 1 > MAX_PAYLOAD_COUNT) {
                throw new PayloadOverflowException(payloadCount, 1, MAX_PAYLOAD_COUNT);
            }
            int index = payloadCount;
            this.payloads.add(payload);
            this.payloadCount = (byte) this.payloads.size();
            if (payloadsMeta == null) {
                payloadsMeta = new long[payloadCount * 2];
            } else {
                long[] tmp = new long[payloadCount * 2];
                System.arraycopy(payloadsMeta, 0, tmp, 0, payloadsMeta.length);
                payloadsMeta = tmp;
            }
            payloadsMeta[index * 2] = payload.getPayloadUID();
            payloadsMeta[index * 2 + 1] = payload.getTotalSize();
            this.packetSize += (Long.BYTES * 2 + payload.getTotalSize());
        }
        return this;
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
    public final long getTaskId() {
        return taskId;
    }

    @Override
    public final PacketType packetType() {
        return PacketType.BUSINESS;
    }

    public static class Builder {
        public long taskId;
        public long packetSize;
        public short way;
        public byte pathDepth;
        public short[] path;
        public byte payloadCount;
        public long[] payloadsMeta;
        public List<AbstractPayload<?>> payloads;

        public BusinessPacket build() {
            BusinessPacket packet = new BusinessPacket();
            packet.taskId = taskId;
            packet.packetSize = packetSize;
            packet.way = way;
            packet.pathDepth = pathDepth;
            packet.path = path;
            packet.payloadCount = payloadCount;
            packet.payloadsMeta = payloadsMeta;
            packet.payloads = payloads;
            return packet;
        }

        public void clear() {
            taskId = 0;
            packetSize = 0;
            way = 0;
            pathDepth = 0;
            path = null;
            payloadCount = 0;
            payloadsMeta = null;
            payloads = null;
        }
    }
}
