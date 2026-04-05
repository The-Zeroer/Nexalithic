package com.thezeroer.nexalithic.client.messaging;

import com.thezeroer.nexalithic.client.lifecycle.session.ClientSession;
import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.messaging.BusinessPacketDispatcher;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;

/**
 * 客户端业务分发器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/18
 * @version 1.0.0
 */
public class ClientBusinessPacketDispatcher extends BusinessPacketDispatcher<
        ClientSession,
        ClientHandlerContext,
        ClientHandlerContext.Recyclable
        > {
    public static final Options OPTIONS = OptionsDefinition.initOptions(Options.class, ClientBusinessPacketDispatcher.class);
    public static final class Options extends BusinessPacketDispatcher.Options {
        public Options(Class<?> holder) {
            super(holder);
        }
        protected Integer HandlerContextPool_Capacity_DefaultValue() {
            return 4;
        }
        protected Integer PacketWrapperPool_Capacity_DefaultValue() {
            return 64;
        }
    }

    public ClientBusinessPacketDispatcher(NexalithicBuilderContext context) {
        super(context, OPTIONS);
    }

    @Override
    protected ClientHandlerContext createHandlerContext() {
        return new ClientHandlerContext(this);
    }

    @Override
    protected ClientHandlerContext.Recyclable createRecyclableWrapper(ClientHandlerContext context) {
        return new ClientHandlerContext.Recyclable(context);
    }
}
