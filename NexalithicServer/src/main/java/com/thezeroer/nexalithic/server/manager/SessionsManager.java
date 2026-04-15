package com.thezeroer.nexalithic.server.manager;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.builder.option.NexalithicOption;
import com.thezeroer.nexalithic.core.builder.option.OptionValidator;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.session.SessionKey;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 会话管理器
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/06
 */
public class SessionsManager {
    public static final Options OPTIONS = OptionsDefinition.initOptions(Options.class, SessionsManager.class);
    public static final class Options extends OptionsDefinition {
        public final NexalithicOption<Integer> Sessions_Initial_Capacity = NexalithicOption.create(
                1024, OptionValidator.positive()
        );
        public final NexalithicOption<Integer> Tokens_Initial_Capacity = NexalithicOption.create(
                1024, OptionValidator.positive()
        );
        public final NexalithicOption<Integer> Sessions_Lock_Stripes = NexalithicOption.create(
                1024, OptionValidator.powerOfTwo()
        );
        public Options(Class<?> holder) {
            super(holder);
        }
    }
    private static final ThreadLocal<SessionKey.Mutable> LOOKUP_KEY = ThreadLocal.withInitial(SessionKey.Mutable::new);
    private final Map<SessionKey, ServerSession> idToSessions;
    private final Map<String, ServerSession> nameToSessions;
    private final Map<SessionKey, ServerSession> tokens;
    private final ReentrantLock[] stripes;

    public SessionsManager(NexalithicBuilderContext context) {
        idToSessions = new ConcurrentHashMap<>(context.getOption(OPTIONS.Sessions_Initial_Capacity));
        nameToSessions = new ConcurrentHashMap<>(context.getOption(OPTIONS.Sessions_Initial_Capacity));
        tokens = new ConcurrentHashMap<>(context.getOption(OPTIONS.Tokens_Initial_Capacity));
        stripes = new ReentrantLock[context.getOption(OPTIONS.Sessions_Lock_Stripes)];
        for (int i = 0; i < stripes.length; i++) {
            stripes[i] = new ReentrantLock();
        }
    }

    public void putSession(ServerSession session) {
        idToSessions.putIfAbsent(session.getSessionKey(), session);
    }

    /**
     * 抢占式设置：返回被抢占的Session
     */
    public ServerSession forceSetSessionName(String name, ServerSession session) {
        ReentrantLock lock = getLock(name);
        lock.lock();
        try {
            ServerSession existing = nameToSessions.put(name, session);
            if (existing != null) {
                idToSessions.remove(existing.getSessionKey());
            }
            session.setSessionName(name);
            return existing;
        } finally {
            lock.unlock();
        }
    }
    /**
     * 保护式设置：如果名字已存在，返回 false 且不修改现有状态
     */
    public boolean trySetSessionName(String name, ServerSession session) {
        ReentrantLock lock = getLock(name);
        lock.lock();
        try {
            if (nameToSessions.putIfAbsent(name, session) == null) {
                session.setSessionName(name);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public ServerSession getSession(SessionKey sessionKey) {
        return idToSessions.get(sessionKey);
    }
    public ServerSession getSession(String sessionName) {
        return nameToSessions.get(sessionName);
    }

    public void removeSession(ServerSession session) {
        if (session == null) {
            return;
        }
        idToSessions.remove(session.getSessionKey());
        String sessionName = session.getSessionName();
        if (sessionName != null) {
            ReentrantLock lock = getLock(sessionName);
            lock.lock();
            try {
                nameToSessions.remove(sessionName, session);
            } finally {
                lock.unlock();
            }
        }
    }
    public ServerSession removeSession(String sessionName) {
        ReentrantLock lock = getLock(sessionName);
        lock.lock();
        try {
            ServerSession session = nameToSessions.remove(sessionName);
            if (session == null) {
                return null;
            }
            idToSessions.remove(session.getSessionKey());
            return session;
        } finally {
            lock.unlock();
        }
    }

    public void relateChannelToken(SessionKey.Immutable sessionKey, ServerSession session) {
        tokens.put(sessionKey, session);
    }
    public ServerSession verifyAndConsumeToken(ByteBuffer buffer, int offset) {
        return tokens.remove(LOOKUP_KEY.get().wrap(buffer, offset));
    }

    private ReentrantLock getLock(String name) {
        int h = name.hashCode();
        h ^= (h >>> 16);
        return stripes[h & (stripes.length - 1)];
    }
}
