package dev.compactmods.machines.api.event;

import java.util.stream.Stream;

/**
 * Used to gather a list of listeners.
 *
 * @param <B> Base Class used for the event listeners
 */
public interface IEventListenerList<B> {
    void addListener(B listener);

    default <E extends B> void addListener(Class<E> eClass, E listener) {
        addListener(listener);
    }

    Stream<B> getListeners();

}
