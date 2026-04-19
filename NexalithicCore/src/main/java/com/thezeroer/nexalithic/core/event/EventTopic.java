package com.thezeroer.nexalithic.core.event;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * 活动主题
 *
 * @author tbrtz647@outlook.com
 * @since 2026/04/19
 * @version 1.0.0
 */
public class EventTopic<T extends NexalithicEvent> {
    private final Queue<Consumer<T>> subscribers = new ConcurrentLinkedQueue<>();
    private volatile boolean subscribed = false;

    public boolean isSubscribed() {
        return subscribed;
    }

    public EventSubscription subscribe(Consumer<T> handler) {
        subscribers.add(handler);
        subscribed = true;
        return () -> this.unsubscribe(handler);
    }
    public EventSubscription subscribeOnce(Consumer<T> handler) {
        Consumer<T> wrapper = new Consumer<>() {
            @Override
            public void accept(T event) {
                try {
                    handler.accept(event);
                } finally {
                    unsubscribe(this);
                }
            }
        };
        subscribers.add(wrapper);
        subscribed = true;
        return () -> this.unsubscribe(wrapper);
    }
    public void unsubscribe(Consumer<T> handler) {
        subscribers.remove(handler);
        subscribed = !subscribers.isEmpty();
    }
    public void publish(T event) {
        for (Consumer<T> subscriber : subscribers) {
            subscriber.accept(event);
        }
    }
}
