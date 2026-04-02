package com.thezeroer.nexalithic.server.lifecycle.service.session;

import com.thezeroer.nexalithic.core.io.codec.assembler.PacketsAssembler;
import com.thezeroer.nexalithic.core.io.codec.fragmenter.PacketsFragmenter;
import com.thezeroer.nexalithic.core.io.codec.fragmenter.FragmentWrapper;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;
import com.thezeroer.nexalithic.core.timer.Expirable;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceLoop;
import com.thezeroer.nexalithic.server.lifecycle.service.WorkerLoop;

/**
 * 服务器会话通道
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/09
 * @version 1.0.0
 */
public class ServerSessionChannel<P extends AbstractPacket, W extends FragmentWrapper<P>> extends SessionChannel<P, W, ServerSession> implements Expirable {

    public ServerSessionChannel(AbstractPacket.PacketType packetType, ServerSession session, ServiceLoop<P, W> loop, PacketsFragmenter<W> fragmenter, PacketsAssembler<P> assembler, SecretKeyContext context) {
        super(packetType, session, loop, fragmenter, assembler, context);
    }

    @Override
    public long getExpiryTime() {
        return lastActiveTime + Interior.MaxFreeTime;
    }

    @Override
    public boolean onExpiryTriggered() {
        return System.currentTimeMillis() - lastActiveTime > Interior.MaxFreeTime;
    }

    @Override
    public boolean isCancelled() {
        return lastActiveTime == -1;
    }

    private static class Interior {
        public static final long MaxFreeTime = WorkerLoop.MaxFreeTime.value();
    }
}
