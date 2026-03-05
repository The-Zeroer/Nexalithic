package com.thezeroer.nexalithic.core.model;

/**
 * 抽象模型
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/03
 * @version 1.0.0
 */
public abstract class AbstractModel {
    public static final int MAGIC_NUMBER = 0x494D5450;
    public enum ModelType {
        Packet,
        Stream
    }

    public abstract ModelType modelType();
}
