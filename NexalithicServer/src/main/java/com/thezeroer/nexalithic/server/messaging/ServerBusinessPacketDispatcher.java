package com.thezeroer.nexalithic.server.messaging;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.messaging.BusinessPacketDispatcher;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.messaging.handler.NexalithicHandler;
import com.thezeroer.nexalithic.core.model.packet.business.BusinessPacket;
import com.thezeroer.nexalithic.server.NexalithicServer;
import com.thezeroer.nexalithic.server.lifecycle.LifecycleManager;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceUnit;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;
import com.thezeroer.nexalithic.server.manager.SessionsManager;

/**
 * 服务器业务分组器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/17
 * @version 1.0.0
 */
public class ServerBusinessPacketDispatcher extends BusinessPacketDispatcher<
        ServerSession,
        ServerHandlerContext,
        ServerHandlerContext.Recyclable
        > {
    public static final Options OPTIONS = OptionsDefinition.initOptions(Options.class, ServerBusinessPacketDispatcher.class);
    public static final class Options extends BusinessPacketDispatcher.Options {
        public Options(Class<?> holder) {
            super(holder);
        }
    }
    private final SessionsManager sessionsManager;

    public ServerBusinessPacketDispatcher(NexalithicBuilderContext context) {
        super(context, OPTIONS, context.getOption(LifecycleManager.OPTIONS.ServiceUnit_Count) != 1 || context.getOption(ServiceUnit.OPTIONS.WorkerLoop_Count) != 1);
        sessionsManager = context.getModule(NexalithicServer.Modules.SessionsManager);
        init(context, OPTIONS);
    }

    @Override
    protected ServerHandlerContext createHandlerContext() {
        return new ServerHandlerContext(this, sessionsManager);
    }

    @Override
    protected ServerHandlerContext.Recyclable createRecyclableWrapper(ServerHandlerContext context) {
        return new ServerHandlerContext.Recyclable(context);
    }
}
