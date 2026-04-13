package com.thezeroer.nexalithic.core.messaging.handler;

import com.thezeroer.nexalithic.core.messaging.Dispatchable;
import com.thezeroer.nexalithic.core.model.packet.business.BusinessPacket;
import com.thezeroer.nexalithic.core.infra.recyclable.TargetStaticWrapperPool;
import com.thezeroer.nexalithic.core.session.NexalithicSession;

/**
 * 处理器上下文
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/16
 * @version 1.0.0
 */
public abstract class HandlerContext<S extends NexalithicSession<?, ?, ?, ?, ?>> {
    protected volatile S session;
    protected volatile BusinessPacket request;

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
        > extends TargetStaticWrapperPool.InteriorRecyclableWrapper<T, W> implements Dispatchable {

        private volatile NexalithicHandler<T> handler;

        public Recyclable(T target) {
            super(target);
        }

        public void initTarget(BusinessPacket request, S session, NexalithicHandler<T> handler) {
            target.request = request;
            target.session = session;
            this.handler = handler;
        }

        @Override
        protected void onRecycle() {
            target.request = null;
            target.session = null;
        }

        public S getSession() {
            return target.session;
        }
        public NexalithicHandler<T> getHandler() {
            return handler;
        }

        @Override
        public final Type type() {
            return Type.Handler;
        }
    }
}
