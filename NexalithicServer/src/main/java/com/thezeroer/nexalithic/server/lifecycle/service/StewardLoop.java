package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;
import com.thezeroer.nexalithic.server.manager.NetworkRouter;
import com.thezeroer.nexalithic.server.manager.SessionsManager;
import org.jctools.queues.MpscArrayQueue;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;

/**
 * 主选择器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/06
 * @version 1.0.0
 */
public class StewardLoop extends ServiceLoop<SignalingPacket> {
    public static final NexalithicOption<Integer> DispatchQueue_Capacity = NexalithicOption.create("StewardLoop_DispatchQueue_Capacity", 1024);
    private final NetworkRouter networkRouter;
    private final ServiceUnit serviceUnit;
    private final SecureRandom secureRandom = new SecureRandom();

    public StewardLoop(OptionMap options, SessionsManager sessionsManager, NetworkRouter networkRouter, ServiceUnit serviceUnit) throws IOException {
        super(options, sessionsManager, new MpscArrayQueue<>(options.value(DispatchQueue_Capacity)));
        this.networkRouter = networkRouter;
        this.serviceUnit = serviceUnit;
    }

    public void pushPackets(SessionChannel<SignalingPacket> channel, SignalingPacket... packets) {
        for (int i = 0; i != packets.length; ++i) {
            while (!channel.put(packets[i])) {
                Thread.onSpinWait();
            }
        }
        if (channel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
            updateChannelInterest(channel);
        }
        wakeupIfNeeded();
    }

    @Override
    public boolean onAsyncEvent() {
        dispatchQueue.drain(channel -> {
            try {
                SelectionKey selectionKey = channel.getSocketChannel().configureBlocking(false).register(selector, SelectionKey.OP_READ);
                NexalithicSession session = new NexalithicSession(channel.getSessionId(), channel.getSessionSecretKey());
                selectionKey.attach(session.attachPrivate(serviceUnit).getSignalingChannel().updateSelectionKey(selectionKey).setLoop(this));
                sessionsManager.putSession(session);
            } catch (IOException ignored) {
            }
        }, MAX_DRAIN_LIMIT);
        return dispatchQueue.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onReadyEvent(SelectionKey key) throws IOException {
        SessionChannel<SignalingPacket> channel = (SessionChannel<SignalingPacket>) key.attachment();
        try {
            if (key.isReadable()) {
                if (channel.read() == -1) {
                    closeChannel(channel);
                }
                SignalingPacket packet;
                while ((packet = channel.get()) != null) {
                    handleSignalPacket(channel, packet);
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
            logger.warn("[{}] onReadyEvent Error", name, e);
            closeChannel(channel);
        }
    }
    private void handleSignalPacket(SessionChannel<SignalingPacket> channel, SignalingPacket packet) {
        switch (packet.getSignal()) {
            case SignalingPacket.Signal.HeartBeat -> {

            }
            case SignalingPacket.Signal.RequestBusinessPort -> becomeChannelConnecting(channel, channel.getSession().getBusinessChannel());
        }
    }

    @Override
    public void onTerminated() {

    }

    void becomeChannelConnecting(SessionChannel<SignalingPacket> signalingChannel, SessionChannel<?> targetChannel) {
        if (targetChannel.becomeConnecting()) {
            byte[] channelToken = new byte[SessionChannel.CHANNEL_TOKEN_LENGTH];
            secureRandom.nextBytes(channelToken);
            sessionsManager.relateChannelToken(channelToken, signalingChannel.getSession());
            pushPackets(signalingChannel, new SignalingPacket(SignalingPacket.Signal.BusinessChannelToken, channelToken),
                    new SignalingPacket(SignalingPacket.Signal.ResponseBusinessPort, AbstractPacket.intToBytes(networkRouter
                            .choosePort(AbstractPacket.PacketType.BUSINESS, signalingChannel.getRemoteAddress().getAddress()))));
        }
    }


    private void closeChannel(SessionChannel<?> channel) {
        channel.getSession().close();
        loadScore.decrement();
    }
}
