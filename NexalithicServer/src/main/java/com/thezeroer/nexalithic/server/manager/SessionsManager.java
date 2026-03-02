package com.thezeroer.nexalithic.server.manager;

import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
import com.thezeroer.nexalithic.core.session.SessionId;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/06
 */
public class SessionsManager {
    public static final NexalithicOption<Integer> Initial_Capacity = NexalithicOption.create("SessionsManager_Initial_Capacity", 1024);
    private static final ThreadLocal<SessionId.Mutable> LOOKUP_KEY = ThreadLocal.withInitial(SessionId.Mutable::new);
    private final Map<SessionId, NexalithicSession> sessions;

    public SessionsManager(OptionMap options) {
        sessions = new ConcurrentHashMap<>(options.value(Initial_Capacity));
    }

    public void putSession(NexalithicSession session) {
        sessions.putIfAbsent(session.getSessionId(), session);
    }
    public NexalithicSession getSession(SessionId sessionId) {
        return sessions.get(sessionId);
    }
    public NexalithicSession getSession(byte[] rawSessionId) {
        return sessions.get(LOOKUP_KEY.get().wrap(rawSessionId));
    }
}
