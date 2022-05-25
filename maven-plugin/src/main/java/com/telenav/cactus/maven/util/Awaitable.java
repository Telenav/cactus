package com.telenav.cactus.maven.util;

import com.mastfrog.util.preconditions.Exceptions;
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

    default T awaitQuietly()
    {
        try
        {
            return await();
        } catch (InterruptedException ex)
        {
            return Exceptions.chuck(ex);
        }
    }

    T await(long amount, TimeUnit unit) throws InterruptedException;

    default T await(Duration duration) throws InterruptedException
    {
        return await(duration.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Awaits, rethrowing the interrupted exception as an undeclared throwable.
     *
     * @param duration A duration
     * @return An instance of T
     */
    default T awaitQuietly(Duration duration)
    {
        try
        {
            return await(duration);
        } catch (InterruptedException ex)
        {
            return Exceptions.chuck(ex);
        }
    }
}
