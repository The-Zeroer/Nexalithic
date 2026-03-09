package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.loadbalance.LoadBalanceable;
import com.thezeroer.nexalithic.core.loadbalance.LoadBalancer;
import com.thezeroer.nexalithic.core.loadbalance.P2CBalancer;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.core.session.SessionAttachment;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSessionChannel;
import com.thezeroer.nexalithic.server.manager.NetworkRouter;
import com.thezeroer.nexalithic.server.manager.SessionsManager;

import java.io.IOException;

/**
 * 服务单元
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/18
 */
public class ServiceUnit implements LoadBalanceable, SessionAttachment {
    public static final NexalithicOption<Integer> Count = NexalithicOption.create("ServiceUnit_Count", 1);
    public static final NexalithicOption<Integer> WorkerLoop_Count = NexalithicOption.create("ServiceUnit_WorkerLoop_Count", Runtime.getRuntime().availableProcessors());
    private final StewardLoop stewardLoop;
    private final WorkerLoop[] workerLoops;
    private final LoadBalancer<Void, WorkerLoop> workerLoopBalancer;

    public ServiceUnit(OptionMap options, SessionsManager sessionsManager, NetworkRouter networkRouter) throws IOException {
        stewardLoop = new StewardLoop(options, sessionsManager, networkRouter);
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

    public boolean pushBusinessPacket(ServerSession session, BusinessPacket<?> packet) {
        ServerSessionChannel<BusinessPacket<?>> channel = session.getBusinessChannel();
        ServiceLoop<ServerSessionChannel<BusinessPacket<?>>, BusinessPacket<?>> loop = channel.getServiceLoop();
        if (loop != null) {
            return loop.pushPacket(channel, packet);
        }
        return stewardLoop.becomeChannelConnecting(session.getSignalingChannel(), channel) && channel.put(packet);
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

    @Override
    public void clear() {
    }
}
