package com.telenav.cactus.maven.util;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Abstraction for things that can be waited on for a result.
 *
 * @see AwaitableCompletionStage
 * @author Tim Boudreau
 */
public interface Awaitable<T>
{

    T await() throws InterruptedException;

    T await(long amount, TimeUnit unit) throws InterruptedException;

    default T await(Duration duration) throws InterruptedException
    {
        return await(duration.toMillis(), TimeUnit.MILLISECONDS);
    }
}
