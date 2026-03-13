package com.thezeroer.nexalithic.core.model.packet;

import com.thezeroer.nexalithic.core.exception.NexalithicException;

/**
 * 当多个 Payload 类产生了相同的 IdentityCode 时抛出
 */
public class PayloadCollisionException extends NexalithicException {
    private final long conflictedId;
    private final Class<?> existingClass;
    private final Class<?> newClass;

    public PayloadCollisionException(long id, Class<?> existing, Class<?> clazz) {
        super(String.format(
                "Payload ID Collision Detected! ID [%d] is already occupied. Existing Class: %s, Conflicting Class: %s",
                id, existing.getName(), clazz.getName()
        ));
        this.conflictedId = id;
        this.existingClass = existing;
        this.newClass = clazz;
    }

    public long getConflictedId() { return conflictedId; }
    public Class<?> getExistingClass() { return existingClass; }
    public Class<?> getNewClass() { return newClass; }
}