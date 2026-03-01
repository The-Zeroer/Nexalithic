package com.thezeroer.nexalithic.core.io.loop;

import com.thezeroer.nexalithic.core.loadbalance.LoadBalanceable;
import com.thezeroer.nexalithic.core.session.SessionChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 抽象选择器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/06
 * @version 1.0.0
 */
public abstract class AbstractLoop<T> implements LoadBalanceable, Runnable {
    protected static final Logger logger = LoggerFactory.getLogger(AbstractLoop.class);
    protected static final int TIMEOUT = 3000;
    protected static final int MAX_EPOLL = 512;
    protected static final int STATE_NEW = 0;
    protected static final int STATE_WORKING = 1;
    protected static final int STATE_WAITING = 2;
    protected static final int STATE_SHUTDOWN = -1;
    protected static final int STATE_TERMINATED = -2;
    protected final AtomicInteger state = new AtomicInteger(STATE_NEW);
    protected final LongAdder loadScore = new LongAdder();
    protected volatile Selector selector;

    protected String name = getClass().getSimpleName();
    protected final Thread thread = new Thread(this);

    public AbstractLoop() throws IOException {
        selector = Selector.open();
        thread.setDaemon(false);
    }

    public void start() throws Exception {
        if (state.get() != STATE_NEW) {
            throw new IllegalStateException("Selector already started");
        }
        thread.setName(name);
        thread.start();
    }

    public void stop() throws Exception {
        if (state.get() != STATE_TERMINATED) {
            state.set(STATE_SHUTDOWN);
            selector.wakeup();
        }
    }

    public void shutdown() throws Exception {
        if (state.get() != STATE_TERMINATED) {
            state.set(STATE_SHUTDOWN);
            selector.wakeup();
        }
    }

    /**
     * 需要时叫醒，只有当 Selector 真的在睡觉（WAITING）时，才将其叫醒并设为 WORKING
     *
     */
    public void wakeupIfNeeded() {
        if (state.compareAndSet(STATE_WAITING, STATE_WORKING)) {
            selector.wakeup();
        }
    }

    @Override
    public long getLoadScore() {
        return loadScore.sum();
    }

    public String getName() {
        return name;
    }
    public AbstractLoop<?> addIdToName(String id) {
        name = name + "-" + id;
        return this;
    }

    public abstract void dispatch(T t) throws Exception;
    protected abstract void onAsyncEvent();
    protected abstract void onReadyEvent(SelectionKey selectionKey) throws IOException;
    protected abstract void onShutdown();
    protected void onSelectorError(IOException error) {}

    @Override
    public void run() {
        Selector localSelector = this.selector;
        int selectCount = 0;
        try {
            logger.debug("[{}] Selector started", name);
            if (!state.compareAndSet(STATE_NEW, STATE_WORKING)) {
                return;
            }
            while (state.get() != STATE_SHUTDOWN) {
                try {
                    onAsyncEvent();
                    int readyCount;
                    long start = System.currentTimeMillis();
                    if (state.compareAndSet(STATE_WORKING, STATE_WAITING)) {
                        readyCount = localSelector.select(TIMEOUT);
                        state.compareAndSet(STATE_WAITING, STATE_WORKING);
                    } else {
                        readyCount = localSelector.selectNow();
                    }
                    long end = System.currentTimeMillis();
                    if (readyCount > 0) {
                        selectCount = 0;
                        Iterator<SelectionKey> iterator = localSelector.selectedKeys().iterator();
                        while (iterator.hasNext()) {
                            SelectionKey key = iterator.next();
                            iterator.remove();
                            try {
                                onReadyEvent(key);
                            } catch (IOException e) {
                                try {
                                    key.cancel();
                                    key.channel().close();
                                } catch (IOException ignored) {}
                            }
                        }
                    } else if (end - start < TIMEOUT / 2) {
                        selectCount++;
                    } else {
                        selectCount = 0;
                    }
                    if (selectCount > MAX_EPOLL) {
                        logger.warn("[{}] Epoll bug detected, rebuilding selector...", name);
                        localSelector = rebuildSelector();
                        selectCount = 0;
                    }
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                    onSelectorError(e);
                }
            }
            logger.debug("[{}] Selector stopped", name);
        } finally {
            state.set(STATE_TERMINATED);
            onShutdown();
            try {
                for (SelectionKey key : localSelector.keys()) {
                    try {
                        key.channel().close();
                    } catch (IOException e) {
                        logger.error("[{}] Error closing channel", name, e);
                    }
                }
                localSelector.close();
            } catch (IOException e) {
                logger.error("[{}] Error closing selector", name, e);
            }
        }
    }

    private Selector rebuildSelector() throws IOException {
        try {
            Selector newSelector = Selector.open();
            Selector oldSelector = this.selector;
            for (SelectionKey oldKey : oldSelector.keys()) {
                if (!oldKey.isValid()) continue;
                Object attachment = oldKey.attachment();
                try {
                    SelectionKey newKey = oldKey.channel().register(newSelector, oldKey.interestOps(), attachment);
                    if (attachment instanceof SessionChannel<?> sessionChannel) {
                        sessionChannel.updateSelectionKey(newKey);
                    }
                    oldKey.cancel();
                } catch (Exception e) {
                    logger.error("[{}] Failed to migrate key for channel", name, e);
                }
            }
            this.selector = newSelector;
            oldSelector.close();
            logger.info("[{}] Selector rebuild successfully completed", name);
            return this.selector;
        } catch (IOException e) {
            logger.error("[{}] Failed to rebuild selector", name, e);
            throw e;
        }
    }
}
