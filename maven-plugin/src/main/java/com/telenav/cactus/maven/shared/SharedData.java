package com.telenav.cactus.maven.shared;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;

/**
 * Allows mojos to share data in a type-safe manner, for cases where some
 * pre-work (such as creating a branch) needs to be hung off the validate phase,
 * before any build has run, while post-work (such as merging a branch back to
 * the stable branch) needs to be hung off the install phase, and so cannot be
 * done by the same mojo. Uses simple typed keys into a map.
 *
 * @author Tim Boudreau
 */
@Singleton
public final class SharedData
{

    private final Map<SharedDataKey<?>, Object> contents = new ConcurrentHashMap<>();

    public <T> SharedData put(SharedDataKey<T> key, T obj)
    {
        contents.put(key, obj);
        return this;
    }

    public <T> Optional<T> get(SharedDataKey<T> key)
    {
        return key.cast(contents.get(key));
    }

    public <T> Optional<T> remove(SharedDataKey<T> key)
    {
        return key.cast(contents.remove(key));
    }
}
