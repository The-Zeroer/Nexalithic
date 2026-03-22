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
public abstract class HandlerContext<S extends NexalithicSession<?, ?, ?, ?, ?>> {
    protected S session;
    protected BusinessPacket request;

    public HandlerContext() {
    }

    public final BusinessPacket getRequest() {
        return request;
    }

    public abstract boolean pushResponse(BusinessPacket response);

    public static class Recyclable<
            S extends NexalithicSession<?, ?, ?, ?, ?>,
            T extends HandlerContext<S>,
            W extends Recyclable<S, T, W>
        > extends TargetStaticWrapperPool.InteriorRecyclableWrapper<T, W> {
        public Recyclable(T target) {
            super(target);
        }

        @SuppressWarnings("unchecked")
        public W initTarget(BusinessPacket request, S session) {
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
