package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;
import com.thezeroer.nexalithic.server.manager.SessionsManager;
import org.jctools.queues.MpscArrayQueue;

import java.io.IOException;
import java.nio.channels.SelectionKey;

/**
 * 从属选择器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/06
 * @version 1.0.0
 */
public class WorkerLoop extends ServiceLoop<BusinessPacket<?>> {
    public static final NexalithicOption<Integer> DispatchQueue_Capacity = NexalithicOption.create("WorkerLoop_DispatchQueue_Capacity", 1024);
    private final StewardLoop stewardLoop;

    public WorkerLoop(OptionMap options, SessionsManager sessionsManager, StewardLoop stewardLoop) throws IOException {
        super(options, sessionsManager, new MpscArrayQueue<>(options.value(DispatchQueue_Capacity)));
        this.stewardLoop = stewardLoop;
    }

    @Override
    public boolean onAsyncEvent() {
        dispatchQueue.drain(channel -> {
            try {
                SelectionKey selectionKey = channel.getSocketChannel().configureBlocking(false).register(selector, SelectionKey.OP_READ);
                selectionKey.attach(channel.getSession().getBusinessChannel().updateSelectionKey(selectionKey).setLoop(this));
            } catch (IOException ignored) {
            }
        }, MAX_DRAIN_LIMIT);
        return dispatchQueue.isEmpty();
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
