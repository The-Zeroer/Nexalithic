package com.thezeroer.nexalithic.core.model.packet;

import com.thezeroer.nexalithic.core.model.AbstractModel;
import com.thezeroer.nexalithic.core.util.SteppedSequenceGenerator;

/**
 * 抽象包
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
public abstract class AbstractPacket extends AbstractModel {
    public static final int MAX_PAYLOAD_COUNT = Byte.MAX_VALUE;
    public enum PacketType {
        /** 信令包 */ SIGNALING,
        /** 业务包 */ BUSINESS,
    }

    private static final SteppedSequenceGenerator sequenceGenerator = new SteppedSequenceGenerator();
    protected long packetId;

    protected AbstractPacket() {
        packetId = sequenceGenerator.nextId();
    }

    public final long getPacketId() {
        return packetId;
    }

    /**
     * 获取包类型
     *
     * @return {@link PacketType }
     */
    public abstract PacketType packetType();
    public ModelType modelType() {
        return ModelType.Packet;
    }
}
