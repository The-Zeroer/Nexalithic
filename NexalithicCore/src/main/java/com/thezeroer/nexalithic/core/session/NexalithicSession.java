package com.thezeroer.nexalithic.core.session;

import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.security.SecuritySession;
import com.thezeroer.nexalithic.core.security.SessionSecretKey;

import java.nio.channels.SelectionKey;
import java.util.EnumMap;

/**
 * Nexalithic会话
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
public class NexalithicSession extends SecuritySession {
    public static final int SESSION_ID_LENGTH = 32;
    private final SessionId sessionId;
    private final EnumMap<AbstractPacket.TYPE, SessionChannel<?>> channels;
    private final long creationTime;
    private String sessionName;

    public NexalithicSession(SessionId sessionId, SessionSecretKey sessionSecretKey) {
        super(sessionSecretKey);
        this.sessionId = sessionId;
        this.channels = new EnumMap<>(AbstractPacket.TYPE.class);
        for (AbstractPacket.TYPE type : AbstractPacket.TYPE.values()) {
            channels.put(type, new SessionChannel<>(this, type));
        }
        this.creationTime = System.currentTimeMillis();
    }

    public NexalithicSession updateSelectionKey(AbstractPacket.TYPE type, SelectionKey selectionKey) {
        channels.get(type).updateSelectionKey(selectionKey);
        return this;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }
    public String getSessionName() {
        return sessionName;
    }

    public SessionId getSessionId() {
        return sessionId;
    }
    public long getCreationTime() {
        return creationTime;
    }
}
