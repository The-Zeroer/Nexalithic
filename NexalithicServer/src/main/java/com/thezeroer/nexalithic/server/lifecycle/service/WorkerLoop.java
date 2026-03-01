package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.server.lifecycle.handshake.PendingChannel;

import java.io.IOException;
import java.nio.channels.SelectionKey;

/**
 * 从属选择器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/06
 * @version 1.0.0
 */
public class WorkerLoop extends AbstractLoop<PendingChannel> {
    public WorkerLoop(OptionMap options) throws IOException {
    }

    @Override
    public void dispatch(PendingChannel pendingChannel) {

    }

    @Override
    public void onAsyncEvent() {

    }

    @Override
    public void onReadyEvent(SelectionKey selectionKey) throws IOException {

    }

    @Override
    public void onShutdown() {

    }
}
