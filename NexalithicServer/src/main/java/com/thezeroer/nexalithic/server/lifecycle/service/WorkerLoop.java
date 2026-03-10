package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSessionChannel;
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
public class WorkerLoop extends ServiceLoop<ServerSessionChannel<BusinessPacket<?>>, BusinessPacket<?>> {
    public static final NexalithicOption<Integer> DispatchQueue_Capacity = NexalithicOption.create("WorkerLoop_DispatchQueue_Capacity", 1024);

    public WorkerLoop(SessionsManager sessionsManager) throws IOException {
        super(sessionsManager, new MpscArrayQueue<>(DispatchQueue_Capacity.value()));
    }

    @Override
    protected boolean onAsyncEvent() {
        dispatchQueue.drain(channel -> {
            try {
                SelectionKey selectionKey = channel.getSocketChannel().configureBlocking(false).register(selector, SelectionKey.OP_READ);
                ServerSessionChannel<BusinessPacket<?>> businessChannel = channel.getSession().getBusinessChannel();
                businessChannel.updateSelectionKey(selectionKey);
                selectionKey.attach(businessChannel.setServiceLoop(this));
                if (!businessChannel.fragmenterIsEmpty() && businessChannel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
                    businessChannel.applyTargetInterest();
                }
            } catch (IOException ignored) {
            }
        }, MAX_DRAIN_LIMIT);
        return dispatchQueue.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onReadyEvent(SelectionKey selectionKey) throws IOException {
        ServerSessionChannel<BusinessPacket<?>> channel = (ServerSessionChannel<BusinessPacket<?>>) selectionKey.attachment();
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
