package com.thezeroer.nexalithic.core.builder.option;

import com.thezeroer.nexalithic.core.exception.NexalithicOptionException;

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
    private final OptionValidator<T> validator;

    private NexalithicOption(T defaultValue, OptionValidator<T> validator) {
        if (validator != null) {
            validator.validate(defaultValue);
        }
        this.defaultValue = defaultValue;
        this.validator = validator;
    }
    void setName(String name) {
        this.name = name;
    }

    public static <T> NexalithicOption<T> create(T defaultValue, OptionValidator<T> validator) {
        return new NexalithicOption<>(defaultValue, validator);
    }

    public final String name() {
        return name;
    }
    public final T defaultValue() {
        return defaultValue;
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
        return String.format("%s(default=%s)", name, defaultValue);
    }
}
