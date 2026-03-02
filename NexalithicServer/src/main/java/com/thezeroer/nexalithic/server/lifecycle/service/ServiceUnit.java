package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.loadbalance.LoadBalanceable;
import com.thezeroer.nexalithic.core.loadbalance.LoadBalancer;
import com.thezeroer.nexalithic.core.loadbalance.P2CBalancer;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.server.manager.SessionsManager;

/**
 * 服务单元
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/18
 */
public class ServiceUnit implements LoadBalanceable {
    public static final NexalithicOption<Integer> Count = NexalithicOption.create("ServiceUnit_Count", 1);
    public static final NexalithicOption<Integer> WorkerLoop_Count = NexalithicOption.create("ServiceUnit_WorkerLoop_Count", Runtime.getRuntime().availableProcessors());
    private final StewardLoop stewardLoop;
    private final WorkerLoop[] workerLoops;
    private final LoadBalancer<Void, WorkerLoop> workerLoopBalancer;

    public ServiceUnit(OptionMap options, SessionsManager sessionsManager) throws Exception {
        stewardLoop = new StewardLoop(options, sessionsManager);
        workerLoops = new WorkerLoop[options.value(WorkerLoop_Count)];
        for (int i = 0; i < workerLoops.length; i++) {
            workerLoops[i] = new WorkerLoop(options, sessionsManager);
        }
        workerLoopBalancer = new P2CBalancer<>(workerLoops);
    }

    public StewardLoop getStewardLoop() {
        return stewardLoop;
    }
    public WorkerLoop selectWorkerLoop() {
        return workerLoopBalancer.select(null);
    }
    public WorkerLoop[] getWorkerLoops() {
        return workerLoops;
    }

    @Override
    public long getLoadScore() {
        return stewardLoop.getLoadScore();
    }

    public ServiceUnit addIdToLoopName(String id) {
        stewardLoop.addIdToName(id);
        for (int i = 0; i < workerLoops.length; i++) {
            workerLoops[i].addIdToName(id + "-" + i);
        }
        return this;
    }
}
