package com.thezeroer.nexalithic.core.io.codec.assembler;

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
    private long remaining;
    private int packetId;
    private int payloadIndex;
    private boolean headerRead;
    private long lastActiveTime;

    public BusinessPacketAssemblyWrapper(PayloadRegistry payloadRegistry) {
        this.payloadRegistry = payloadRegistry;
    }

    public boolean hasFrame() {
        return remaining > 0;
    }

    public int onFrame(LoopBuffer input, int quota, boolean isStartFrame) throws IOException {
        lastActiveTime = System.currentTimeMillis();
        int total = 0;
        if (!headerRead) {
            if (!isStartFrame) {
                input.advanceHead(quota);
                return quota;
            }
            total += readPacketHeader(input);
            headerRead = true;
        }
        List<AbstractPayload<?>> payloads = packetBuilder.payloads;
        int read;
        while (total < quota && payloadIndex < packetBuilder.payloadCount) {
            AbstractPayload<?> payload = payloads.get(payloadIndex);
            long totalSize = packetBuilder.payloadsMeta[payloadIndex * 2 + 1];
            long processedSize = payload.getProcessedSize();
            LoopBuffer.LimitedReadableView readableView = input.unsafeLimitedReadableView(
                    Math.min((int) (totalSize - processedSize), quota - total)
            );
            try {
                if (processedSize == 0) {
                    payload.prepareDecode(totalSize);
                }
                read = payload.decode(readableView);
                if (payload.getProcessedSize() >= totalSize) {
                    payload.finishDecode();
                    payload.release();
                    payloadIndex++;
                }
                total += read;
            } catch (IOException e) {
                payload.release();
                throw e;
            }
        }
        remaining -= total;
        if (remaining == 0) {
            packet = packetBuilder.build();
        }
        return total;
    }
    private int readPacketHeader(LoopBuffer input) throws IOException {
        int read = BusinessPacket.BASE_HEADER_SIZE;
        packetBuilder.taskId = input.unsafeGetLong();
        packetBuilder.packetSize = input.unsafeGetLong();
        remaining = packetBuilder.packetSize;
        packetBuilder.way = input.unsafeGetShort();
        byte depth = input.unsafeGetByte();
        packetBuilder.pathDepth = depth;
        if (depth > 0) {
            packetBuilder.path = new short[depth];
            for (int i = 0; i < depth; i++) {
                packetBuilder.path[i] = input.unsafeGetShort();
            }
            read += depth * Short.BYTES;
        }
        byte count = input.unsafeGetByte();
        packetBuilder.payloadCount = count;
        if (count > 0) {
            packetBuilder.payloads = new ArrayList<>(count);
            packetBuilder.payloadsMeta = new long[count * 2];
            for (int i = 0; i < count; i++) {
                long uid = input.unsafeGetLong();
                long size = input.unsafeGetLong();
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

    public BusinessPacketAssemblyWrapper setPacketId(int packetId) {
        this.packetId = packetId;
        return this;
    }
    public int getPacketId() {
        return packetId;
    }

    public BusinessPacket getPacket() {
        return packet;
    }

    @Override
    protected void onRecycle() {
        packetBuilder.clear();
        packet = null;
        packetId = 0;
        headerRead = false;
        payloadIndex = 0;
        lastActiveTime = -1;
    }

    @Override
    public long getExpiryTime() {
        return lastActiveTime + Interior.MaxWaitTime;
    }

    @Override
    public boolean onExpiryTriggered() {
        return System.currentTimeMillis() > lastActiveTime + Interior.MaxWaitTime;
    }

    @Override
    public boolean isCancelled() {
        return lastActiveTime == -1;
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
                throw new IllegalStateException("Packet Size Mismatch, %d or %d".formatted(packetSize, packet.getPacketSize()));
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

    private static class Interior {
        public static final long MaxWaitTime = BusinessPacketsAssembler.MaxWaitTime.value();
    }
}
