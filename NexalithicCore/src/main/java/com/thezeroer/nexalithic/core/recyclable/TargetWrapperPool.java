package com.thezeroer.nexalithic.core.recyclable;

import com.thezeroer.nexalithic.core.io.thread.LoopThread;

import java.util.Queue;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 目标资源包装池 (Target Wrapper Pool)
 *
 * <p>该类是资源池化设计的通用实现。其特征在于<b>“资源与容器分离”</b>：
 * 包装器（Wrapper）作为外部资源（Target）的载体，建立统一的生命周期管理。</p>
 *
 * <p>与 {@link SelfWrapperPool} 的区别：</p>
 * <ul>
 * <li><b>双重结构：</b> 存在明确的 Target (T) 和 Wrapper (W) 两个对象，适合包装第三方或无法修改源码的资源。</li>
 * <li><b>解耦工厂：</b> 通过 {@code targetFactory} 与 {@code wrapperFactory} 分离生产与组装逻辑。</li>
 * </ul>
 *
 * @param <T> 底层物理资源类型（被包装的目标）
 * @param <W> 具备回收能力的包装器类型
 * @author tbrtz647@outlook.com
 * @since 2026/02/11
 */
public class TargetWrapperPool<T, W extends RecyclableWrapper<T>> extends AbstractWrapperPool<T, W> {
    protected final Supplier<T> targetFactory;
    protected final Function<T, W> wrapperFactory;

    /**
     * 构造一个通用包装器池。
     *
     * @param storage        存储容器实现（决定了池的并发特性与容量限制）
     * @param targetFactory  原始资源工厂
     * @param wrapperFactory 包装器组装工厂
     */
    public TargetWrapperPool(PoolStorage<W> storage, PoolStrategy<W> strategy, Supplier<T> targetFactory, Function<T, W> wrapperFactory) {
        super(storage, strategy);
        this.targetFactory = targetFactory;
        this.wrapperFactory = wrapperFactory;
    }

    @Override
    protected W create() {
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
    public abstract static class StaticRecyclableWrapper<T, W extends StaticRecyclableWrapper<T, W>> implements RecyclableWrapper<T> {
        /** 绑定的常驻对象，通过 {@code final} 锁定内存可见性 */
        protected final T target;
        protected final ProxyRecycler<? super W> recycler;
        private final W self;

        public StaticRecyclableWrapper(T target) {
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
    public abstract static class DynamicRecyclableWrapper<T, W extends DynamicRecyclableWrapper<T, W>> implements RecyclableWrapper<T> {
        protected volatile T target;
        protected final ProxyRecycler<? super W> recycler;
        private final W self;

        public DynamicRecyclableWrapper() {
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
        public final void wrap(T target) {
            this.target = target;
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
        public T unwrap() {
            return target;
        }

        protected void onRecycle(T target) {}

        protected void onOverflow(T target) {}
    }
}