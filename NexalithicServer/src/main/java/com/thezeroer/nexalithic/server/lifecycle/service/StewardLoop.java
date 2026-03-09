package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSessionChannel;
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
public class StewardLoop extends ServiceLoop<ServerSessionChannel<SignalingPacket>, SignalingPacket> {
    public static final NexalithicOption<Integer> DispatchQueue_Capacity = NexalithicOption.create("StewardLoop_DispatchQueue_Capacity", 1024);
    private final NetworkRouter networkRouter;
    private final SecureRandom secureRandom = new SecureRandom();

    public StewardLoop(OptionMap options, SessionsManager sessionsManager, NetworkRouter networkRouter) throws IOException {
        super(options, sessionsManager, new MpscArrayQueue<>(options.value(DispatchQueue_Capacity)));
        this.networkRouter = networkRouter;
    }

    public boolean becomeChannelConnecting(ServerSessionChannel<SignalingPacket> signalingChannel, ServerSessionChannel<?> targetChannel) {
        if (targetChannel.becomeConnecting()) {
            byte[] channelToken = new byte[SessionChannel.CHANNEL_TOKEN_LENGTH];
            secureRandom.nextBytes(channelToken);
            sessionsManager.relateChannelToken(channelToken, signalingChannel.session());
            return pushPacket(signalingChannel, new SignalingPacket(SignalingPacket.Signal.BusinessChannelToken, channelToken),
                    new SignalingPacket(SignalingPacket.Signal.ResponseBusinessPort, AbstractPacket.intToBytes(networkRouter
                            .choosePort(AbstractPacket.PacketType.BUSINESS, signalingChannel.getRemoteAddress().getAddress()))));
        }
        return true;
    }

    @Override
    protected boolean onAsyncEvent() {
        dispatchQueue.drain(channel -> {
            try {
                SelectionKey selectionKey = channel.getSocketChannel().configureBlocking(false).register(selector, SelectionKey.OP_READ);
                ServerSession session = channel.getSession();
                selectionKey.attach(session.getSignalingChannel().updateSelectionKey(selectionKey));
                sessionsManager.putSession(session);
            } catch (IOException ignored) {
            }
        }, MAX_DRAIN_LIMIT);
        return dispatchQueue.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onReadyEvent(SelectionKey key) throws IOException {
        ServerSessionChannel<SignalingPacket> channel = (ServerSessionChannel<SignalingPacket>) key.attachment();
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
            logger.warn("ServerSessionChannel[{}] onReadyEvent[{}] error", channel, name, e);
            closeChannel(channel);
        }
    }

    private void handleSignalPacket(ServerSessionChannel<SignalingPacket> channel, SignalingPacket packet) {
        switch (packet.getSignal()) {
            case SignalingPacket.Signal.HeartBeat -> {

            }
            case SignalingPacket.Signal.RequestBusinessPort -> privateBecomeChannelConnecting(channel, channel.session().getBusinessChannel());
        }
    }
    private void privatePushPackets(ServerSessionChannel<SignalingPacket> channel, SignalingPacket... packets) {
        if (channel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
            channel.applyTargetInterest();
        }
        for (int i = 0; i != packets.length; ++i) {
            if (!channel.put(packets[i])) {
                logger.warn("ServerSessionChannel[{}] signalingPacket overflow", channel);
                closeChannel(channel);
            }
        }
    }
    private void privateBecomeChannelConnecting(ServerSessionChannel<SignalingPacket> signalingChannel, ServerSessionChannel<?> targetChannel) {
        if (targetChannel.becomeConnecting()) {
            byte[] channelToken = new byte[SessionChannel.CHANNEL_TOKEN_LENGTH];
            secureRandom.nextBytes(channelToken);
            sessionsManager.relateChannelToken(channelToken, signalingChannel.session());
            privatePushPackets(signalingChannel, new SignalingPacket(SignalingPacket.Signal.BusinessChannelToken, channelToken),
                    new SignalingPacket(SignalingPacket.Signal.ResponseBusinessPort, AbstractPacket.intToBytes(networkRouter
                            .choosePort(AbstractPacket.PacketType.BUSINESS, signalingChannel.getRemoteAddress().getAddress()))));
        }
    }

    private void closeChannel(ServerSessionChannel<?> channel) {
        ServerSession session = channel.session();
        sessionsManager.removeSession(session);
        loadScore.decrement();
        session.close();
    }
}
