package com.telenav.cactus.cli.nuprocess;

import com.zaxxer.nuprocess.NuProcess;
import java.nio.ByteBuffer;

/**
 * Callback interface for handling stdin when the process requests it.
 *
 * @author Tim Boudreau
 */
public interface StdinHandler
{
    static final StdinHandler DEFAULT = new DefaultStdinHandler();

    /**
     * Called with a byte buffer that can be written into (up to its remainder)
     * when the application requests input. Return true to notify the
     * application that there is more input if the buffer is not large enough to
     * accommodate all you want to write.
     *
     * @param process A process
     * @param bb A byte buffer
     * @return true if there is more data to write
     */
    boolean onStdinReady(ProcessControl process, ByteBuffer bb);

}
