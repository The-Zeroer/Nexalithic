package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.io.loop.SessionLoop;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;
import com.thezeroer.nexalithic.server.lifecycle.handshake.PendingChannel;
import com.thezeroer.nexalithic.server.manager.SessionsManager;
import org.jctools.queues.MpscArrayQueue;

import java.io.IOException;
import java.nio.channels.SelectionKey;

/**
 * 服务 Loop
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/08
 * @version 1.0.0
 */
public abstract class ServiceLoop<P extends AbstractPacket> extends SessionLoop<P> {
    protected static final int MAX_DRAIN_LIMIT = 64;
    protected final MpscArrayQueue<PendingChannel> dispatchQueue;
    protected final SessionsManager sessionsManager;

    public ServiceLoop(OptionMap options, SessionsManager sessionsManager, MpscArrayQueue<PendingChannel> dispatchQueue) throws IOException {
        super(options);
        this.dispatchQueue = dispatchQueue;
        this.sessionsManager = sessionsManager;
    }

    public void dispatch(PendingChannel pendingChannel) {
        if (dispatchQueue.offer(pendingChannel)) {
            loadScore.increment();
            wakeupIfNeeded();
        } else {
            pendingChannel.close();
        }
    }

    public boolean pushPacket(SessionChannel<P> channel, P packet) {
        while (!channel.put(packet)) {
            Thread.onSpinWait();
        }
        if (channel.updateChannelInterest(SelectionKey.OP_WRITE, true)) {
            updateChannelInterest(channel);
        }
        wakeupIfNeeded();
        return true;
    }
}
