package com.thezeroer.nexalithic.core.model.packet.business.payload;

import com.thezeroer.nexalithic.core.infra.buffer.LoopBuffer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 文本有效载荷
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
public class TextPayload extends AbstractPayload<String> {
    public static final long UID = 1;
    private byte[] bytes;

    public TextPayload() {}
    public TextPayload(String text) {
        value = text;
        bytes = text.getBytes(StandardCharsets.UTF_8);
        totalSize = bytes.length;
    }

    @Override
    public int encode(LoopBuffer.LimitedWritableView output) {
        int length = (int) Math.min(output.remaining(), totalSize - processedSize);
        if (length <= 0) {
            return 0;
        }
        output.putBytes(bytes, (int) processedSize, length);
        processedSize += length;
        return length;
    }
    @Override
    public int decode(LoopBuffer.LimitedReadableView input) {
        int length = (int) Math.min(input.remaining(), totalSize - processedSize);
        if (length <= 0) {
            return 0;
        }
        input.getBytes(bytes, (int) processedSize, length);
        processedSize += length;
        return length;
    }

    @Override
    public void prepareDecode(long totalSize) throws IOException {
        super.prepareDecode(totalSize);
        bytes = new byte[(int) totalSize];
    }
    @Override
    public void finishDecode() {
        value = new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public long getPayloadUID() {
        return UID;
    }
}
