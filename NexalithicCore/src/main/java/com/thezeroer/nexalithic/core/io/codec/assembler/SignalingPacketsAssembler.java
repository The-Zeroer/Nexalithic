package com.thezeroer.nexalithic.core.io.codec.assembler;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import org.jctools.queues.SpscArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 信令包汇编器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/10
 * @version 1.0.0
 */
public class SignalingPacketsAssembler implements PacketsAssembler<SignalingPacket> {
    private static final Logger logger = LoggerFactory.getLogger(SignalingPacketsAssembler.class);
    private final SpscArrayQueue<SignalingPacket> packets = new SpscArrayQueue<>(128);
    private SignalingPacket pendingPacket;

    @Override
    public boolean feed(LoopBuffer source) {
        int flag = source.readableBytes();
        if (pendingPacket != null) {
            if (packets.offer(pendingPacket)) {
                pendingPacket = null;
            } else {
                return false;
            }
        }
        while (true) {
            int readable = source.readableBytes();
            if (readable < SignalingPacket.HEADER_LENGTH) {
                break;
            }
            source.markHead();
            byte signal = source.unsafeGetByte();
            short length = source.unsafeGetShort();
            if (source.readableBytes() < length) {
                source.resetHead();
                break;
            }
            byte[] content = new byte[length];
            source.unsafeGetBytes(content, length);
            SignalingPacket packet = new SignalingPacket(signal, content);
            if (logger.isTraceEnabled()) {
                logger.trace("[{}] received SIGNALING packet", packet);
            }
            if (packets.offer(packet)) {
                source.dropMarkHead();
            } else {
                this.pendingPacket = packet;
                break;
            }
        }
        return flag != source.readableBytes();
    }

    @Override
    public SignalingPacket drain() {
        if (pendingPacket != null && packets.offer(pendingPacket)) {
            pendingPacket = null;
        }
        return packets.poll();
    }

    @Override
    public void clear() {
        packets.clear();
        pendingPacket = null;
    }
}
