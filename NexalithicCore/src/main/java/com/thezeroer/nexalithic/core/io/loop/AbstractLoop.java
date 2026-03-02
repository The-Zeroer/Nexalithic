package com.thezeroer.nexalithic.core.io.loop;

import com.thezeroer.nexalithic.core.loadbalance.LoadBalanceable;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.core.session.SessionChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * 抽象循环
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/06
 * @version 1.0.0
 */
public abstract class AbstractLoop implements LoadBalanceable, Runnable {
    public static final NexalithicOption<Integer> Max_Shutdown_Wait = NexalithicOption.create("AbstractLoop_Max_Shutdown_Wait", 30000);
    protected enum State {
        NEW,
        STARTING,
        WORKING,
        WAITING,
        STOPPING,
        SHUTTING_DOWN,
        TERMINATED
    }
    protected static final Logger logger = LoggerFactory.getLogger(AbstractLoop.class);
    protected static final int TIMEOUT = 3000;
    protected static final int MAX_EPOLL = 512;
    protected final AtomicReference<State> state = new AtomicReference<>(State.NEW);
    protected final LongAdder loadScore = new LongAdder();
    protected volatile Selector selector;

    protected final Thread thread = new Thread(this);
    protected String name = getClass().getSimpleName();

    public AbstractLoop(OptionMap options) throws IOException {
        selector = Selector.open();
        thread.setDaemon(false);
        Max_Shutdown_Wait.set(options.value(Max_Shutdown_Wait));
    }

    public void start() throws Exception {
        synchronized (this) {
            if (!state.compareAndSet(State.NEW, State.STARTING)) {
                throw new IllegalStateException("Loop already [%s]".formatted(state.get()));
            }
            thread.setName(name);
            thread.start();
        }
    }

    public void stop() throws Exception {
        synchronized (this) {
            if (state.get() == State.STOPPING || state.get() == State.TERMINATED) {
                throw new IllegalStateException("Loop already [%s]".formatted(state.get()));
            }
            state.set(State.STOPPING);
            selector.wakeup();
        }
    }

    public void shutdown() throws Exception {
        synchronized (this) {
            if (state.get() == State.SHUTTING_DOWN || state.get() == State.STOPPING || state.get() == State.TERMINATED) {
                throw new IllegalStateException("Loop already [%s]".formatted(state.get()));
            }
            state.set(State.SHUTTING_DOWN);
            selector.wakeup();
        }
    }

    /**
     * 需要时叫醒，只有当 Selector 真的在睡觉（WAITING）时，才将其叫醒并设为 WORKING
     *
     */
    public void wakeupIfNeeded() {
        if (state.compareAndSet(State.WAITING, State.WORKING)) {
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
    public AbstractLoop addIdToName(String id) {
        name = name + "-" + id;
        return this;
    }

    protected abstract boolean onAsyncEvent();
    protected abstract void onReadyEvent(SelectionKey selectionKey) throws IOException;
    protected void onShuttingDown() {}
    protected void onTerminated() {}
    protected void onSelectorError(IOException error) {}

    @Override
    public void run() {
        Selector localSelector = this.selector;
        int emptyCount = 0, readyCount;
        long start = 0, end = 0;
        boolean running = true;
        state.compareAndSet(State.STARTING, State.WORKING);
        logger.debug("[{}] started", name);
        while (running) {
            switch (state.get()) {
                case WORKING -> {
                    try {
                        if (onAsyncEvent()) {
                            if (state.compareAndSet(State.WORKING, State.WAITING)) {
                                start = System.currentTimeMillis();
                                try {
                                    readyCount = localSelector.select(TIMEOUT);
                                } finally {
                                    state.compareAndSet(State.WAITING, State.WORKING);
                                }
                                end = System.currentTimeMillis();
                            } else {
                                continue;
                            }
                        } else {
                            readyCount = localSelector.selectNow();
                        }
                        if (readyCount > 0) {
                            emptyCount = 0;
                            readyEvent(localSelector);
                        } else if (start != 0) {
                            if (end - start < TIMEOUT / 2) {
                                if (++emptyCount > MAX_EPOLL) {
                                    logger.warn("[{}] Epoll bug detected, rebuilding selector...", name);
                                    localSelector = rebuildSelector();
                                    emptyCount = 0;
                                }
                            } else {
                                emptyCount = 0;
                            }
                            start = 0;
                            end = 0;
                        }
                    } catch (IOException e) {
                        logger.error(e.getMessage(), e);
                        onSelectorError(e);
                    }
                }
                case SHUTTING_DOWN -> {
                    if (start == 0) {
                        onShuttingDown();
                        start = System.currentTimeMillis();
                    }
                    try {
                        if (localSelector.selectNow() > 0) {
                            readyEvent(localSelector);
                        }
                    } catch (IOException ignored) {}
                    if (localSelector.keys().isEmpty()) {
                        try {
                            localSelector.close();
                        } catch (IOException e) {
                            logger.error("[{}] Error closing loop", name, e);
                        }
                        logger.debug("[{}] shutdown", name);
                    } else if (System.currentTimeMillis() - start > Max_Shutdown_Wait.get()) {
                        logger.warn("[{}] max shutdown time exceeded, forcing stop.", name);
                        state.set(State.STOPPING);
                    }
                }
                case STOPPING -> {
                    for (SelectionKey key : localSelector.keys()) {
                        try {
                            key.channel().close();
                        } catch (IOException ignored) {}
                    }
                    try {
                        localSelector.close();
                    } catch (IOException e) {
                        logger.error("[{}] Error closing loop", name, e);
                    }
                    logger.debug("[{}] stopped", name);
                    running = false;
                }
                default -> {
                    running = false;
                }
            }
        }
        onTerminated();
        state.set(State.TERMINATED);
    }
    private void readyEvent(Selector selector) throws IOException {
        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove();
            try {
                onReadyEvent(key);
            } catch (IOException e) {
                logger.warn("[{}] failed to read events", name, e);
                try {
                    key.cancel();
                    key.channel().close();
                } catch (IOException ignored) {}
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

    protected void closeSelectionKey(SelectionKey key) {
        try {
            key.cancel();
            key.channel().close();
        } catch (IOException ignored) {}
    }
}
