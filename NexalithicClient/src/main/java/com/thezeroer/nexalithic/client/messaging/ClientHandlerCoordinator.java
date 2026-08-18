package com.thezeroer.nexalithic.client.messaging;

import com.thezeroer.nexalithic.client.lifecycle.session.ClientSession;
import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerCoordinator;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;

/**
 * 客户端业务分发器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/18
 * @version 1.0.0
 */
public class ClientHandlerCoordinator extends HandlerCoordinator<
        ClientSession,
        ClientHandlerContext,
        ClientHandlerContext.Recyclable
        > {
    public static final Options OPTIONS = OptionsDefinition.initOptions(Options.class, ClientHandlerCoordinator.class);
    public static final class Options extends HandlerCoordinator.Options {
        public Options(Class<?> holder) {
            super(holder);
        }
        protected Integer HandlerContextPool_Capacity_DefaultValue() {
            return 4;
        }
    }

    public ClientHandlerCoordinator(NexalithicBuilderContext context) {
        super(context, OPTIONS, false);
        init(context, OPTIONS);
    }

    @Override
    protected ClientHandlerContext createHandlerContext() {
        return new ClientHandlerContext();
    }

    @Override
    protected ClientHandlerContext.Recyclable createRecyclableWrapper(ClientHandlerContext context) {
        return new ClientHandlerContext.Recyclable(context);
    }
}
