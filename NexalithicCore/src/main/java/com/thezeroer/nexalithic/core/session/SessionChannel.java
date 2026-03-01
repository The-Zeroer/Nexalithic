package com.thezeroer.nexalithic.core.session;

import com.thezeroer.nexalithic.core.io.codec.AssemblerFactory;
import com.thezeroer.nexalithic.core.io.codec.FragmenterFactory;
import com.thezeroer.nexalithic.core.io.codec.PacketAssembler;
import com.thezeroer.nexalithic.core.io.codec.PacketFragmenter;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * 会话通道
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/04
 * @version 1.0.0
 */
public class SessionChannel<P extends AbstractPacket<?>> {
    private final NexalithicSession NexalithicSession;
    private final PacketFragmenter<P> fragmenter;
    private final PacketAssembler assembler;
    private volatile SelectionKey selectionKey;
    private volatile SocketChannel socketChannel;

    public SessionChannel(NexalithicSession NexalithicSession, AbstractPacket.TYPE type) {
        this.NexalithicSession = NexalithicSession;
        fragmenter = FragmenterFactory.create(type);
        assembler = AssemblerFactory.create();
    }

    public void updateSelectionKey(SelectionKey selectionKey) {
        if (this.selectionKey == selectionKey) {
            return;
        }
        if (this.selectionKey != null) {
            this.selectionKey.cancel();
        }
        this.selectionKey = selectionKey;
    }

    public boolean put(P packet) {
        return fragmenter.dispatch(packet);
    }
    public int write() throws IOException {
        return 0;
    }
    public AbstractPacket<?> read() throws IOException {
        return null;
    }
}
