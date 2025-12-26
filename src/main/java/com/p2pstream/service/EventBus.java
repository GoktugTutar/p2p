package com.p2pstream.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class EventBus {

    public enum EventType {
        PEER_FOUND,
        LOG,
        SEARCH_RESULT,
        TRANSFER_PROGRESS
    }

    public record Event(EventType type, Object payload) {}

    private final Map<EventType, CopyOnWriteArrayList<Consumer<Event>>> listeners = new ConcurrentHashMap<>();
    private final Executor dispatchPool = Executors.newCachedThreadPool();

    public void on(EventType type, Consumer<Event> handler) {
        listeners.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public void emit(EventType type, Object payload) {
        Event e = new Event(type, payload);
        List<Consumer<Event>> ls = listeners.get(type);
        if (ls == null) return;
        for (Consumer<Event> c : ls) {
            dispatchPool.execute(() -> c.accept(e));
        }
    }
}
