package com.telenav.cactus.process;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * Implementation of ProcessResult.
 *
 * @author Tim Boudreau
 */
final class ProcessResultImpl<StdOut, StdErr> extends ProcessResult<StdOut, StdErr>
{
    private final ProcessState state;

    private final StdOut stdout;

    private final StdErr stderr;

    ProcessResultImpl(ProcessState state,
                      StdOut stdout,
                      StdErr stderr)
    {
        this.state = notNull("state", state);
        this.stdout = stdout;
        this.stderr = stderr;
    }

    @Override
    public StdErr standardError()
    {
        return stderr;
    }

    @Override
    public StdOut standardOutput()
    {
        return stdout;
    }

    @Override
    public ProcessState state()
    {
        return state;
    }
}
