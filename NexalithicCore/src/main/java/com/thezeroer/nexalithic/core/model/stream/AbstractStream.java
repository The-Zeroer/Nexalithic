package com.thezeroer.nexalithic.core.model.stream;

import com.thezeroer.nexalithic.core.model.AbstractModel;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.stream.chunk.AbstractChunk;

/**
 * 抽象流
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/03
 * @version 1.0.0
 */
public abstract class AbstractStream<C extends AbstractChunk> extends AbstractModel {
    public enum StreamType {
        /** 媒体 */ MEDIA,
        /** 文件 */ FILE
    }

    /**
     * 获取流类型
     *
     * @return {@link AbstractPacket.PacketType }
     */
    public abstract StreamType streamType();
    public ModelType modelType() {
        return ModelType.Stream;
    }
}
