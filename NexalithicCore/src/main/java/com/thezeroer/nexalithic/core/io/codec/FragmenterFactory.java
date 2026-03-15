package com.thezeroer.nexalithic.core.io.codec;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.io.codec.wrapper.BusinessPacketFragmentWrapper;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.recyclable.PoolStorage;
import com.thezeroer.nexalithic.core.recyclable.PoolStrategy;
import com.thezeroer.nexalithic.core.recyclable.TargetDynamicWrapperPool;
import com.thezeroer.nexalithic.core.recyclable.WrapperPool;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.MpscArrayQueue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分片器工厂
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/10
 * @version 1.0.0
 */
public class FragmenterFactory {
    public static final NexalithicOption<Integer> WrapperPool_Capacity = NexalithicOption.create("BusinessPacketFragmenter_WrapperPool_Capacity", 4096);
    public static final NexalithicOption<Integer> WrapperQueue_Capacity = NexalithicOption.create("BusinessPacketFragmenter_WrapperQueue_Capacity", 64);


    @SuppressWarnings("unchecked")
    public static <P extends AbstractPacket> PacketsFragmenter<P> create(AbstractPacket.PacketType packetType) {
        return (PacketsFragmenter<P>) switch (packetType) {
            case SIGNALING -> new SignalingPacketsFragmenter();
            case BUSINESS -> new BusinessPacketsFragmenter();
        };
    }

    static class SignalingPacketsFragmenter implements PacketsFragmenter<SignalingPacket> {
        public static final int QUEUE_CAPACITY = 256;
        private final MpscArrayQueue<SignalingPacket> packets = new MpscArrayQueue<>(QUEUE_CAPACITY);
        private SignalingPacket currentPacket;

        @Override
        public boolean feed(SignalingPacket packet) {
            return packets.offer(packet);
        }

        @Override
        public boolean fill(SignalingPacket... packets) {
            int length = packets.length;
            if (QUEUE_CAPACITY - this.packets.size() < length * 4) {
                return false;
            }
            final int[] cursor = {0};
            int result = this.packets.fill(() -> packets[cursor[0]++], length);
            if (result != length) {
                throw new IllegalStateException(String.format(
                        "Nexalithic Fatal: Partial fill in MpscQueue! Expected %d, but only %d queued. Check concurrency or capacity.",
                        length, result
                ));
            }
            return true;
        }

        @Override
        public int drain(LoopBuffer target) {
            int total = 0;
            SignalingPacket packet = currentPacket;
            while (packet != null || !packets.isEmpty()) {
                if (packet == null) {
                    packet = packets.poll();
                    if (packet == null) {
                        return -1;
                    }
                }
                int totalRequired = packet.getTotalSize();
                if (target.writableBytes() < totalRequired) {
                    currentPacket = packet;
                    return total;
                }
                packet.unsafeToBuffer(target);
                packet = null;
                total += totalRequired;
            }
            return total;
        }

        @Override
        public boolean isEmpty() {
            return currentPacket == null && packets.isEmpty();
        }

        @Override
        public void clear() {
            packets.clear();
            currentPacket = null;
        }
    }

    static class BusinessPacketsFragmenter implements PacketsFragmenter<BusinessPacket> {
        public static final int MAX_LINKED_COUNT = 64;
        private static final WrapperPool<BusinessPacketFragmentWrapper> WRAPPER_POOL = new TargetDynamicWrapperPool<>(
            PoolStorage.of(new MpmcArrayQueue<>(WrapperPool_Capacity.value()), WrapperPool_Capacity.value()),
            PoolStrategy.alwaysCreate(),
            BusinessPacketFragmentWrapper::new
        );
        private final MpscArrayQueue<BusinessPacketFragmentWrapper> packets = new MpscArrayQueue<>(WrapperQueue_Capacity.value());
        private final AtomicInteger currentLinkedCount = new AtomicInteger(0);
        private BusinessPacketFragmentWrapper head, last;

        @Override
        public boolean feed(BusinessPacket packet) {
            if (currentLinkedCount.get() >= MAX_LINKED_COUNT) {
                return false;
            }
            return packets.offer((BusinessPacketFragmentWrapper) WRAPPER_POOL.acquire().wrap(packet));
        }

        @Override
        public boolean fill(BusinessPacket... p) {
            return false;
        }

        @Override
        public int drain(LoopBuffer target) throws IOException {
            int total = 0, written;
            BusinessPacketFragmentWrapper wrapper;
            while ((wrapper = packets.poll()) != null) {
                written = wrapper.nextFrame(target);
                total += written;
                if (wrapper.hasFrame()) {
                    if (head == null) {
                        head = wrapper;
                        last = wrapper;
                    }
                    head.setPrev(wrapper);
                    currentLinkedCount.incrementAndGet();
                } else {
                    wrapper.recycle();
                }
                if (written == 0) {
                    break;
                }
            }
            wrapper = last;
            while (wrapper != null) {
                written = wrapper.nextFrame(target);
                if (written == 0) {
                    break;
                }
                total += written;
                if (wrapper.hasFrame()) {
                    wrapper = wrapper.getNext();
                } else {
                    BusinessPacketFragmentWrapper next = wrapper.removeSelfAndGetNext();
                    currentLinkedCount.decrementAndGet();
                    if (next == null) {
                        wrapper.recycle();
                        head = null;
                        last = null;
                        break;
                    }
                    if (wrapper == head) {
                        head = next;
                    }
                    wrapper.recycle();
                    wrapper = next;
                }
                last = wrapper;
            }
            return total;
        }

        @Override
        public boolean isEmpty() {
            return packets.isEmpty() && currentLinkedCount.get() == 0;
        }

        @Override
        public void clear() {
            packets.clear();
            BusinessPacketFragmentWrapper wrapper = head;
            while (wrapper != null) {
                BusinessPacketFragmentWrapper next = wrapper.removeSelfAndGetNext();
                wrapper.recycle();
                wrapper = next;
            }
            currentLinkedCount.set(0);
            head = null;
            last = null;
        }
    }
}
