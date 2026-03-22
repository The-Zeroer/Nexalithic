package com.thezeroer.nexalithic.core.io.codec;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.io.codec.wrapper.FragmentWrapper;

import java.io.IOException;

/**
 * 包分片器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/03
 * @version 1.0.0
 */
public interface PacketsFragmenter<W extends FragmentWrapper<?>> {
    boolean feed(W wrapper);
    boolean fill(W... wrappers);
    int drain(LoopBuffer target) throws IOException;
    boolean isEmpty();
    void clear();
}
