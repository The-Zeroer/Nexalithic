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
    public static final NexalithicOption<Integer> Sessions_Initial_Capacity = NexalithicOption.create("SessionsManager_Sessions_Initial_Capacity", 1024);
    public static final NexalithicOption<Integer> Tokens_Initial_Capacity = NexalithicOption.create("SessionsManager_Tokens_Sessions_Initial_Capacity", 1024);
    private static final ThreadLocal<SessionId.Mutable> LOOKUP_KEY = ThreadLocal.withInitial(SessionId.Mutable::new);
    private final Map<SessionId, NexalithicSession> idToSessions;
    private final Map<String, NexalithicSession> nameToSessions;
    private final Map<SessionId, NexalithicSession> tokens;

    public SessionsManager(OptionMap options) {
        idToSessions = new ConcurrentHashMap<>(options.value(Sessions_Initial_Capacity));
        nameToSessions = new ConcurrentHashMap<>(options.value(Tokens_Initial_Capacity));
        tokens = new ConcurrentHashMap<>(options.value(Tokens_Initial_Capacity));
    }

    public void putSession(NexalithicSession session) {
        idToSessions.putIfAbsent(session.getSessionId(), session);
    }
    public boolean setSessionName(String sessionName, NexalithicSession session) {
        if (nameToSessions.putIfAbsent(sessionName, session) != null) {
            return false;
        }
        session.setSessionName(sessionName);
        return true;
    }

    public NexalithicSession getSession(SessionId sessionId) {
        return idToSessions.get(sessionId);
    }
    public NexalithicSession getSession(String sessionName) {
        return nameToSessions.get(sessionName);
    }
    public NexalithicSession getSession(byte[] rawSessionId) {
        return idToSessions.get(LOOKUP_KEY.get().wrap(rawSessionId));
    }

    public void relateChannelToken(byte[] token, NexalithicSession session) {
        tokens.put(new SessionId.Immutable(token), session);
    }
    public NexalithicSession verifyAndConsumeToken(byte[] token) {
        return tokens.remove(LOOKUP_KEY.get().wrap(token));
    }
}
