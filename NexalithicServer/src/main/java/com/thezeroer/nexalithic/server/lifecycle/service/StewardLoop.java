package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
import com.thezeroer.nexalithic.core.session.SessionChannel;
import com.thezeroer.nexalithic.server.lifecycle.handshake.PendingChannel;
import com.thezeroer.nexalithic.server.manager.NetworkRouter;
import com.thezeroer.nexalithic.server.manager.SessionsManager;
import org.jctools.queues.MpscArrayQueue;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

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
    private final NetworkRouter networkRouter;

    public StewardLoop(OptionMap options, SessionsManager sessionsManager, NetworkRouter networkRouter) throws IOException {
        super(options);
        this.sessionsManager = sessionsManager;
        this.networkRouter = networkRouter;
        dispatchQueue = new MpscArrayQueue<>(options.value(DispatchQueue_Capacity));
    }

    public void dispatch(PendingChannel pendingChannel) {
        if (dispatchQueue.offer(pendingChannel)) {
            loadScore.increment();
            wakeupIfNeeded();
        } else {
            try {
                pendingChannel.getSocketChannel().close();
            } catch (IOException ignored) {
            }
            pendingChannel.recycle();
        }
    }

    @SuppressWarnings("unchecked")
    public boolean pushPacket(SelectionKey selectionKey, SignalingPacket signalingPacket) {
        SessionChannel<SignalingPacket> sessionChannel = (SessionChannel<SignalingPacket>) selectionKey.attachment();
        if (!sessionChannel.put(signalingPacket)) {
            return false;
        }
        if (sessionChannel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
            updateChannelInterest(sessionChannel);
        }
        wakeupIfNeeded();
        return true;
    }

    @Override
    public boolean onAsyncEvent() {
        dispatchQueue.drain(pendingChannel -> {
            try {
                SelectionKey selectionKey = pendingChannel.getSocketChannel().configureBlocking(false).register(selector, SelectionKey.OP_READ);
                NexalithicSession session = new NexalithicSession(pendingChannel.getSessionId(), pendingChannel.getSessionSecretKey());
                selectionKey.attach(session.getChannel(AbstractPacket.PacketType.SIGNALING).updateSelectionKey(selectionKey));
                sessionsManager.putSession(session);
            } catch (IOException ignored) {
            }
        }, MAX_DRAIN_LIMIT);
        return dispatchQueue.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onReadyEvent(SelectionKey selectionKey) throws IOException {
        SessionChannel<SignalingPacket> sessionChannel = (SessionChannel<SignalingPacket>) selectionKey.attachment();
        try {
            if (selectionKey.isReadable()) {
                if (sessionChannel.read() == -1) {
                    closeSelectionKey(selectionKey);
                }
                SignalingPacket packet;
                while ((packet = sessionChannel.get()) != null) {
                    handleSignalPacket(packet);
                }
            } else if (selectionKey.isWritable()) {
                if (sessionChannel.write() == -1) {
                    selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
                }
            } else {
                closeSelectionKey(selectionKey);
            }
        } catch (InvalidAlgorithmParameterException | ShortBufferException | IllegalBlockSizeException |
                 BadPaddingException | InvalidKeyException e) {
            logger.warn("[{}] onReadyEvent Error", name, e);
            closeSelectionKey(selectionKey);
        }
    }
    private void handleSignalPacket(SignalingPacket packet) {
        switch (packet.getSignal()) {

        }
    }

    @Override
    public void onTerminated() {

    }

    @Override
    protected void closeSelectionKey(SelectionKey key) {
        super.closeSelectionKey(key);
        loadScore.decrement();
    }
}
