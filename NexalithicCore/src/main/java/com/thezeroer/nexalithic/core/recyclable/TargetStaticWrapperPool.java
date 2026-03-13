package com.thezeroer.nexalithic.core.recyclable;

import com.thezeroer.nexalithic.core.io.thread.LoopThread;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * <h2>目标静态包装池 (Target Static Wrapper Pool)</h2>
 *
 * <p>设计核心：<b>“强绑定复用”</b>。包装器与目标对象在创建时即完成绑定，
 * 并在整个生命周期内保持 1:1 的稳定关系。回收时，两者共同回到池中。</p>
 *
 * <p>适用场景：目标对象（如重型 Buffer、数据库连接）创建成本极高，
 * 且需要固定的包装器元数据来管理其生命周期的场景。</p>
 *
 * @param <T> 物理资源类型
 * @param <W> 固定包装器类型
 * @author tbrtz647@outlook.com
 * @since 2026/03/11
 */
public class TargetStaticWrapperPool<T, W extends TargetStaticWrapperPool.InteriorRecyclableWrapper<T, W>> extends AbstractWrapperPool<T, W> {
    protected final Supplier<T> targetFactory;
    protected final Function<T, W> wrapperFactory;

    public TargetStaticWrapperPool(PoolStorage<W> storage, PoolStrategy<W> strategy, Supplier<T> targetFactory, Function<T, W> wrapperFactory) {
        super(storage, strategy);
        this.targetFactory = targetFactory;
        this.wrapperFactory = wrapperFactory;
    }

    @Override
    protected final W create() {
        return wrapperFactory.apply(targetFactory.get());
    }

    /**
     * 静态资源可回收包装器 (Static Resource Recyclable Wrapper)
     *
     * <p>设计核心：<b>“资源常驻，整体复用”</b>。包装器与其内部资源在初始化时强绑定，
     * 在整个系统运行期间共同进退，实现真正意义上的<b>零 GC (Zero-GC)</b>。</p>
     *
     * <p>性能特性：</p>
     * <ul>
     * <li>利用 {@code final target} 触发 JVM 激进优化，实现极高性能的资源访问。</li>
     * <li>通过模版方法 {@link #onRecycle(Object)} 强制执行状态重置，确保资源“归池即净”。</li>
     * <li>适用于 {@code ByteBuffer}、{@code byte[]}、{@code Connection} 等昂贵且可重置的资源。</li>
     * </ul>
     *
     * @param <T> 常驻资源类型
     * @param <W> 具体子类类型
     * @author tbrtz647@outlook.com
     * @since 2026/02/11
     */
    @SuppressWarnings("unchecked")
    public abstract static class InteriorRecyclableWrapper<T, W extends InteriorRecyclableWrapper<T, W>> implements RecyclableWrapper<T> {
        /** 绑定的常驻对象，通过 {@code final} 锁定内存可见性 */
        protected final T target;
        protected final ProxyRecycler<? super W> recycler;
        private final W self;

        public InteriorRecyclableWrapper(T target) {
            this.target = target;
            if (Thread.currentThread() instanceof LoopThread loopThread) {
                this.recycler = loopThread.consumeProxyRecycler();
            } else {
                this.recycler = (ProxyRecycler<? super W>) INJECTOR.get();
            }
            self = (W) this;
        }

        /**
         * 触发回收序列。
         * <p>1. 调用 {@link #onRecycle(T)} 执行子类定义的重置逻辑。</p>
         * <p>2. 将包装器与资源作为整体归还池中。若池满，则触发 {@link #onOverflow(T)}。</p>
         */
        @Override
        public final void recycle() {
            onRecycle(target);
            if (!recycler.release(self)) {
                onOverflow(target);
            }
        }

        @Override
        public T unwrap() {
            return target;
        }

        /**
         * 资源状态重置钩子。
         * <p>子类必须在此实现重置逻辑（如 {@code buffer.clear()}），确保资源再次借出时无脏数据。</p>
         * @param target 待重置的常驻资源
         */
        protected abstract void onRecycle(T target);

        /**
         * 溢出销毁钩子。
         * <p>当池已满且无法接收归还时，在此执行彻底的销毁动作（如关闭句柄）。</p>
         */
        protected void onOverflow(T target) {}
    }
}
