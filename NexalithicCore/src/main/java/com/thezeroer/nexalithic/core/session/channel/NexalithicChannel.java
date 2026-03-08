package com.thezeroer.nexalithic.core.session.channel;

import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;

/**
 * Nexalithic 通道
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/07
 * @version 1.0.0
 */
public abstract class NexalithicChannel<L extends AbstractLoop> {
    private volatile L loop;

    public NexalithicChannel<L> setLoop(L loop) {
        this.loop = loop;
        return this;
    }
    public L getLoop() {
        return loop;
    }

    public abstract void close();
}
