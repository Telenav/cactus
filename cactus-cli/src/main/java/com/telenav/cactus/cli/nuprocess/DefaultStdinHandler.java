package com.telenav.cactus.cli.nuprocess;

import java.nio.ByteBuffer;

/**
 *
 * @author Tim Boudreau
 */
class DefaultStdinHandler implements StdinHandler
{
    @Override
    public boolean onStdinReady(ProcessControl process, ByteBuffer bb)
    {
        System.err.println(
                "Stdin request from " + process + " but no handler was set up");
        return false;
    }

}
