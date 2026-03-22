package com.thezeroer.nexalithic.core.io.codec.wrapper;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.payload.AbstractPayload;
import com.thezeroer.nexalithic.core.model.packet.payload.TextPayload;
import com.thezeroer.nexalithic.core.recyclable.SelfStaticWrapperPool;

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
public class BusinessPacketAssemblyWrapper extends SelfStaticWrapperPool.InteriorRecyclableWrapper<BusinessPacketAssemblyWrapper> {
    public static final int FRAME_HEADER_LENGTH = BusinessPacketFragmentWrapper.FRAME_HEADER_LENGTH;
    public static final int MAX_PAYLOAD_SIZE = BusinessPacketFragmentWrapper.MAX_PAYLOAD_SIZE;
    private final BusinessPacket.Builder packetBuilder = new BusinessPacket.Builder();
    private BusinessPacket packet;
    private long packetId;
    private long remaining;
    private int payloadIndex;
    private boolean headerRead;
    private long lastActiveTime;

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
        while (input.remaining() > 0) {
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
        if (remaining <= 0) {
            packet = packetBuilder.build();
        }
        return total;
    }
    private int readPacketHeader(LoopBuffer.LimitedReadableView input) {
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
            packetBuilder.payloadsMeta = new long[count *= 2];
            for (int i = 0; i < count;) {
                packetBuilder.payloads.add(new TextPayload());
                packetBuilder.payloadsMeta[i++] = input.getLong();
                packetBuilder.payloadsMeta[i++] = input.getLong();
            }
            read += count * Long.BYTES;
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
}
