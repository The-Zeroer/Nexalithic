package com.thezeroer.nexalithic.core.infra.recyclable;

import java.util.function.Supplier;

/**
 * <h2>自包装器池 (Self-Wrapper Pool)</h2>
 *
 * <p>Nexalithic 框架的高性能核心组件，专门用于管理 {@link InteriorRecyclableWrapper} 及其子类。
 * 该组件通过“身兼两职”的设计，消除了池化中 Wrapper 与 Target 之间的解引用开销。</p>
 *
 * @param <W> 必须继承自 {@link InteriorRecyclableWrapper} 的具体实现类型
 * @author tbrtz647@outlook.com
 * @since 2026/03/10
 */
public class SelfStaticWrapperPool<W extends SelfStaticWrapperPool.InteriorRecyclableWrapper<W>> extends AbstractWrapperPool<W, W> {
    protected final Supplier<W> wrapperFactory;

    /**
     * 构造一个新的自包装池。
     * @param storage 外部提供的无锁或高并发存储容器
     * @param wrapperFactory 用户定义的实例工厂，通常为 {@code MyWrapper::new}
     */
    public SelfStaticWrapperPool(PoolStorage<W> storage, PoolStrategy<W> strategy, Supplier<W> wrapperFactory) {
        super(storage, strategy);
        this.wrapperFactory = wrapperFactory;
    }

    @Override
    protected final W onCreate() {
        return wrapperFactory.get();
    }

    /**
     * 静态自包装可回收包装器 (Static Self-Recyclable Wrapper)
     *
     * <p>设计核心：<b>“身兼两职”</b>。该类既作为生命周期管理的 Wrapper，其自身实例也是
     * 需要被复用的资源实体（Target）。</p>
     *
     * <p>性能特性：</p>
     * <ul>
     * <li><b>零引用开销：</b> 移除了独立的 target 字段，减少 4-8 字节的指针占用及解引用开销。</li>
     * <li><b>更好的缓存局部性：</b> 资源数据与回收元数据在物理内存上紧凑排列。</li>
     * <li>适用于逻辑简单的池化组件，如自定义 Task、Signal 事件或状态位容器。</li>
     * </ul>
     *
     * @param <W> 具体子类类型，既是容器也是资源
     * @author tbrtz647@outlook.com
     * @since 2026/03/10
     */
    @SuppressWarnings("unchecked")
    public abstract static class InteriorRecyclableWrapper<W extends InteriorRecyclableWrapper<W>> extends AbstractRecyclableWrapper<W, W> {

        /**
         * 返回自身作为资源引用。
         * @return 当前实例 {@code this}
         */
        @Override
        public final W unwrap() {
            return (W) this;
        }
    }
}