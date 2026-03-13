package com.thezeroer.nexalithic.core.recyclable;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 池资源获取策略
 * <p>不仅决定缓存击穿时的行为，还介入资源获取前后的生命周期管理。</p>
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/11
 * @version 1.0.0
 */
public interface PoolStrategy<W> {

    /**
     * 在尝试从存储容器（Storage）获取资源之前调用。
     * <p>常用于记录获取请求的原始频率（Request Rate）。</p>
     */
    default void beforeAcquire() {}

    /**
     * 在资源成功获取（命中池或创建成功）之后调用。
     * <p>标志着一次分配流程的完美闭环。可用于统计活跃对象数或分配吞吐量。</p>
     */
    default void afterAcquire() {}

    /**
     * 当池中无可用资源，准备调用 {@code create()} 前的决策钩子。
     * @return {@code true} 允许创建新实例；{@code false} 拒绝创建（将导致分配失败），
     * 常用于实现资源配额限制或动态背压。
     */
    default boolean allowCreate() {
        return true;
    }

    /**
     * 当实例创建（factory.get()）过程中抛出异常时触发。
     * @param e 捕获到的异常实例，用于审计创建失败的原因（如资源耗尽或配置错误）。
     */
    default void createException(Exception e) {}

    /**
     * 在资源执行归还存储之前调用。
     * @return {@code true} 允许归还存储；{@code false} 拒绝归还存储，
     * <p>适用于回收前的合规性校验或回收请求计数。</p>
     */
    default boolean beforeRelease(W wrapper) {
        return true;
    }

    /**
     * 在资源成功归还至存储容器后调用。
     * <p>标志着资源生命周期的回归。可用于统计回收延迟或缓存命中率分析。</p>
     */
    default void afterRelease() {}

    @SuppressWarnings("unchecked")
    static <W> PoolStrategy<W> alwaysCreate() {
        return (PoolStrategy<W>) AlwaysCreateStrategy.INSTANCE;
    }
    static <W> PoolStrategy<W> failFast(int max) {
        return new FailFastStrategy<>(max);
    }
    static <W> PoolStrategy<W> blocking(int max) {
        return new BlockingWaitStrategy<>(max);
    }
    static <W> PoolStrategy<W> waitOrCreate(int max, long timeoutMillis) {
        return new WaitOrCreateStrategy<>(max, timeoutMillis);
    }
    static <W> PoolStrategy<W> waitOrFail(int max, long timeoutMillis) {
        return new WaitOrFailStrategy<>(max, timeoutMillis);
    }

    /**
     * 总是创建策略：不对创建逻辑做任何干预，直接允许。
     *
     * @author tbrtz647@outlook.com
     * @since 2026/03/11
     * @version 1.0.0
     */
    record AlwaysCreateStrategy<W>() implements PoolStrategy<W> {
        public static final AlwaysCreateStrategy<Object> INSTANCE = new AlwaysCreateStrategy<>();
    }

    /**
     * 抛出异常策略：达到硬上限后直接拒绝。
     *
     * @author tbrtz647@outlook.com
     * @since 2026/03/11
     * @version 1.0.0
     */
    class FailFastStrategy<W> implements PoolStrategy<W> {
        private final int max;
        private final AtomicInteger count = new AtomicInteger(0);

        public FailFastStrategy(int max) { this.max = max; }

        @Override
        public boolean allowCreate() {
            while (true) {
                int current = count.get();
                if (current >= max) {
                    throw new IllegalStateException(String.format("Nexalithic Pool Exhausted: [Active: %d, Max: %d]. Allocation denied.", current, max));
                }
                if (count.compareAndSet(current, current+1)) {
                    return true;
                }
            }
        }

        @Override
        public void afterRelease() { count.decrementAndGet(); }

        @Override
        public void createException(Exception e) { count.decrementAndGet(); }
    }

    /**
     * 无限等待策略：使用信号量阻塞 acquire 线程直到有资源空闲。
     *
     * @author tbrtz647@outlook.com
     * @since 2026/03/11
     * @version 1.0.0
     */
    class BlockingWaitStrategy<W> implements PoolStrategy<W> {
        private final Semaphore semaphore;

        public BlockingWaitStrategy(int max) {
            this.semaphore = new Semaphore(max);
        }

        @Override
        public void beforeAcquire() {
            semaphore.acquireUninterruptibly();
        }

        @Override
        public void afterRelease() {
            semaphore.release();
        }

        @Override
        public void createException(Exception e) {
            semaphore.release();
        }
    }

    /**
     * 有限等待后创建：优先等待复用，超时则“借债”创建。
     *
     * @author tbrtz647@outlook.com
     * @since 2026/03/11
     * @version 1.0.0
     */
    class WaitOrCreateStrategy<W> implements PoolStrategy<W> {
        private final Semaphore semaphore;
        private final long timeoutMillis;

        public WaitOrCreateStrategy(int max, long timeoutMillis) {
            this.semaphore = new Semaphore(max);
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public void beforeAcquire() {
            try {
                semaphore.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void afterRelease() {
            semaphore.release();
        }

        @Override
        public void createException(Exception e) {
            semaphore.release();
        }
    }

    /**
     * 有限等待后抛出异常：最常用的背压策略。
     *
     * @author tbrtz647@outlook.com
     * @since 2026/03/11
     * @version 1.0.0
     */
    class WaitOrFailStrategy<W> implements PoolStrategy<W> {
        private final Semaphore semaphore;
        private final long timeoutMillis;
        private final ThreadLocal<Boolean> acquired = new ThreadLocal<>();

        public WaitOrFailStrategy(int max, long timeoutMillis) {
            this.semaphore = new Semaphore(max);
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public void beforeAcquire() {
            try {
                acquired.set(semaphore.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                acquired.set(false);
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public boolean allowCreate() {
            if (acquired.get()) {
                return true;
            }
            throw new IllegalStateException(
                    String.format("Nexalithic Pool Timeout: Failed to acquire resource within %d ms. [Available: %d]",
                            timeoutMillis, semaphore.availablePermits())
            );
        }

        @Override
        public void afterAcquire() {
            acquired.remove();
        }

        @Override
        public void afterRelease() {
            semaphore.release();
        }

        @Override
        public void createException(Exception e) {
            semaphore.release();
            acquired.remove();
        }
    }
}
