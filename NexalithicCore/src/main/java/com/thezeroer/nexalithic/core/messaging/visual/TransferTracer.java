package com.thezeroer.nexalithic.core.messaging.visual;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.builder.module.ModulesDefinition;
import com.thezeroer.nexalithic.core.builder.module.NexalithicModule;
import com.thezeroer.nexalithic.core.builder.option.NexalithicOption;
import com.thezeroer.nexalithic.core.builder.option.OptionValidator;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 传输追踪器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/04/03
 * @version 1.0.0
 */
public class TransferTracer {
    public static final Options OPTIONS = OptionsDefinition.initOptions(Options.class, TransferTracer.class);
    public static final class Options extends AbstractLoop.Options {
        public final NexalithicOption<Boolean> Enable_ExecutorService = NexalithicOption.create(true, OptionValidator.nonNull());
        public final NexalithicOption<Long> ProgressPoller_Delay = NexalithicOption.create(10L, OptionValidator.positive());
        private Options(Class<?> holder) {
            super(holder);
        }
    }
    public static final class Modules implements ModulesDefinition {
        public static final NexalithicModule<ExecutorService> ExecutorService = NexalithicModule.create("TransferTracer_ExecutorService", ExecutorService.class);
    }
    public record Constant(long UpdateRunnable_Delay) {}
    private final Constant CONSTANT;
    private final Map<Long, TransferListenerGroup> visualizers = new ConcurrentHashMap<>();
    private final Set<TransferListener> listeners = ConcurrentHashMap.newKeySet();
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduler;
    private final Runnable updateRunnable;
    private final AtomicBoolean isPolling = new AtomicBoolean(false);

    public TransferTracer(NexalithicBuilderContext context) {
        CONSTANT = new Constant(context.getOption(OPTIONS.ProgressPoller_Delay));
        if (context.getOption(OPTIONS.Enable_ExecutorService)) {
            executorService = context.getModule(Modules.ExecutorService, () -> new ThreadPoolExecutor(
                    0, 1, 60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(1024),
                    runnable -> {
                        Thread t = new Thread(runnable, "TransferTracer-ExecutorService");
                        t.setDaemon(true);
                        return t;
                    }
            ));
        } else {
            executorService = null;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "TransferTracer-Progress-Poller");
            t.setDaemon(true);
            return t;
        });
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (listeners.isEmpty()) {
                    if (isPolling.compareAndSet(false, true)) {
                        return;
                    }
                }
                for (TransferListener listener : listeners) {
                    try {
                        listener.onUpdated();
                    } catch (Exception ignored) {
                    }
                }
                scheduler.schedule(this, CONSTANT.UpdateRunnable_Delay, TimeUnit.MILLISECONDS);
            }
        };
    }

    public void putVisualizer(TransferListenerGroup visualizer) {
        visualizers.put(visualizer.taskId(), visualizer);
    }
    public TransferListenerGroup getVisualizer(long taskId) {
        return visualizers.get(taskId);
    }
    public TransferListenerGroup removeVisualizer(long taskId) {
        return visualizers.remove(taskId);
    }

    public void onStart(TransferListener listener, TransferSnapshot snapshot) {
        if (executorService == null) {
            listener.onStarted(snapshot);
            listeners.add(listener);
            checkAndActivatePolling();
        } else {
            executorService.execute(() -> {
                listener.onStarted(snapshot);
                listeners.add(listener);
                checkAndActivatePolling();
            });
        }
    }
    public void onPause(TransferListener listener) {
        if (executorService == null) {
            listener.onPaused();
        } else {
            executorService.execute(listener::onPaused);
        }
    }
    public void onResume(TransferListener listener) {
        if (executorService == null) {
            listener.onResumed();
        } else {
            executorService.execute(listener::onResumed);
        }
    }
    public void onFinish(TransferListener listener) {
        if (executorService == null) {
            listeners.remove(listener);
            listener.onUpdated();
            listener.onFinished();
        } else {
            executorService.execute(() -> {
                listeners.remove(listener);
                listener.onUpdated();
                listener.onFinished();
            });
        }
    }

    private void checkAndActivatePolling() {
        if (isPolling.compareAndSet(false, true)) {
            if (!listeners.isEmpty()) {
                scheduler.execute(updateRunnable);
            } else {
                isPolling.set(false);
            }
        }
    }
}
