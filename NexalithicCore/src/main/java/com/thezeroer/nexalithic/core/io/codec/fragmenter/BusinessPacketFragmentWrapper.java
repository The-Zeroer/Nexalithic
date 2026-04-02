package com.thezeroer.nexalithic.core.io.codec.fragmenter;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.io.codec.PacketFrame;
import com.thezeroer.nexalithic.core.messaging.task.TaskTracer;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.payload.AbstractPayload;
import com.thezeroer.nexalithic.core.recyclable.TargetDynamicWrapperPool;

import java.io.IOException;
import java.util.List;

/**
 * 业务数据包分段包装器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/11
 * @version 1.0.0
 */
public class BusinessPacketFragmentWrapper extends TargetDynamicWrapperPool.InteriorRecyclableWrapper<BusinessPacket, BusinessPacketFragmentWrapper> implements FragmentWrapper<BusinessPacket> {
    private final TaskTracer taskTracer;
    private BusinessPacketFragmentWrapper prev;
    private BusinessPacketFragmentWrapper next;
    private long remaining;
    private int packetId;
    private int payloadIndex;
    private List<? extends AbstractPayload<?>> payloads;

    public BusinessPacketFragmentWrapper(TaskTracer taskTracer) {
        this.taskTracer = taskTracer;
    }

    @Override
    public void onWrap(BusinessPacket packet) {
        remaining = packet.getPacketSize();
        packetId = packet.getPacketId();
        payloads = packet.payloads();
        payloadIndex = 0;
    }

    public boolean hasFrame() {
        if (remaining > 0) {
            return true;
        } else {
            taskTracer.activate(target.getTaskId());
            return false;
        }
    }
    public int firstFrame(LoopBuffer output) throws IOException {
        int writable = output.writableBytes();
        int headerSize = target.getHeaderSize();
        int offest = PacketFrame.FRAME_HEADER_LENGTH + headerSize;
        if (writable < offest) {
            return 0;
        }
        output.markTail();
        output.advanceTail(PacketFrame.FRAME_HEADER_LENGTH);
        writePacketHeader(output);
        int total = headerSize + writePayloads(output, Math.min(writable - offest, PacketFrame.MAX_PAYLOAD_LENGTH));
        if (total == 0) {
            output.resetTail();
            return 0;
        } else {
            long tail = output.getTail();
            output.resetTail();
            output.put(PacketFrame.pack(packetId, total, true));
            output.setTail(tail);
        }
        remaining -= total;
        return total + PacketFrame.FRAME_HEADER_LENGTH;
    }

    public int nextFrame(LoopBuffer output) throws IOException {
        int writable = output.writableBytes();
        if (writable <= PacketFrame.FRAME_HEADER_LENGTH) {
            return 0;
        }
        output.markTail();
        output.advanceTail(PacketFrame.FRAME_HEADER_LENGTH);
        int total = writePayloads(output, Math.min(writable - PacketFrame.FRAME_HEADER_LENGTH, PacketFrame.MAX_PAYLOAD_LENGTH));
        if (total == 0) {
            output.resetTail();
            return 0;
        } else {
            long tail = output.getTail();
            output.resetTail();
            output.put(PacketFrame.pack(packetId, total, false));
            output.setTail(tail);
        }
        remaining -= total;
        return total + PacketFrame.FRAME_HEADER_LENGTH;
    }

    private void writePacketHeader(LoopBuffer output) {
        output.put(target.getTaskId());
        output.put(target.getPacketSize());
        output.put(target.getWayCode());
        byte pathDepth = target.getPathDepth();
        output.put(pathDepth);
        if (pathDepth > 0) {
            for (short value : target.getPath()) {
                output.put(value);
            }
        }
        byte payloadCount = target.getPayloadCount();
        output.put(payloadCount);
        if (payloadCount > 0) {
            for (long value : target.getPayloadsMeta()) {
                output.put(value);
            }
        }
    }
    private int writePayloads(LoopBuffer output, int quota) throws IOException {
        int total = 0, written, size = payloads.size();
        while (payloadIndex < size) {
            AbstractPayload<?> payload = payloads.get(payloadIndex);
            long totalSize = payload.getTotalSize();
            long processedSize = payload.getProcessedSize();
            LoopBuffer.LimitedWritableView writableView = output.unsafeLimitedWritableView(Math.min((int) (totalSize - processedSize), quota - total));
            try {
                if (processedSize == 0) {
                    payload.prepareEncode();
                }
                written = payload.encode(writableView);
                total += written;
                if (payload.getProcessedSize() >= totalSize) {
                    payload.finishEncode();
                    payload.release();
                    payloadIndex++;
                }
            } catch (IOException e) {
                payload.release();
                throw e;
            }
            if (written == 0 || writableView.remaining() == 0) {
                break;
            }
        }
        return total;
    }

    public boolean hasNext() {
        return next != null;
    }
    public BusinessPacketFragmentWrapper getNext() {
        return next;
    }
    public BusinessPacketFragmentWrapper getPrev() {
        return prev;
    }

    public void setNext(BusinessPacketFragmentWrapper node) {
        BusinessPacketFragmentWrapper next = this.next;
        this.next = node;
        node.prev = this;
        if (next != null) {
            next.prev = node;
            node.next = next;
        }
        if (this.prev == null) {
            this.prev = node;
            node.next = this;
        }
    }
    public void setPrev(BusinessPacketFragmentWrapper node) {
        BusinessPacketFragmentWrapper prev = this.prev;
        this.prev = node;
        node.next = this;
        if (prev != null) {
            prev.next = node;
            node.prev = prev;
        }
        if (this.next == null) {
            this.next = node;
            node.prev = this;
        }
    }
    public BusinessPacketFragmentWrapper removeSelfAndGetNext() {
        if (next == this) {
            this.next = null;
            this.prev = null;
            return null;
        }
        if (next != null) {
            next.prev = prev;
        }
        if (prev != null) {
            prev.next = next;
        }
        BusinessPacketFragmentWrapper temp = next;
        this.next = null;
        this.prev = null;
        return temp;
    }

    @Override
    public void onRecycle() {
        prev = null;
        next = null;
    }
}
