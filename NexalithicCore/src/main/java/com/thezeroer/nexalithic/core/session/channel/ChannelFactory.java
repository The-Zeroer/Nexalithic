package com.thezeroer.nexalithic.core.session.channel;

import com.thezeroer.nexalithic.core.io.codec.wrapper.FragmentWrapper;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.core.session.NexalithicSession;

/**
 * 通道工厂
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/03/28
 */
public interface ChannelFactory<
        S extends NexalithicSession<S, SC, BC, SW, BW>,
        SC extends SessionChannel<SignalingPacket, SW, S>,
        BC extends SessionChannel<BusinessPacket, BW, S>,
        SW extends FragmentWrapper<SignalingPacket>,
        BW extends FragmentWrapper<BusinessPacket>> {

    SC createSignalingChannel(S session, SecretKeyContext secretKeyContext);
    BC createBusinessChannel(S session, SecretKeyContext secretKeyContext);
}
