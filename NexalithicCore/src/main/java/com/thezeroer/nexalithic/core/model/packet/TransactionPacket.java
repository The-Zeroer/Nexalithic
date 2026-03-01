package com.thezeroer.nexalithic.core.model.packet;

import com.thezeroer.nexalithic.core.model.Prioritizable;
import com.thezeroer.nexalithic.core.model.payload.AbstractPayload;

import java.util.ArrayList;

/**
 * 事务包
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
public final class TransactionPacket extends AbstractPacket<AbstractPayload<?>> implements Prioritizable {
    private byte suggestedPriority = PRIORITY_NORMAL;
    public static enum WAY {

    }
    public static class Metadata {
        private long taskId;
        private byte packetIndex;
        private long totalSize;
    }

    private TransactionPacket() {
        payloads = new ArrayList<>();
    }

    public static TransactionPacket build() {
        return new TransactionPacket();
    }

    @Override
    public TYPE getType() {
        return TYPE.TRANSACTION;
    }

    @Override
    public byte getSuggestedPriority() {
        return suggestedPriority;
    }

    /**
     * 设置建议优先级，小值优先
     *
     * @param suggestedPriority 建议优先级
     * @return {@link TransactionPacket }
     */
    public TransactionPacket setSuggestedPriority(byte suggestedPriority) {
        this.suggestedPriority = suggestedPriority;
        return this;
    }
}
