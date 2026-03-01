package com.thezeroer.nexalithic.core.pool;

/**
 * 包装器池 (Wrapper Pool)
 * <p>负责管理 {@link RecyclableWrapper} 实例的生命周期。通过复用包装器对象，
 * 消除高并发场景下频繁创建临时包装对象带来的 GC 压力。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 * <li><b>高吞吐：</b> 建议实现类使用无锁队列（如 MpscQueue）以减少竞争。</li>
 * <li><b>内存安全：</b> 配合包装器状态位，防止资源归还后的数据踩踏。</li>
 * </ul>
 *
 * @param <W> 池所管理的具体包装器类型，必须是自引用泛型。
 * @author tbrtz647@outlook.com
 * @since 2026/02/11
 */
public interface WrapperPool<W extends RecyclableWrapper<?, W>> {

    /**
     * 从池中获取一个可用的包装器。
     * <p>如果池为空，实现类应根据策略创建新实例或阻塞等待。</p>
     * @return 准备就绪的包装器实例
     */
    W acquire();

    /**
     * 将包装器归还至池中。
     * <p>调用者不应直接调用此方法，而应通过包装器的 {@code recycle()} 触发自动归还。</p>
     * @param w 要释放的包装器实例
     * @return 是否成功归还到 Queue
     */
    boolean release(W w);
}
