package dev.compactmods.machines.event;

import dev.compactmods.machines.api.event.IEventListenerList;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Used to gather a list of listeners.
 * An implementation of {@link IEventListenerList}
 *
 * @param <B> Base Class used for the event listeners
 */
public final class EventListenerList<B> implements IEventListenerList<B> {
    public static <B> EventListenerList<B> createList() {
        return new EventListenerList<>();
    }

    private final List<B> listeners = new ArrayList<>();

    private EventListenerList() {}

    public void addListener(B listener) {
        this.listeners.add(listener);
    }

    public Stream<B> getListeners() {
        return listeners.stream();
    }
}
