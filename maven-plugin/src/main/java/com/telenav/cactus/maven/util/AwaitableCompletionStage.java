package com.telenav.cactus.maven.util;

import com.mastfrog.function.throwing.ThrowingSupplier;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * CompletionStage has no way to do a blocking wait for a result, and
 * CompletableFuture should not be directly exposed unless you want to invite
 * thw orld to be able to randomly complete it, so - this.
 *
 * @author Tim Boudreau
 */
public interface AwaitableCompletionStage<T> extends CompletionStage<T>, Awaitable<T>
{

    public static <R> AwaitableCompletionStage<R> of(CompletionStage<R> stage)
    {
        if (stage instanceof AwaitableCompletionStage<?>)
        {
            return (AwaitableCompletionStage<R>) stage;
        }
        return new AwaitableCompletionStageImpl<>(notNull("stage", stage));
    }

    public static <R> AwaitableCompletionStage<R> from(ThrowingSupplier<CompletionStage<R>> supp)
    {
        try
        {
            return of(supp.get());
        } catch (Exception | Error e)
        {
            return of(CompletableFuture.failedStage(e));
        }
    }

    public static AwaitableCompletionStage<Process> of(Process process)
    {
        return of(process.onExit());
    }

    // Override a few things we're likely to need to directly return
    // what we want.
    @Override
    <U> AwaitableCompletionStage<U> thenApply(Function<? super T, ? extends U> fn);

    @Override
    <U> AwaitableCompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn);

    @Override
    <U> AwaitableCompletionStage<U> thenApplyAsync(
            Function<? super T, ? extends U> fn, Executor executor);

    @Override
    <U> AwaitableCompletionStage<U> thenCompose(
            Function<? super T, ? extends CompletionStage<U>> fn);

    @Override
    <U> AwaitableCompletionStage<U> thenComposeAsync(
            Function<? super T, ? extends CompletionStage<U>> fn);

    @Override
    <U> AwaitableCompletionStage<U> thenComposeAsync(
            Function<? super T, ? extends CompletionStage<U>> fn, Executor executor);

    @Override
    <U> AwaitableCompletionStage<U> handle(
            BiFunction<? super T, Throwable, ? extends U> fn);

    @Override
    <U> AwaitableCompletionStage<U> handleAsync(
            BiFunction<? super T, Throwable, ? extends U> fn);

    @Override
    <U> AwaitableCompletionStage<U> handleAsync(
            BiFunction<? super T, Throwable, ? extends U> fn, Executor executor);
}
