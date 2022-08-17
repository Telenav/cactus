package com.telenav.cactus.cli.nuprocess;

import java.nio.ByteBuffer;

/**
 * In the case of running a cli application that <i>might</i> try to request
 * interactive input (effectively hanging the process) to kill the process
 * immediately, with an optional notification callback that will be run if that
 * happens.
 *
 * @author Tim Boudreau
 */
final class AbortOnInputStdinHandler implements StdinHandler
{

    private final Runnable callback;

    AbortOnInputStdinHandler(Runnable callback)
    {
        this.callback = callback;
    }

    AbortOnInputStdinHandler()
    {
        this(() ->
        {
        });
    }

    @Override
    public boolean onStdinReady(ProcessControl process, ByteBuffer bb)
    {
        if (bb.remaining() == 0)
        {
            return true;
        }
        try
        {
            callback.run();
        }
        finally
        {
            process.kill();
        }
        return false;
    }

}
