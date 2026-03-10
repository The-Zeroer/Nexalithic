package com.thezeroer.nexalithic.server.option;

import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.server.lifecycle.accept.AcceptorLoop;
import com.thezeroer.nexalithic.server.lifecycle.handshake.HandshakeLoop;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceUnit;
import com.thezeroer.nexalithic.server.lifecycle.service.StewardLoop;
import com.thezeroer.nexalithic.server.lifecycle.service.WorkerLoop;
import com.thezeroer.nexalithic.server.manager.SessionsManager;

/**
 * 服务器选项集
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/10
 * @version 1.0.0
 */
public class ServerOptionSet {
    public static final class AcceptorLoop_ {
        public static final NexalithicOption<Integer> FiltrationContextPool_Capacity = AcceptorLoop.FiltrationContextPool_Capacity;
        public static final NexalithicOption<Integer> FiltrationContextPool_Limit = AcceptorLoop.FiltrationContextPool_Limit;
        public static final NexalithicOption<Double> FiltrationContextPool_PrefillRatio = AcceptorLoop.FiltrationContextPool_PrefillRatio;
        public static final NexalithicOption<Integer> PendingChannelPool_Capacity = AcceptorLoop.PendingChannelPool_Capacity;
        public static final NexalithicOption<Integer> PendingChannelPool_Limit = AcceptorLoop.PendingChannelPool_Limit;
        public static final NexalithicOption<Double> PendingChannelPool_PrefillRatio = AcceptorLoop.PendingChannelPool_PrefillRatio;
    }
    public static final class HandshakeLoop_ {
        public static final NexalithicOption<Integer> Count = HandshakeLoop.Count;
        public static final NexalithicOption<Integer> DispatchQueue_Capacity = HandshakeLoop.DispatchQueue_Capacity;
    }
    public static final class ServiceUnit_ {
        public static final NexalithicOption<Integer> Count = ServiceUnit.Count;
        public static final NexalithicOption<Integer> WorkerLoop_Count = ServiceUnit.WorkerLoop_Count;
    }
    public static final class StewardLoop_ {
        public static final NexalithicOption<Integer> DispatchQueue_Capacity = StewardLoop.DispatchQueue_Capacity;
    }
    public static final class WorkerLoop_ {
        public static final NexalithicOption<Integer> DispatchQueue_Capacity = WorkerLoop.DispatchQueue_Capacity;
    }
    public static final class SessionManager_ {
        public static final NexalithicOption<Integer> Sessions_Initial_Capacity = SessionsManager.Sessions_Initial_Capacity;
        public static final NexalithicOption<Integer> Tokens_Initial_Capacity = SessionsManager.Tokens_Initial_Capacity;
    }
}
