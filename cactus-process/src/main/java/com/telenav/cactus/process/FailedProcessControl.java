package com.telenav.cactus.process;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static com.mastfrog.util.preconditions.Exceptions.chuck;

/**
 *
 * @author Tim Boudreau
 */
final class FailedProcessControl<O, E> implements ProcessControl<O, E>
{

    private final Exception thrown;

    FailedProcessControl(Exception thrown)
    {
        this.thrown = thrown;
    }

    @Override
    public void onExit(CompletableFuture<ProcessResult<O, E>> future)
    {
        future.completeExceptionally(thrown);
    }

    @Override
    public ProcessState state()
    {
        return ProcessState.INITIAL;
    }

    @Override
    public <T> ProcessControl<T, E> withOutputHandler(OutputHandler<T> oh)
    {
        return chuck(thrown);
    }

    @Override
    public <T> ProcessControl<O, T> withErrorHandler(OutputHandler<T> oe)
    {
        return chuck(thrown);
    }

    @Override
    public void await() throws InterruptedException
    {
        chuck(thrown);
    }

    @Override
    public void await(Duration dur) throws InterruptedException
    {
        chuck(thrown);
    }

    @Override
    public boolean isRunning()
    {
        return false;
    }

    @Override
    public boolean kill()
    {
        return true;
    }

    @Override
    public ProcessResult<O, E> result()
    {
        return new ProcessResultImpl<>(ProcessState.INITIAL, null, null);
    }

    @Override
    public ProcessControl<O, E> withStandardInputHandler(
            StandardInputHandler handler, boolean wantIn)
    {
        return this;
    }

    @Override
    public int exitValue()
    {
        return 1;
    }

}
