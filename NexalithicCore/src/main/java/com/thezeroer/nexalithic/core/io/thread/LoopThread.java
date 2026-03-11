package com.thezeroer.nexalithic.core.io.thread;

import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;
import com.thezeroer.nexalithic.core.recyclable.ProxyRecycler;

/**
 * Loop的执行线程
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/03
 * @version 1.0.0
 */
public class LoopThread extends Thread {
    private ProxyRecycler<?> proxyRecycler;

    public LoopThread(AbstractLoop loop) {
        super(loop);
    }

    public void productionProxyRecycler(ProxyRecycler<?> proxyRecycler) {
        this.proxyRecycler = proxyRecycler;
    }
    @SuppressWarnings("unchecked")
    public <R extends ProxyRecycler<?>> R consumeProxyRecycler() {
        R r = (R) proxyRecycler;
        proxyRecycler = null;
        return r;
    }
}
