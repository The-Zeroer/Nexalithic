package com.thezeroer.nexalithic.core.pool;

/**
 * 通用可回收包装器抽象实现 (General Recyclable Wrapper)
 *
 * <p>实现了包装器与池之间的<b>环向引用（Circular Reference）</b>，
 * 使得包装器“出生”即知“归处”，实现了极高性能的自归还逻辑。</p>
 *
 * <p>使用规范：</p>
 * <pre>{@code
 * public class MyBuffer extends GeneralRecyclableWrapper<Buffer, MyBuffer> {
 * @Override
 * protected void onRecycle(Buffer target) { target.clear(); }
 * }
 * }</pre>
 *
 * @param <T> 底层资源类型
 * @param <W> 具体子类类型
 * @author tbrtz647@outlook.com
 * @since 2026/02/11
 */
public abstract class GeneralRecyclableWrapper<T, W extends GeneralRecyclableWrapper<T, W>> implements RecyclableWrapper<T, W> {

    /** 包装的真实对象，通过 {@code final} 保证内存可见性与不可变性 */
    protected final T target;

    /** 所属的包装池，用于执行自归还动作 */
    protected final WrapperPool<? super W> pool;

    /**
     * 构造函数。通常由闭环工厂（BiFunction）在池初始化时调用。
     * @param target 底层资源实例
     * @param pool 宿主池引用
     */
    public GeneralRecyclableWrapper(T target, WrapperPool<? super W> pool) {
        this.target = target;
        this.pool = pool;
    }

    /**
     * 触发回收。利用模版方法模式将具体的重置逻辑推迟至子类。
     */
    @Override
    @SuppressWarnings("unchecked")
    public void recycle() {
        onRecycle(target);
        if (!pool.release((W) this)) {
            onOverflow(target);
        }
    }

    /**
     * 建议子类重写并加入 released 状态检查以提高安全性
     */
    @Override
    public T unwrap() {
        return target;
    }

    /**
     * 资源重置钩子。
     * <p>子类应在此实现具体资源的清理逻辑（如清空缓冲区、重置计数器等），
     * 确保资源在回到池中时是干净的。</p>
     * @param target 需要被重置的对象
     */
    protected abstract void onRecycle(T target);

    /**
     * 溢出回收逻辑：当对象无法回到池中时，执行彻底的物理销毁。
     * 默认不执行任何操作，子类可按需重写（例如关闭文件句柄）。
     */
    protected void onOverflow(T target) {}
}
