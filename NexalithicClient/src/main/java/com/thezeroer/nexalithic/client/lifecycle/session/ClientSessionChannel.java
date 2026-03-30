package com.thezeroer.nexalithic.client.lifecycle.session;

import com.thezeroer.nexalithic.client.lifecycle.GeneralLoop;
import com.thezeroer.nexalithic.core.io.codec.PacketsAssembler;
import com.thezeroer.nexalithic.core.io.codec.PacketsFragmenter;
import com.thezeroer.nexalithic.core.io.codec.wrapper.FragmentWrapper;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;

/**
 * 客户端会话通道
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/09
 * @version 1.0.0
 */
public class ClientSessionChannel<P extends AbstractPacket, W extends FragmentWrapper<P>> extends SessionChannel<P, W, ClientSession> {

    public ClientSessionChannel(AbstractPacket.PacketType packetType, ClientSession session, GeneralLoop loop, PacketsFragmenter<W> fragmenter, PacketsAssembler<P> assembler, SecretKeyContext context) {
        super(packetType, session, loop, fragmenter, assembler, context);
    }
}
