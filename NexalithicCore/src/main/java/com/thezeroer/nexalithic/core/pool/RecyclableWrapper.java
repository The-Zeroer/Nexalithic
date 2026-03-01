package com.thezeroer.nexalithic.core.pool;

/**
 * 池化资源的可回收包装器 (Recyclable Wrapper)
 *
 * <p>作为底层资源（如 TLoopBuffer）与业务逻辑之间的“隔离带”和“生命周期凭证”。
 * 采用 <b>F-Bound Polymorphism (递归泛型)</b> 确保包装器与所属池的类型精确匹配。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 * <li><b>自动闭环：</b> 实现 {@link AutoCloseable}，支持 try-with-resources 语法。</li>
 * <li><b>权限控制：</b> 通过 {@link #unwrap()} 提供高性能后门，但保留状态检查能力。</li>
 * </ul>
 *
 * @param <T> 被包装的底层资源类型
 * @param <W> 包装器自身的具体类型，用于类型安全的回收操作
 * @author tbrtz647@outlook.com
 * @since 2026/02/10
 */
public interface RecyclableWrapper<T, W extends RecyclableWrapper<T, W>> extends AutoCloseable {

    /**
     * 显式回收资源。
     * <p>执行流程：重置底层资源 -> 解除引用绑定（可选） -> 归还至 {@link WrapperPool}。</p>
     */
    void recycle();

    /**
     * 默认关闭实现，建议配合 try-with-resources 使用。
     */
    @Override
    default void close() {
        recycle();
    }

    /**
     * 提取底层裸资源。
     * <p><b>性能警告：</b> 此方法允许开发者跳过包装器的状态检查直接操作内存。
     * 使用时必须遵守“栈封闭”原则：严禁将返回值传递给异步线程或赋值给成员变量。</p>
     * @return 底层资源实例
     */
    T unwrap();
}
