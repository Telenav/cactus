package com.telenav.cactus.process;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * Implementation of ProcessResult.
 *
 * @author Tim Boudreau
 */
final class ProcessResultImpl<O, E> extends ProcessResult<O, E>
{
    private final ProcessState state;
    private final O stdout;
    private final E stderr;

    ProcessResultImpl(ProcessState state, O stdout,
            E stderr)
    {
        this.state = notNull("state", state);
        this.stdout = stdout;
        this.stderr = stderr;
    }

    @Override
    public ProcessState state()
    {
        return state;
    }

    @Override
    public O standardOutput()
    {
        return stdout;
    }

    @Override
    public E standardError()
    {
        return stderr;
    }

}
