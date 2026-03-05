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
    private final EnumMap<AbstractPacket.PacketType, SessionChannel<? super AbstractPacket>> channels;
    private final long creationTime;
    private String sessionName;

    public NexalithicSession(SessionId sessionId, SessionSecretKey sessionSecretKey) {
        super(sessionSecretKey);
        this.sessionId = sessionId;
        this.channels = new EnumMap<>(AbstractPacket.PacketType.class);
        for (AbstractPacket.PacketType packetType : AbstractPacket.PacketType.values()) {
            channels.put(packetType, new SessionChannel<>(this, packetType));
        }
        this.creationTime = System.currentTimeMillis();
    }

    public NexalithicSession updateSelectionKey(AbstractPacket.PacketType packetType, SelectionKey selectionKey) {
        channels.get(packetType).updateSelectionKey(selectionKey);
        return this;
    }

    public void putSendQueue(AbstractPacket packet) {
        channels.get(packet.packetType()).put(packet);
    }

    public SessionChannel<? super AbstractPacket> getChannel(AbstractPacket.PacketType packetType) {
        return channels.get(packetType);
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
