package com.thezeroer.nexalithic.core.session;

import java.util.Arrays;
import java.util.HexFormat;

/**
 * 会话唯一编码
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/02
 * @version 1.0.0
 */
public interface SessionId {
    int hashCode();
    boolean equals(Object o);
    byte[] getBytes();

    /**
     * 不可变版本：用于存储在 Map 的 Key 中
     */
    class Immutable implements SessionId {
        private final byte[] bytes;
        private final int hashCode;

        public Immutable(byte[] bytes) {
            this.bytes = bytes.clone();
            this.hashCode = Arrays.hashCode(this.bytes);
        }

        @Override
        public byte[] getBytes() {
            return bytes.clone();
        }

        byte[] internalBytes() {
            return bytes;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj instanceof Immutable other) {
                return Arrays.equals(this.bytes, other.bytes);
            }
            if (obj instanceof SessionId other) {
                return Arrays.equals(this.bytes, other.getBytes());
            }
            return false;
        }

        @Override
        public String toString() {
            return HexFormat.of().formatHex(bytes);
        }
    }

    /**
     * 可变版本：仅限单线程内查询使用（如 ThreadLocal）
     */
    class Mutable implements SessionId {
        private byte[] ref;
        private int cachedHashCode;

        public Mutable() {}

        public Mutable wrap(byte[] bytes) {
            this.ref = bytes;
            this.cachedHashCode = Arrays.hashCode(bytes);
            return this;
        }

        @Override
        public byte[] getBytes() {
            return ref;
        }

        @Override
        public int hashCode() {
            return cachedHashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj instanceof Immutable other) {
                return Arrays.equals(this.ref, other.internalBytes());
            }
            if (obj instanceof SessionId other) {
                return Arrays.equals(this.ref, other.getBytes());
            }
            return false;
        }

        @Override
        public String toString() {
            return ref == null ? "null" : HexFormat.of().formatHex(ref);
        }
    }
}
