package com.thezeroer.nexalithic.core.messaging.handler;

import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.recyclable.TargetStaticWrapperPool;
import com.thezeroer.nexalithic.core.session.NexalithicSession;

/**
 * 处理器上下文
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/16
 * @version 1.0.0
 */
public class HandlerContext {
    protected NexalithicSession<?, ?, ?> session;
    protected BusinessPacket request;

    public HandlerContext() {}

    public final BusinessPacket getRequest() {
        return request;
    }

    public final boolean pushResponse(BusinessPacket response) {
        return session.pushBusinessPacket(response);
    }

    public static class Recyclable<
            T extends HandlerContext,
            W extends Recyclable<T, W>
        > extends TargetStaticWrapperPool.InteriorRecyclableWrapper<T, W> {
        public Recyclable(T target) {
            super(target);
        }

        @SuppressWarnings("unchecked")
        public W initTarget(BusinessPacket request, NexalithicSession<?, ?, ?> session) {
            target.request = request;
            target.session = session;
            return (W) this;
        }

        @Override
        protected void onRecycle(T target) {
            target.request = null;
            target.session = null;
        }
    }
}
