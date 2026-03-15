package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSessionChannel;
import com.thezeroer.nexalithic.server.manager.SessionsManager;
import org.jctools.queues.MpscArrayQueue;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

/**
 * 从属选择器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/06
 * @version 1.0.0
 */
public class WorkerLoop extends ServiceLoop<ServerSessionChannel<BusinessPacket>, BusinessPacket> {
    public static final NexalithicOption<Integer> DispatchQueue_Capacity = NexalithicOption.create("WorkerLoop_DispatchQueue_Capacity", 1024);

    public WorkerLoop(SessionsManager sessionsManager) throws IOException {
        super(sessionsManager, new MpscArrayQueue<>(DispatchQueue_Capacity.value()));
    }

    @Override
    protected boolean onAsyncEvent() {
        dispatchQueue.drain(channel -> {
            try {
                SelectionKey selectionKey = channel.getSocketChannel().configureBlocking(false).register(selector, SelectionKey.OP_READ);
                ServerSessionChannel<BusinessPacket> businessChannel = channel.getSession().getBusinessChannel();
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
    protected void onReadyEvent(SelectionKey key) throws IOException {
        ServerSessionChannel<BusinessPacket> channel = (ServerSessionChannel<BusinessPacket>) key.attachment();
        try {
            if (key.isReadable()) {
                if (channel.read() == -1) {
                    closeChannel(channel);
                }
                BusinessPacket packet;
                while ((packet = channel.get()) != null) {
                    handleBusinessPacket(channel, packet);
                }
            } else if (key.isWritable()) {
                if (channel.write() == -1) {
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                }
            } else {
                closeChannel(channel);
            }
        } catch (InvalidAlgorithmParameterException | ShortBufferException | IllegalBlockSizeException |
                 BadPaddingException | InvalidKeyException e) {
            logger.warn("ServerSessionChannel[{}] onReadyEvent[{}] error", channel, name, e);
            closeChannel(channel);
        }
    }

    private void handleBusinessPacket(ServerSessionChannel<BusinessPacket> channel, BusinessPacket packet) {

    }

    private void closeChannel(ServerSessionChannel<?> channel) {
        loadScore.decrement();
        channel.close();
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
