package com.thezeroer.nexalithic.server.lifecycle;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.builder.module.ModulesDefinition;
import com.thezeroer.nexalithic.core.builder.module.NexalithicModule;
import com.thezeroer.nexalithic.core.builder.option.NexalithicOption;
import com.thezeroer.nexalithic.core.builder.option.OptionValidator;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.infra.loadbalance.LoadBalancer;
import com.thezeroer.nexalithic.server.lifecycle.accept.AcceptorLoop;
import com.thezeroer.nexalithic.server.lifecycle.handshake.HandshakeLoop;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceUnit;
import com.thezeroer.nexalithic.server.lifecycle.service.WorkerLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 生命周期管理器
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/19
 */
public class LifecycleManager extends com.thezeroer.nexalithic.core.lifecycle.LifecycleManager {
    public static final Options OPTIONS = OptionsDefinition.initOptions(Options.class, LifecycleManager.class);
    public static final class Options extends OptionsDefinition {
        public final NexalithicOption<Integer> HandshakeLoop_Count = NexalithicOption.create(
                1, OptionValidator.positive()
        );
        public final NexalithicOption<Integer> ServiceUnit_Count = NexalithicOption.create(
                1, OptionValidator.positive()
        );
        public Options(Class<?> holder) {
            super(holder);
        }
    }
    public static final class Modules implements ModulesDefinition {
        public static final NexalithicModule<AcceptorLoop> AcceptorLoop = NexalithicModule.create("LifecycleManager_AcceptorLoop", AcceptorLoop.class);
        public static final NexalithicModule<LoadBalancer<Void, HandshakeLoop>> HandshakeLoopLoadBalancer = NexalithicModule.create("LifecycleManager_HandshakeLoopLoadBalancer", LoadBalancer.class);
        public static final NexalithicModule<LoadBalancer<Void, ServiceUnit>> ServiceUnitLoadBalancer = NexalithicModule.create("LifecycleManager_ServiceUnitLoadBalancer", LoadBalancer.class);
    }

    private final AcceptorLoop acceptorLoop;
    private final LoadBalancer<Void, HandshakeLoop> handshakeLoopLoadBalancer;
    private final LoadBalancer<Void, ServiceUnit> serviceUnitLoadBalancer;

    public LifecycleManager(NexalithicBuilderContext context) {
        super("NexalithicServer");
        this.acceptorLoop = context.getModule(Modules.AcceptorLoop);
        this.handshakeLoopLoadBalancer = context.getModule(Modules.HandshakeLoopLoadBalancer);
        this.serviceUnitLoadBalancer = context.getModule(Modules.ServiceUnitLoadBalancer);
    }

    @Override
    public void onStart() {
        for (ServiceUnit serviceUnit : serviceUnitLoadBalancer.all()) {
            serviceUnit.getStewardLoop().start();
            for (WorkerLoop workerLoop : serviceUnit.getWorkerLoops()) {
                workerLoop.start();
            }
        }
        for (HandshakeLoop handshakeLoop : handshakeLoopLoadBalancer.all()) {
            handshakeLoop.start();
        }
        acceptorLoop.start();
    }

    @Override
    public void onStop() {
        acceptorLoop.stop();
        for (HandshakeLoop handshakeLoop : handshakeLoopLoadBalancer.all()) {
            handshakeLoop.stop();
        }
        for (ServiceUnit serviceUnit : serviceUnitLoadBalancer.all()) {
            serviceUnit.getStewardLoop().stop();
            for (WorkerLoop workerLoop : serviceUnit.getWorkerLoops()) {
                workerLoop.stop();
            }
        }
    }

    @Override
    public void onShutdown() {
        acceptorLoop.shutdown();
        for (HandshakeLoop handshakeLoop : handshakeLoopLoadBalancer.all()) {
            handshakeLoop.shutdown();
        }
        for (ServiceUnit serviceUnit : serviceUnitLoadBalancer.all()) {
            serviceUnit.getStewardLoop().shutdown();
            for (WorkerLoop workerLoop : serviceUnit.getWorkerLoops()) {
                workerLoop.shutdown();
            }
        }
    }

    public AcceptorLoop getAcceptorLoop() {
        return acceptorLoop;
    }
}
