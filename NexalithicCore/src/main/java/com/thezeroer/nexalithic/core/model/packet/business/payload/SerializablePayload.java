package com.thezeroer.nexalithic.core.model.packet.business.payload;

import com.thezeroer.nexalithic.core.infra.buffer.LoopBuffer;

import java.io.*;

/**
 * 可序列化有效载荷
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/31
 * @version 1.0.0
 */
public class SerializablePayload extends AbstractPayload<Serializable>{
    public static final long UID = 3;
    private byte[] bytes;

    public SerializablePayload() {}
    public SerializablePayload(Serializable serializable) throws IOException {
        value = serializable;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(serializable);
            bytes = bos.toByteArray();
            totalSize = bytes.length;
            if (totalSize > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Payload too large, maximum size is " + Integer.MAX_VALUE);
            }
        }
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
    public SerializablePayload duplicate() {
        SerializablePayload clone = new SerializablePayload();
        clone.value = this.value;
        clone.bytes = this.bytes;
        clone.totalSize = this.totalSize;
        clone.processedSize = 0;
        return clone;
    }

    @Override
    public void prepareDecode(long totalSize) throws IOException {
        super.prepareDecode(totalSize);
        bytes = new byte[Math.toIntExact(totalSize)];
    }

    @Override
    public void finishDecode() throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            try {
                value = (Serializable) ois.readObject();
            } catch (ClassNotFoundException e) {
                throw new IOException(e);
            }
        }
    }

    @Override
    public long getPayloadUID() {
        return UID;
    }
}
