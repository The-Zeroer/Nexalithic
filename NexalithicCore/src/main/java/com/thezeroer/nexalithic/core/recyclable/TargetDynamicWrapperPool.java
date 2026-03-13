package com.thezeroer.nexalithic.core.recyclable;

import com.thezeroer.nexalithic.core.io.thread.LoopThread;

import java.util.function.Supplier;

/**
 * <h2>目标动态包装池 (Target Dynamic Wrapper Pool)</h2>
 *
 * <p>设计核心：<b>“容器复用，内容更替”</b>。包装器实例在池中长期存在，
 * 但其包装的目标对象（Target）是瞬态的，随业务流流转不断更替。</p>
 *
 * <p>适用场景：目标对象本身不支持池化或由外部产生，需要临时包装以注入回收能力。
 * 例如：包装外部产生的字节数组、瞬态业务报文等。</p>
 *
 * @param <T> 瞬态载荷类型
 * @param <W> 具体包装器类型
 * @author tbrtz647@outlook.com
 * @since 2026/03/11
 */
public class TargetDynamicWrapperPool <T, W extends TargetDynamicWrapperPool.InteriorRecyclableWrapper<T, W>> extends AbstractWrapperPool<T, W> {
    protected final Supplier<W> wrapperFactory;

    public TargetDynamicWrapperPool(PoolStorage<W> storage, PoolStrategy<W> strategy, Supplier<W> wrapperFactory) {
        super(storage, strategy);
        this.wrapperFactory = wrapperFactory;
    }

    @Override
    protected final W create() {
        return wrapperFactory.get();
    }

    /**
     * 动态内容可回收包装器 (Dynamic Content Recyclable Wrapper)
     *
     * <p>设计核心：<b>“容器复用，内容更替”</b>。包装器实例在池中长期存在，但其包装的
     * 目标对象（Target）是瞬态的，随业务流转不断更替。</p>
     *
     * <p>性能特性：</p>
     * <ul>
     * <li>使用 {@code volatile} 保证 Target 在不同线程（如 EventLoop 切换）间的内存可见性。</li>
     * <li>回收时强制解绑 Target 引用，辅助 JVM 对瞬态对象的垃圾回收。</li>
     * <li>适用于 {@code BusinessPacket}、{@code TaskContext} 等高频产生、生命周期短的对象。</li>
     * </ul>
     *
     * @param <T> 瞬态载荷类型
     * @param <W> 具体子类类型
     * @author tbrtz647@outlook.com
     * @since 2026/03/10
     */
    @SuppressWarnings("unchecked")
    public abstract static class InteriorRecyclableWrapper<T, W extends InteriorRecyclableWrapper<T, W>> implements RecyclableWrapper<T> {
        protected volatile T target;
        protected final ProxyRecycler<? super W> recycler;
        private final W self;

        public InteriorRecyclableWrapper() {
            if (Thread.currentThread() instanceof LoopThread loopThread) {
                this.recycler = loopThread.consumeProxyRecycler();
            } else {
                this.recycler = (ProxyRecycler<? super W>) INJECTOR.get();
            }
            self = (W) this;
        }

        /**
         * 注入新的业务对象。
         * @param target 待包装的对象
         */
        public final InteriorRecyclableWrapper<T, W> wrap(T target) {
            this.target = target;
            onWrap(target);
            return this;
        }

        @Override
        public final void recycle() {
            T currentTarget = this.target;
            onRecycle(currentTarget);
            this.target = null;
            if (!recycler.release(self)) {
                onOverflow(currentTarget);
            }
        }

        @Override
        public final T unwrap() {
            return target;
        }

        protected void onWrap(T target) {

        }
        protected void onRecycle(T target) {}
        protected void onOverflow(T target) {}
    }
}
