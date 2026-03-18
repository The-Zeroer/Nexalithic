package com.thezeroer.nexalithic.core.messaging.handler;

import com.thezeroer.nexalithic.core.io.loop.ChannelLoop;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.recyclable.TargetStaticWrapperPool;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;

/**
 * 处理器上下文
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/16
 * @version 1.0.0
 */
public class HandlerContext<
        P extends BusinessPacket,
        C extends SessionChannel<P, ?, L>,
        L extends ChannelLoop<? super C, ? super P>
    > {
    protected P request;
    protected C channel;

    public HandlerContext() {}

    public final P getRequest() {
        return request;
    }

    public final boolean pushResponse(P response) {
        return channel.localLoop().pushPacket(channel, response);
    }

    public static class Recyclable<
            P extends BusinessPacket,
            C extends SessionChannel<P, ?, L>,
            L extends ChannelLoop<? super C, ? super P>,
            T extends HandlerContext<P, C, L>,
            W extends Recyclable<P, C, L, T, W>
        > extends TargetStaticWrapperPool.InteriorRecyclableWrapper<T, W> {
        public Recyclable(T target) {
            super(target);
        }

        @SuppressWarnings("unchecked")
        public W initTarget(P request, C channel) {
            target.request = request;
            target.channel = channel;
            return (W) this;
        }

        @Override
        protected void onRecycle(T target) {
            target.request = null;
            target.channel = null;
        }
    }
}
