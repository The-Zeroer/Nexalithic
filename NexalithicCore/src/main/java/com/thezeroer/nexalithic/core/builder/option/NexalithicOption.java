package com.thezeroer.nexalithic.core.builder.option;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.exception.NexalithicOptionException;

import java.util.function.Function;

/**
 * Nexalithic选项
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/19
 */
public class NexalithicOption<T> {
    private volatile String name;
    private final T defaultValue;
    private final Function<NexalithicBuilderContext, T> defaultValueLazy;
    private final OptionValidator<T> validator;

    private NexalithicOption(T defaultValue, Function<NexalithicBuilderContext, T> defaultValueLazy, OptionValidator<T> validator) {
        if (validator != null && defaultValueLazy == null) {
            validator.validate(defaultValue);
        }
        this.defaultValue = defaultValue;
        this.defaultValueLazy = defaultValueLazy;
        this.validator = validator;
    }
    void setName(String name) {
        this.name = name;
    }

    public static <T> NexalithicOption<T> create(T defaultValue, OptionValidator<T> validator) {
        return new NexalithicOption<>(defaultValue, null, validator);
    }
    public static <T> NexalithicOption<T> create(Function<NexalithicBuilderContext, T> defaultValueLazy, OptionValidator<T> validator) {
        return new NexalithicOption<>(null, defaultValueLazy, validator);
    }

    public final String name() {
        return name;
    }
    public final T defaultValue() {
        return defaultValue;
    }
    public final T defaultValue(NexalithicBuilderContext context) {
        T value = defaultValue;
        if (defaultValueLazy != null) {
            value = defaultValueLazy.apply(context);
            validator.validate(value);
        }
        return value;
    }
    public final NexalithicOption<T> validate(T value) {
        if (validator != null) {
            try {
                validator.validate(value);
            } catch (IllegalArgumentException e) {
                throw new NexalithicOptionException(name, e.getMessage());
            }
        }
        return this;
    }

    @Override
    public String toString() {
        if (defaultValueLazy == null) {
            return String.format("%s(default=%s)", name, defaultValue);
        } else {
            return String.format("%s(default=%s)", name, defaultValueLazy);
        }
    }
}
