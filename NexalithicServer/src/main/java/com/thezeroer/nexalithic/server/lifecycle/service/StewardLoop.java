package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
import com.thezeroer.nexalithic.server.lifecycle.handshake.PendingChannel;
import com.thezeroer.nexalithic.server.manager.SessionsManager;
import org.jctools.queues.MpscArrayQueue;

import java.io.IOException;
import java.nio.channels.SelectionKey;

/**
 * 主选择器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/06
 * @version 1.0.0
 */
public class StewardLoop extends AbstractLoop {
    public static final NexalithicOption<Integer> DispatchQueue_Capacity = NexalithicOption.create("StewardLoop_DispatchQueue_Capacity", 1024);
    private static final int MAX_DRAIN_LIMIT = 64;
    private final MpscArrayQueue<PendingChannel> dispatchQueue;
    private final SessionsManager sessionsManager;

    public StewardLoop(OptionMap options, SessionsManager sessionsManager) throws IOException {
        super(options);
        this.sessionsManager = sessionsManager;
        dispatchQueue = new MpscArrayQueue<>(options.value(DispatchQueue_Capacity));
    }

    public void dispatch(PendingChannel pendingChannel) {
        if (dispatchQueue.offer(pendingChannel)) {
            loadScore.increment();
        } else {
            try {
                pendingChannel.getSocketChannel().close();
            } catch (IOException ignored) {}
            pendingChannel.recycle();
        }
    }

    @Override
    public boolean onAsyncEvent() {
        dispatchQueue.drain(pendingChannel -> {
            try {
                SelectionKey selectionKey = pendingChannel.getSocketChannel().configureBlocking(false).register(selector, SelectionKey.OP_READ);
                NexalithicSession session = new NexalithicSession(pendingChannel.getSessionId(), pendingChannel.getSessionSecretKey());
                selectionKey.attach(session.updateSelectionKey(AbstractPacket.TYPE.SIGNALING, selectionKey));
                sessionsManager.putSession(session);
            } catch (IOException ignored) {}
        }, MAX_DRAIN_LIMIT);
        return dispatchQueue.isEmpty();
    }

    @Override
    public void onReadyEvent(SelectionKey selectionKey) throws IOException {

    }

    @Override
    public void onTerminated() {

    }
}
