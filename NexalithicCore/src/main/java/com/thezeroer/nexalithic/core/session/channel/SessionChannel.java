package com.thezeroer.nexalithic.core.session.channel;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.io.buffer.LoopBufferPool;
import com.thezeroer.nexalithic.core.io.codec.AssemblerFactory;
import com.thezeroer.nexalithic.core.io.codec.FragmenterFactory;
import com.thezeroer.nexalithic.core.io.codec.PacketAssembler;
import com.thezeroer.nexalithic.core.io.codec.PacketFragmenter;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.security.SecretKeyContext;
import com.thezeroer.nexalithic.core.security.SecurityChannel;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 会话通道
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/04
 * @version 1.0.0
 */
public class SessionChannel<P extends AbstractPacket, S extends NexalithicSession<S, ?, ?>> extends SecurityChannel implements NexalithicChannel{
    private static final Logger logger = LoggerFactory.getLogger(SessionChannel.class);
    // 状态掩码：Bit 31 为 Dirty 位，低位存储 SelectionKey.OP_XXX
    private static final int DIRTY_BIT = 1 << 31;
    private static final int INTEREST_MASK = ~DIRTY_BIT;
    private final S session;
    private final AbstractPacket.PacketType type;
    private final PacketFragmenter<P> fragmenter;
    private final PacketAssembler<P> assembler;
    private volatile SelectionKey selectionKey;
    private volatile SocketChannel socketChannel;
    private volatile InetSocketAddress remoteAddress;
    private final AtomicInteger targetInterest = new AtomicInteger(0);
    private final AtomicReference<State> state = new AtomicReference<>(State.Unconnected);
    private LoopBuffer readPlainBuffer, writeCipheBuffer;
    private LoopBuffer readCipheBuffer, writePlainBuffer;

    public SessionChannel(AbstractPacket.PacketType packetType, S session, SecretKeyContext secretKeyContext) {
        super(secretKeyContext);
        this.session = session;
        this.type = packetType;
        fragmenter = FragmenterFactory.create(packetType);
        assembler = AssemblerFactory.create(packetType);
    }

    public final boolean becomeConnecting() {
        return state.compareAndSet(State.Unconnected, State.Connecting);
    }
    public final SessionChannel<P, S> updateSelectionKey(SelectionKey selectionKey) throws IOException {
        if (this.selectionKey == selectionKey) {
            return this;
        }
        if (this.selectionKey != null) {
            this.selectionKey.cancel();
        }
        if (this.socketChannel != null) {
            try {
                this.socketChannel.close();
            } catch (IOException ignored) {}
        }
        this.selectionKey = selectionKey;
        this.socketChannel = (SocketChannel) selectionKey.channel();
        this.remoteAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
        this.targetInterest.set(selectionKey.interestOps());
        this.state.set(State.Connected);
        return this;
    }
    public final boolean updateChannelInterest(int interest, boolean enable) {
        while (true) {
            int oldInterest = targetInterest.get();
            int newInterest = enable ? (oldInterest | interest) : (oldInterest & ~interest);
            // 性能优化：如果兴趣位没变且已经处于 Dirty 状态，直接返回 false
            if (newInterest == oldInterest && (oldInterest & DIRTY_BIT) != 0) {
                return false;
            }
            if (targetInterest.compareAndSet(oldInterest, newInterest | DIRTY_BIT)) {
                return (oldInterest & DIRTY_BIT) == 0;
            }
            Thread.onSpinWait();
        }
    }
    public final void applyTargetInterest() {
        while (true) {
            int oldInterest = targetInterest.get();
            if ((oldInterest & DIRTY_BIT) == 0) {
                return;
            }
            int newValue = oldInterest & INTEREST_MASK;
            if (targetInterest.compareAndSet(oldInterest, newValue)) {
                SelectionKey key = this.selectionKey;
                if (key != null) {
                    try {
                        if (key.interestOps() != newValue) {
                            key.interestOps(newValue);
                        }
                    } catch (IllegalArgumentException e) {
                        logger.error("applyTargetInterest error: {}", e.getMessage());
                    } catch (Exception ignored) {}
                }
                return;
            }
            Thread.onSpinWait();
        }
    }

    public final boolean put(P packet) {
        return fragmenter.feed(packet);
    }
    @SafeVarargs
    public final boolean fill(P... packets) {
        return fragmenter.fill(packets);
    }
    public final P get() {
        return assembler.drain();
    }

    public final boolean fragmenterIsEmpty() {
        return fragmenter.isEmpty();
    }

    public final long write() throws IOException, InvalidAlgorithmParameterException, ShortBufferException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        if (readPlainBuffer == null) {
            readPlainBuffer = LoopBufferPool.INSTANCE.acquire();
            writeCipheBuffer = LoopBufferPool.INSTANCE.acquire();
        }
        if (fragmenter.drain(readPlainBuffer) > 0) {
            encrypt(readPlainBuffer, writeCipheBuffer);
        }
        long written = writeCipheBuffer.writeToChannel(socketChannel);
        if (written == 0 && writeCipheBuffer.isEmpty() && fragmenter.isEmpty()) {
            readPlainBuffer.recycle();
            readPlainBuffer = null;
            writeCipheBuffer.recycle();
            writeCipheBuffer = null;
            return -1;
        } else {
            return written;
        }
    }
    public final long read() throws IOException, InvalidAlgorithmParameterException, IllegalBlockSizeException, ShortBufferException, BadPaddingException, InvalidKeyException {
        if (readCipheBuffer == null) {
            readCipheBuffer = LoopBufferPool.INSTANCE.acquire();
            writePlainBuffer = LoopBufferPool.INSTANCE.acquire();
        }
        long read = readCipheBuffer.readFromChannel(socketChannel);
        if (read > 0) {
            decrypt(readCipheBuffer, writePlainBuffer);
            assembler.feed(writePlainBuffer);
        }
        if (readCipheBuffer.isEmpty() && writePlainBuffer.isEmpty()) {
            readCipheBuffer.recycle();
            readCipheBuffer = null;
            writePlainBuffer.recycle();
            writePlainBuffer = null;
        }
        return read;
    }

    public final S session() {
        return session;
    }
    public final AbstractPacket.PacketType getType() {
        return type;
    }
    public final State getState() {
        return state.get();
    }
    public final SelectionKey getSelectionKey() {
        return selectionKey;
    }
    public final InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public final void close() {
        if (state.compareAndSet(State.Connected, State.Unconnected)) {
            try {
                if (selectionKey != null) {
                    selectionKey.cancel();
                }
                if (socketChannel != null) {
                    socketChannel.close();
                }
            } catch (IOException ignored) {}
            if (readPlainBuffer != null) {
                readPlainBuffer.recycle();
            }
            if (writeCipheBuffer != null) {
                writeCipheBuffer.recycle();
            }
            if (readCipheBuffer != null) {
                readCipheBuffer.recycle();
            }
            if (writePlainBuffer != null) {
                writePlainBuffer.recycle();
            }
            fragmenter.clear();
            assembler.clear();
        }
    }
}
