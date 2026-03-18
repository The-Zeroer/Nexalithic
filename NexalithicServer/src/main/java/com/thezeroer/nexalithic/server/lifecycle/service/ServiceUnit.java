package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.loadbalance.LoadBalanceable;
import com.thezeroer.nexalithic.core.loadbalance.LoadBalancer;
import com.thezeroer.nexalithic.core.loadbalance.P2CBalancer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.session.SessionAttachment;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSessionChannel;
import com.thezeroer.nexalithic.server.manager.NetworkRouter;
import com.thezeroer.nexalithic.server.manager.SessionsManager;
import com.thezeroer.nexalithic.server.messaging.ServerBusinessPacketDispatcher;

import java.io.IOException;
import java.security.SecureRandom;

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
    private final SecureRandom random = new SecureRandom();
    private final NetworkRouter router;
    private final SessionsManager manager;

    public ServiceUnit(SessionsManager manager, NetworkRouter router, ServerBusinessPacketDispatcher dispatcher) throws IOException {
        this.manager = manager;
        this.router = router;
        stewardLoop = new StewardLoop(manager, router);
        workerLoops = new WorkerLoop[WorkerLoop_Count.value()];
        for (int i = 0; i < workerLoops.length; i++) {
            workerLoops[i] = new WorkerLoop(manager, dispatcher);
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

    public ServiceUnit addIdToLoopName(String id) {
        stewardLoop.addIdToName(id);
        for (int i = 0; i < workerLoops.length; i++) {
            workerLoops[i].addIdToName(id + "-" + i);
        }
        return this;
    }

    public SignalingPacket[] prepareChannelAccess(ServerSessionChannel<?> channel) {
        byte[] channelToken = new byte[SessionChannel.CHANNEL_TOKEN_LENGTH];
        random.nextBytes(channelToken);
        manager.relateChannelToken(channelToken, channel.session());
        return new SignalingPacket[] {
                new SignalingPacket(SignalingPacket.Signal.BusinessChannelToken, channelToken),
                new SignalingPacket(SignalingPacket.Signal.ResponseBusinessPort, AbstractPacket.intToBytes(
                        router.choosePort(channel.getType(), channel.getRemoteAddress().getAddress()))),
        };
    }

    @Override
    public long getLoadScore() {
        return stewardLoop.getLoadScore();
    }

    @Override
    public void clear() {
    }
}
