package com.thezeroer.nexalithic.core.exception;

/**
 * Nexalithic异常基类
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/03
 * @version 1.0.0
 */
public class NexalithicException extends RuntimeException {
    public NexalithicException(String message) {
        super(message);
    }
}