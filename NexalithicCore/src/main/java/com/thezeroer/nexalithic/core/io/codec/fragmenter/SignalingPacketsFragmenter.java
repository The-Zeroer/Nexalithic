package com.thezeroer.nexalithic.core.io.codec.fragmenter;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import org.jctools.queues.MpscArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 信令分组碎片器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/10
 * @version 1.0.0
 */
public class SignalingPacketsFragmenter implements PacketsFragmenter<SignalingPacket> {
    public static final NexalithicOption<Integer> WrapperQueue_Capacity = NexalithicOption.create("SignalingPacketsFragmenter_WrapperQueue_Capacity", 256);
    private static final Logger logger = LoggerFactory.getLogger(SignalingPacketsFragmenter.class);
    private final MpscArrayQueue<SignalingPacket> packets = new MpscArrayQueue<>(Interior.WrapperQueue_Capacity);
    private SignalingPacket currentPacket;

    @Override
    public boolean feed(SignalingPacket wrapper) {
        return packets.offer(wrapper);
    }

    @Override
    public int fill(SignalingPacket... wrappers) {
        int count = wrappers.length;
        for (SignalingPacket wrapper : wrappers) {
            if (feed(wrapper)) {
                count--;
            } else {
                break;
            }
        }
        return count;
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
            if (logger.isTraceEnabled()) {
                logger.trace("[{}] sent SIGNALING packet", packet);
            }
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

    private static class Interior {
        public static final int WrapperQueue_Capacity = SignalingPacketsFragmenter.WrapperQueue_Capacity.value();
    }
}
