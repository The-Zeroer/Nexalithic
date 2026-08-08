package com.thezeroer.nexalithic.client.lifecycle;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.builder.module.ModulesDefinition;
import com.thezeroer.nexalithic.core.builder.module.NexalithicModule;
import com.thezeroer.nexalithic.core.lifecycle.LifecycleManager;

/**
 * 生命周期管理器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/04/14
 * @version 1.0.0
 */
public class ClientLifecycleManager extends LifecycleManager {
    public static final class Modules implements ModulesDefinition {
        public static final NexalithicModule<GeneralLoop> GeneralLoop = NexalithicModule.create("LifecycleManager_GeneralLoop", GeneralLoop.class);
    }

    private final GeneralLoop generalLoop;

    public ClientLifecycleManager(NexalithicBuilderContext context) {
        super("NexalithicClient");
        generalLoop = context.getModule(Modules.GeneralLoop);
    }

    @Override
    public void onStart() {
        generalLoop.start();
    }

    @Override
    public void onStop() {
        generalLoop.stop();
    }

    @Override
    public void onShutdown() {
        generalLoop.shutdown();
    }
}
