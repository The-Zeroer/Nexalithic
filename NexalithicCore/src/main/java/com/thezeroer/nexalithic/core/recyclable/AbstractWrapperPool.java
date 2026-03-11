package com.thezeroer.nexalithic.core.recyclable;

import com.thezeroer.nexalithic.core.io.thread.LoopThread;

/**
 * 抽象包装池
 *
 * <p>该类为所有回收池提供统一的生命周期管理逻辑。通过集成 {@link PoolStorage} 存储方案
 * 与 {@link PoolStrategy} 策略控制，实现了分配、回收及预热的标准化流程。</p>
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/11
 * @version 1.0.0
 */
public abstract class AbstractWrapperPool<T, W extends RecyclableWrapper<T>> implements WrapperPool<W>{
    /**
     * 慢速路径注入器。用于在非 LoopThread 环境下安全地传递回收器实例。
     */
    protected static final ThreadLocal<ProxyRecycler<?>> INJECTOR = new ThreadLocal<>();
    /**
     * 具体的回收处理器。采用记录类 (Record) 实现以增强 JIT 的类型剖析 (Type Profiling) 效率。
     */
    private final ProxyRecycler<W> recycler;
    private final PoolStorage<W> storage;
    private final PoolStrategy<W> strategy;

    protected AbstractWrapperPool(PoolStorage<W> storage, PoolStrategy<W> strategy) {
        this.storage = storage;
        this.strategy = strategy;
        this.recycler = new InternalRecycler<>(storage, strategy);
    }

    /**
     * 执行池预热
     * @param prefillRatio 预热比例 (0.0 ~ 1.0)。
     * 例如 0.5 表示启动时填充 50% 的容量。
     * @throws IllegalArgumentException 如果比例不在有效范围内
     */
    @Override
    public final WrapperPool<W> warmUp(double prefillRatio) {
        if (prefillRatio < 0.0 || prefillRatio > 1.0) {
            throw new IllegalArgumentException("Prefill ratio must be between 0.0 and 1.0");
        }
        for (int i = 0; i < (int) (storage.capacity() * prefillRatio); i++) {
            if (!storage.offer(create())) {
                break;
            }
        }
        return this;
    }

    /**
     * 获取一个可用实例。
     *
     * @return 准备就绪的 {@link W} 实例
     * @throws RuntimeException 若工厂生产失败，将清理上下文并向上抛出
     */
    @Override
    public final W acquire() {
        strategy.beforeAcquire();
        W w = storage.poll();
        if (w == null) {
            if (Thread.currentThread() instanceof LoopThread loopThread) {
                loopThread.productionProxyRecycler(recycler);
                try {
                    if (strategy.allowCreate()) {
                        w = create();
                    }
                } catch (Exception e) {
                    loopThread.consumeProxyRecycler();
                    strategy.createException(e);
                    throw e;
                }
            } else {
                INJECTOR.set(recycler);
                try {
                    if (strategy.allowCreate()) {
                        w = create();
                    }
                } catch (Exception e) {
                    strategy.createException(e);
                    throw e;
                } finally {
                    INJECTOR.remove();
                }
            }
        }
        strategy.afterAcquire();
        return w;
    }

    /**
     * 在池为空或执行池预热时，创建一个新实列
     *
     * @return {@link W }
     */
    protected abstract W create();

    private record InternalRecycler<W extends RecyclableWrapper<?>>(PoolStorage<W> storage, PoolStrategy<W> strategy) implements ProxyRecycler<W> {
        @Override
        public boolean release(W w) {
            try {
                return strategy.beforeRelease(w) && storage.offer(w);
            } finally {
                strategy.afterRelease();
            }
        }
    }
}
