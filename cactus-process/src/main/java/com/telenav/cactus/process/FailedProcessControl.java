package com.telenav.cactus.process;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static com.mastfrog.util.preconditions.Exceptions.chuck;
import static com.telenav.cactus.process.ProcessState.INITIAL;

/**
 *
 * @author Tim Boudreau
 */
final class FailedProcessControl<StdOut, StdErr> implements ProcessControl<StdOut, StdErr>
{
    private final Exception thrown;

    FailedProcessControl(Exception thrown)
    {
        this.thrown = thrown;
    }

    @Override
    public void onExit(CompletableFuture<ProcessResult<StdOut, StdErr>> future)
    {
        future.completeExceptionally(thrown);
    }

    @Override
    public ProcessState state()
    {
        return INITIAL;
    }

    @Override
    public <T> ProcessControl<T, StdErr> withOutputHandler(OutputHandler<T> oh)
    {
        return chuck(thrown);
    }

    @Override
    public <T> ProcessControl<StdOut, T> withErrorHandler(OutputHandler<T> oe)
    {
        return chuck(thrown);
    }

    @Override
    public void await()
    {
        chuck(thrown);
    }

    @Override
    public void await(Duration ignored)
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
    public ProcessResult<StdOut, StdErr> result()
    {
        return new ProcessResultImpl<>(INITIAL, null, null);
    }

    @Override
    public ProcessControl<StdOut, StdErr> withStandardInputHandler(
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
