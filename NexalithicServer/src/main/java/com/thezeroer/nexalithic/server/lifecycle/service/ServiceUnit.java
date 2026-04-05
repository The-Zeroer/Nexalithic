package com.thezeroer.nexalithic.server.lifecycle.service;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.loadbalance.LoadBalanceable;
import com.thezeroer.nexalithic.core.loadbalance.LoadBalancer;
import com.thezeroer.nexalithic.core.loadbalance.P2CBalancer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.SignalingPacket;
import com.thezeroer.nexalithic.core.builder.option.NexalithicOption;
import com.thezeroer.nexalithic.core.builder.option.OptionValidator;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.session.SessionAttachment;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;
import com.thezeroer.nexalithic.server.NexalithicServer;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;
import com.thezeroer.nexalithic.server.manager.NetworkRouter;
import com.thezeroer.nexalithic.server.manager.SessionsManager;

import java.io.IOException;
import java.net.InetAddress;
import java.security.SecureRandom;

/**
 * 服务单元
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/18
 */
public class ServiceUnit implements LoadBalanceable, SessionAttachment {
    public static final Options OPTIONS = OptionsDefinition.initOptions(Options.class, ServiceUnit.class);
    public static final class Options extends OptionsDefinition {
        public final NexalithicOption<Integer> WorkerLoop_Count = NexalithicOption.create(
                Runtime.getRuntime().availableProcessors(), OptionValidator.positive()
        );

        public Options(Class<?> holder) {
            super(holder);
        }
    }
    private final StewardLoop stewardLoop;
    private final WorkerLoop[] workerLoops;
    private final LoadBalancer<Void, WorkerLoop> workerLoopBalancer;
    private final SecureRandom random = new SecureRandom();
    private final NetworkRouter router;
    private final SessionsManager manager;

    public ServiceUnit(NexalithicBuilderContext context) throws IOException {
        manager = context.getModule(NexalithicServer.Modules.SessionsManager);
        router = context.getModule(NexalithicServer.Modules.NetworkRouter);
        stewardLoop = new StewardLoop(context, this);
        workerLoops = new WorkerLoop[context.getOption(OPTIONS.WorkerLoop_Count)];
        for (int i = 0; i < workerLoops.length; i++) {
            workerLoops[i] = new WorkerLoop(context);
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

    public SignalingPacket[] prepareChannelAccess(ServerSession session, AbstractPacket.PacketType type, InetAddress remoteAddress) {
        byte[] channelToken = new byte[SessionChannel.CHANNEL_TOKEN_LENGTH];
        random.nextBytes(channelToken);
        manager.relateChannelToken(channelToken, session);
        return new SignalingPacket[] {
                new SignalingPacket(SignalingPacket.Signal.BusinessChannelToken, channelToken),
                new SignalingPacket(SignalingPacket.Signal.ResponseBusinessPort, AbstractPacket.intToBytes(
                        router.choosePort(type, remoteAddress))),
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
