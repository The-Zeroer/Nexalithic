package com.thezeroer.nexalithic.core.option;

import java.util.Map;

public class OptionMap {
    private final Map<NexalithicOption<?>, Object> values;

    private OptionMap(Map<NexalithicOption<?>, Object> values) {
        this.values = Map.copyOf(values);
    }

    public static OptionMap of(Map<NexalithicOption<?>, Object> values) {
        return new OptionMap(values);
    }

    @SuppressWarnings("unchecked")
    public <T> T value(NexalithicOption<T> option) {
        Object val = values.get(option);
        if (val == null) {
            return option.defaultValue();
        } else {
            option.set((T) val);
            return (T) val;
        }
    }

    public boolean contains(NexalithicOption<?> option) {
        return values.containsKey(option);
    }
}