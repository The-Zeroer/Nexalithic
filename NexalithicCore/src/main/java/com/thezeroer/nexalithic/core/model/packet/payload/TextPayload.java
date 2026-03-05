package com.thezeroer.nexalithic.core.model.packet.payload;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;

/**
 * 文本有效载荷
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
public class TextPayload extends AbstractPayload<String> {
    @Override
    public int encode(LoopBuffer output) throws Exception {
        return 0;
    }

    @Override
    public int decode(LoopBuffer input) throws Exception {
        return 0;
    }

    @Override
    public long getPayloadUID() {
        return 1;
    }
}
