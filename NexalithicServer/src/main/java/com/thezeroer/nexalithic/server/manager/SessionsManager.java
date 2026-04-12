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
        public Options(Class<?> holder) {
            super(holder);
        }
    }
    private static final ThreadLocal<SessionKey.Mutable> LOOKUP_KEY = ThreadLocal.withInitial(SessionKey.Mutable::new);
    private final Map<SessionKey, ServerSession> idToSessions;
    private final Map<String, ServerSession> nameToSessions;
    private final Map<SessionKey, ServerSession> tokens;

    public SessionsManager(NexalithicBuilderContext context) {
        idToSessions = new ConcurrentHashMap<>(context.getOption(OPTIONS.Sessions_Initial_Capacity));
        nameToSessions = new ConcurrentHashMap<>(context.getOption(OPTIONS.Sessions_Initial_Capacity));
        tokens = new ConcurrentHashMap<>(context.getOption(OPTIONS.Tokens_Initial_Capacity));
    }

    public void putSession(ServerSession session) {
        idToSessions.putIfAbsent(session.getSessionKey(), session);
    }
    public boolean setSessionName(String sessionName, ServerSession session) {
        if (nameToSessions.putIfAbsent(sessionName, session) != null) {
            return false;
        }
        session.setSessionName(sessionName);
        return true;
    }

    public ServerSession getSession(SessionKey sessionKey) {
        return idToSessions.get(sessionKey);
    }
    public ServerSession getSession(String sessionName) {
        return nameToSessions.get(sessionName);
    }

    public void removeSession(ServerSession session) {
        idToSessions.remove(session.getSessionKey());
        String sessionName = session.getSessionName();
        if (sessionName != null) {
            nameToSessions.remove(sessionName);
        }
    }
    public void removeSession(String sessionName) {
        ServerSession session = nameToSessions.remove(sessionName);
        if (session != null) {
            idToSessions.remove(session.getSessionKey());
        }
    }

    public void relateChannelToken(SessionKey.Immutable sessionKey, ServerSession session) {
        tokens.put(sessionKey, session);
    }
    public ServerSession verifyAndConsumeToken(ByteBuffer buffer, int offset) {
        return tokens.remove(LOOKUP_KEY.get().wrap(buffer, offset));
    }
}
