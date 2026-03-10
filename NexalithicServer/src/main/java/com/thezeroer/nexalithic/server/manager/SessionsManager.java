package com.thezeroer.nexalithic.server.manager;

import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.session.SessionId;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;

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
    private final Map<SessionId, ServerSession> idToSessions;
    private final Map<String, ServerSession> nameToSessions;
    private final Map<SessionId, ServerSession> tokens;

    public SessionsManager() {
        idToSessions = new ConcurrentHashMap<>(Sessions_Initial_Capacity.value());
        nameToSessions = new ConcurrentHashMap<>(Tokens_Initial_Capacity.value());
        tokens = new ConcurrentHashMap<>(Tokens_Initial_Capacity.value());
    }

    public void putSession(ServerSession session) {
        idToSessions.putIfAbsent(session.getSessionId(), session);
    }
    public boolean setSessionName(String sessionName, ServerSession session) {
        if (nameToSessions.putIfAbsent(sessionName, session) != null) {
            return false;
        }
        session.setSessionName(sessionName);
        return true;
    }

    public ServerSession getSession(SessionId sessionId) {
        return idToSessions.get(sessionId);
    }
    public ServerSession getSession(String sessionName) {
        return nameToSessions.get(sessionName);
    }
    public ServerSession getSession(byte[] rawSessionId) {
        return idToSessions.get(LOOKUP_KEY.get().wrap(rawSessionId));
    }

    public void removeSession(ServerSession session) {
        idToSessions.remove(session.getSessionId());
        nameToSessions.remove(session.getSessionName());
    }
    public void removeSession(String sessionName) {
        ServerSession session = nameToSessions.remove(sessionName);
        if (session != null) {
            idToSessions.remove(session.getSessionId());
        }
    }

    public void relateChannelToken(byte[] token, ServerSession session) {
        tokens.put(new SessionId.Immutable(token), session);
    }
    public ServerSession verifyAndConsumeToken(byte[] token) {
        return tokens.remove(LOOKUP_KEY.get().wrap(token));
    }
}
