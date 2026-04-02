package com.thezeroer.nexalithic.core.io.codec.fragmenter;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionValidator;
import com.thezeroer.nexalithic.core.option.OptionsDefinition;
import org.jctools.queues.MpscArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务数据包碎片器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/10
 * @version 1.0.0
 */
public class BusinessPacketsFragmenter implements PacketsFragmenter<BusinessPacketFragmentWrapper> {
    public static final class Options implements OptionsDefinition {
        public static final NexalithicOption<Integer> WrapperQueue_Capacity = NexalithicOption.create(
                "BusinessPacketFragmenter_WrapperQueue_Capacity", 64, OptionValidator.positive()
        );
        public static final NexalithicOption<Integer> WrapperLinked_Capacity = NexalithicOption.create(
                "BusinessPacketFragmenter_WrapperQueue_Capacity", 64, OptionValidator.positive()
        );
    }
    private static final Logger logger = LoggerFactory.getLogger(BusinessPacketsFragmenter.class);
    private final MpscArrayQueue<BusinessPacketFragmentWrapper> packets = new MpscArrayQueue<>(Interior.WrapperQueue_Capacity);
    private final AtomicInteger currentLinkedCount = new AtomicInteger(0);
    private BusinessPacketFragmentWrapper head, last;

    @Override
    public boolean feed(BusinessPacketFragmentWrapper wrapper) {
        if (currentLinkedCount.get() >= Interior.WrapperLinked_Capacity) {
            return false;
        }
        return packets.offer(wrapper);
    }

    @Override
    public int fill(BusinessPacketFragmentWrapper... wrappers) {
        int count = wrappers.length;
        for (BusinessPacketFragmentWrapper wrapper : wrappers) {
            if (feed(wrapper)) {
                count--;
            } else {
                break;
            }
        }
        return count;
    }

    @Override
    public int drain(LoopBuffer target) throws IOException {
        int total = 0, written;
        BusinessPacketFragmentWrapper wrapper;
        while ((wrapper = packets.peek()) != null) {
            written = wrapper.firstFrame(target);
            if (written == 0) {
                break;
            }
            packets.poll();
            total += written;
            if (wrapper.hasFrame()) {
                if (head == null) {
                    head = wrapper;
                    last = wrapper;
                }
                head.setPrev(wrapper);
                currentLinkedCount.incrementAndGet();
            } else {
                if (logger.isTraceEnabled()) {
                    logger.trace("[{}] sent BUSINESS packet", wrapper.unwrap());
                }
                wrapper.recycle();
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
                if (logger.isTraceEnabled()) {
                    logger.trace("[{}] sent BUSINESS packet", wrapper.unwrap());
                }
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

    private static class Interior {
        public static final int WrapperQueue_Capacity = Options.WrapperQueue_Capacity.value();
        public static final int WrapperLinked_Capacity = Options.WrapperLinked_Capacity.value();
    }
}
