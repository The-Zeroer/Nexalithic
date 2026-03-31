package com.thezeroer.nexalithic.core.io.codec.wrapper;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
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
    public static final int FRAME_HEADER_LENGTH = Short.BYTES + Long.BYTES;
    public static final int MAX_PAYLOAD_SIZE = 1024 * 16;
    private final TaskTracer taskTracer;
    private BusinessPacketFragmentWrapper prev;
    private BusinessPacketFragmentWrapper next;
    private boolean headerWritten;
    private long remaining;
    private List<? extends AbstractPayload<?>> payloads;
    private int payloadIndex;

    public BusinessPacketFragmentWrapper(TaskTracer taskTracer) {
        this.taskTracer = taskTracer;
    }

    @Override
    public void onWrap(BusinessPacket packet) {
        headerWritten = false;
        remaining = packet.getPacketSize();
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
    public int nextFrame(LoopBuffer output) throws IOException {
        int total = 0;
        int writable = output.writableBytes();
        int quota = (int) Math.min(Math.min(writable, MAX_PAYLOAD_SIZE), remaining);
        if (!headerWritten) {
            int headerSize = target.getHeaderSize();
            if (writable < FRAME_HEADER_LENGTH + headerSize) {
                return total;
            }
            writeFrameHeader(output, quota);
            writePacketHeader(output);
            total += headerSize;
            headerWritten = true;
        } else {
            if (writable <= FRAME_HEADER_LENGTH) {
                return total;
            }
            writeFrameHeader(output, quota);
        }
        int written, size = payloads.size();
        while (payloadIndex < size) {
            AbstractPayload<?> payload = payloads.get(payloadIndex);
            LoopBuffer.LimitedWritableView writableView = output.unsafeLimitedWritableView((int) (payload.getTotalSize() - payload.getProcessedSize()));
            try {
                if (payload.getProcessedSize() == 0) {
                    payload.prepareEncode();
                }
                written = payload.encode(writableView);
                total += written;
                if (payload.getProcessedSize() >= payload.getTotalSize()) {
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
        if (total < quota) {
            if (total == 0) {
                output.setTail(output.getTail() - FRAME_HEADER_LENGTH);
                return total;
            } else {
                long tail = output.getTail();
                output.resetTail();
                output.put((short) total);
                output.setTail(tail);
            }
        }
        remaining -= total;
        return total + FRAME_HEADER_LENGTH;
    }

    private void writeFrameHeader(LoopBuffer output, int payloadLength) {
        output.markTail();
        output.put((short) payloadLength);
        output.put(target.getPacketId());
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
