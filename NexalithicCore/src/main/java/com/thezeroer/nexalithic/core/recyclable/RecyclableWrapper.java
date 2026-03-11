package com.thezeroer.nexalithic.core.recyclable;

/**
 * 池化资源的可回收包装器 (Recyclable Wrapper)
 *
 * <p>作为底层资源与业务逻辑之间的“隔离带”和“生命周期凭证”。通过池化技术规避 JVM 对象分配开销，
 * 采用 <b>递归泛型</b> 确保包装器与所属池的类型精确匹配。</p>
 *
 * @param <T> 被包装的底层资源类型
 * @author tbrtz647@outlook.com
 * @since 2026/02/10
 */
public interface RecyclableWrapper<T> extends AutoCloseable {
    /**
     * 显式回收包装器。
     * <p>根据实现类不同，可能执行不同的重置逻辑（如 {@code target.clear()} 或 {@code target = null}）。</p>
     * <p><b>幂等性要求：</b> 必须确保多次调用不会导致池数据损坏。</p>
     */
    void recycle();

    /**
     * 提取内部对象。
     * <p><b>约束：</b> 严禁将此返回值逃逸（Escape）到当前线程栈之外，除非明确了解其生命周期。</p>
     * @return 内部对象引用
     */
    T unwrap();

    /**
     * 默认支持 try-with-resources。
     */
    @Override
    default void close() {
        recycle();
    }
}
