package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.server.lifecycle.handshake.PendingChannel;
import com.thezeroer.nexalithic.server.manager.SessionsManager;

import java.io.IOException;
import java.nio.channels.SelectionKey;

/**
 * 从属选择器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/06
 * @version 1.0.0
 */
public class WorkerLoop extends AbstractLoop {
    private static final int MAX_DRAIN_LIMIT = 64;
    private final SessionsManager sessionsManager;

    public WorkerLoop(OptionMap options, SessionsManager sessionsManager) throws IOException {
        super(options);
        this.sessionsManager = sessionsManager;
    }

    public void dispatch(PendingChannel pendingChannel) {

    }

    @Override
    public boolean onAsyncEvent() {

        return true;
    }

    @Override
    public void onReadyEvent(SelectionKey selectionKey) throws IOException {

    }

    @Override
    protected void onShuttingDown() {
        for (SelectionKey key : selector.keys()) {
            try {
                key.channel().close();
            } catch (IOException ignored) {}
        }
    }
}
