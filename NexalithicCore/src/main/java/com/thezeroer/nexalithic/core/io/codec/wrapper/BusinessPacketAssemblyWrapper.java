package com.thezeroer.nexalithic.core.io.codec.wrapper;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.messaging.payload.PayloadRegistry;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.payload.AbstractPayload;
import com.thezeroer.nexalithic.core.recyclable.SelfStaticWrapperPool;
import com.thezeroer.nexalithic.core.timer.Expirable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 业务包组装封装器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/15
 * @version 1.0.0
 */
public class BusinessPacketAssemblyWrapper extends SelfStaticWrapperPool.InteriorRecyclableWrapper<BusinessPacketAssemblyWrapper> implements AssemblyWrapper<BusinessPacket>, Expirable {
    private final PacketBuilder packetBuilder = new PacketBuilder();
    private final PayloadRegistry payloadRegistry;
    private BusinessPacket packet;
    private long packetId;
    private long remaining;
    private int payloadIndex;
    private boolean headerRead;
    private long lastActiveTime;

    public BusinessPacketAssemblyWrapper(PayloadRegistry payloadRegistry) {
        this.payloadRegistry = payloadRegistry;
    }

    public boolean hasFrame() {
        return remaining > 0;
    }

    public int onFrame(LoopBuffer.LimitedReadableView input) throws IOException {
        lastActiveTime = System.currentTimeMillis();
        int total = 0;
        if (!headerRead) {
            total += readPacketHeader(input);
            headerRead = true;
        }
        List<AbstractPayload<?>> payloads = packetBuilder.payloads;
        int read;
        while (input.remaining() > 0 && payloadIndex < packetBuilder.payloadCount) {
            AbstractPayload<?> payload = payloads.get(payloadIndex);
            if (payload.getProcessedSize() == 0) {
                payload.prepareDecode(packetBuilder.payloadsMeta[payloadIndex * 2 + 1]);
            }
            read = payload.decode(input);
            if (payload.getProcessedSize() == payload.getTotalSize()) {
                payload.finishDecode();
                payloadIndex++;
            }
            total += read;
        }
        remaining -= total;
        if (remaining == 0 && payloadIndex == packetBuilder.payloadCount) {
            packet = packetBuilder.build();
        }
        return total;
    }
    private int readPacketHeader(LoopBuffer.LimitedReadableView input) throws IOException {
        int read = BusinessPacket.BASE_HEADER_SIZE;
        packetBuilder.taskId = input.getLong();
        packetBuilder.packetSize = input.getLong();
        remaining = packetBuilder.packetSize;
        packetBuilder.way = input.getShort();
        byte depth = input.getByte();
        packetBuilder.pathDepth = depth;
        if (depth > 0) {
            packetBuilder.path = new short[depth];
            for (int i = 0; i < depth; i++) {
                packetBuilder.path[i] = input.getShort();
            }
            read += depth * Short.BYTES;
        }
        byte count = input.getByte();
        packetBuilder.payloadCount = count;
        if (count > 0) {
            packetBuilder.payloads = new ArrayList<>(count);
            packetBuilder.payloadsMeta = new long[count * 2];
            for (int i = 0; i < count; i++) {
                long uid = input.getLong();
                long size = input.getLong();
                AbstractPayload<?> payload = payloadRegistry.get(uid);
                if (payload == null) {
                    throw new IOException("Unknown Payload UID: " + uid);
                }
                packetBuilder.payloads.add(payload);
                packetBuilder.payloadsMeta[i * 2] = uid;
                packetBuilder.payloadsMeta[i * 2 + 1] = size;
            }
            read += count * Long.BYTES * 2;
        }
        return read;
    }

    public BusinessPacketAssemblyWrapper setPacketId(long packetId) {
        this.packetId = packetId;
        return this;
    }
    public long getPacketId() {
        return packetId;
    }
    public long getLastActiveTime() {
        return lastActiveTime;
    }

    public BusinessPacket getPacket() {
        return packet;
    }

    @Override
    protected void onRecycle() {
        packetBuilder.clear();
        packet = null;
        packetId = 0;
        lastActiveTime = 0;
        headerRead = false;
        payloadIndex = 0;
    }

    @Override
    public long getExpiryTime() {
        return 0;
    }

    @Override
    public boolean onExpiryTriggered() {
        return true;
    }

    private static class PacketBuilder {
        private static final BusinessPacket.Way[] WAYS = BusinessPacket.Way.values();
        public long taskId;
        public long packetSize;
        public short way;
        public byte pathDepth;
        public short[] path;
        public byte payloadCount;
        public long[] payloadsMeta;
        public List<AbstractPayload<?>> payloads;

        public BusinessPacket build() {
            BusinessPacket packet = BusinessPacket.create(WAYS[way], path).attach(payloads).setTaskId(taskId).seal();
            if (packetSize != packet.getPacketSize()) {
                throw new IllegalStateException("Packet Size Mismatch");
            }
            for (int i = 0; i < payloadsMeta.length; i++) {
                long[] payloadsMeta = packet.getPayloadsMeta();
                if (this.payloadsMeta[i] != payloadsMeta[i]) {
                    throw new IllegalStateException("Payloads Meta Mismatch");
                }
            }
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

    private static class Config {
        public static final long ExpiryTime = 0;
    }
}
