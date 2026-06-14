package com.thezeroer.nexalithic.core.builder;

import com.thezeroer.nexalithic.core.builder.module.NexalithicModule;
import com.thezeroer.nexalithic.core.builder.option.NexalithicOption;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Nexalithic 构建上下文
 *
 * @author tbrtz647@outlook.com
 * @since 2026/04/03
 * @version 1.0.0
 */
public class NexalithicBuilderContext {
    private final Map<NexalithicOption<?>, Object> options = new ConcurrentHashMap<>();
    private final Map<NexalithicModule<?>, Object> modules = new ConcurrentHashMap<>();
    private final Map<CompositeKey, Object> constants = new ConcurrentHashMap<>();

    public NexalithicBuilderContext() {}

    public <T> void setOption(NexalithicOption<T> option, T value) {
        options.put(option.validate(value), value);
    }
    @SuppressWarnings("unchecked")
    public <T> T getOption(NexalithicOption<T> option) {
        Object value = options.get(option);
        if (value == null) {
            return option.defaultValue(this);
        } else {
            return (T) value;
        }
    }

    /**
     * @param <K> module 声明的基础类型
     * @param <V> 实际注入的对象类型，必须是 K 的子类 (V extends K)
     */
    public <K, V> NexalithicBuilderContext setModule(NexalithicModule<K> module, V value) {
        if (!module.type().isInstance(value)) {
            throw new IllegalArgumentException(String.format(
                    "Module [%s] mismatch: Expected %s, but got %s",
                    module.name(), module.type().getSimpleName(), value.getClass().getSimpleName()));
        }
        modules.put(module, value);
        return this;
    }

    /**
     * @param <T> Key 定义时携带的原始类型
     * @param <R> 目标变量需要的精确类型
     */
    @SuppressWarnings("unchecked")
    public <T, R> R getModule(NexalithicModule<T> module) {
        Object value = modules.get(module);
        if (value == null) {
            throw new IllegalArgumentException("Module " + module.name() + " not found");
        }
        if (!module.type().isInstance(value)) {
            throw new IllegalArgumentException(String.format(
                    "Module [%s] mismatch: Expected %s, but got %s",
                    module.name(), module.type().getSimpleName(), value.getClass().getSimpleName()));
        }
        return (R) value;
    }
    @SuppressWarnings("unchecked")
    public <T, R> R getModule(NexalithicModule<T> module, Supplier<R> lazy) {
        Object value = modules.get(module);
        if (value == null) {
            synchronized (modules) {
                value = modules.get(module);
                if (value == null) {
                    value = lazy.get();
                    setModule(module, value);
                }
            }
        }
        if (!module.type().isInstance(value)) {
            throw new IllegalArgumentException(String.format(
                    "Module [%s] mismatch: Expected %s, but got %s",
                    module.name(), module.type().getSimpleName(), value.getClass().getSimpleName()));
        }
        return (R) value;
    }

    @SuppressWarnings("unchecked")
    public <T> T getConstant(Class<?> holder, Class<T> constant, Supplier<T> lazy) {
        CompositeKey key = new CompositeKey(holder, constant);
        Object value = constants.get(key);
        if (value == null) {
            synchronized (constants) {
                value = constants.get(key);
                if (value == null) {
                    value = lazy.get();
                    constants.put(key, value);
                }
            }
        }
        return (T) value;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<NexalithicOption<?>, Object> entry : options.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    private record CompositeKey(Class<?> holder, Class<?> type) {}
}
