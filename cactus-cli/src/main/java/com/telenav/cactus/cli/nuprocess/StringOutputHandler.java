package com.telenav.cactus.cli.nuprocess;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Default implementation of an output handler which concatenates a string.
 *
 * @author Tim Boudreau
 */
final class StringOutputHandler implements OutputHandler<String>
{
    private final StringBuilder output = new StringBuilder();

    @Override
    public synchronized void onOutput(ByteBuffer bb, boolean closed)
    {
        int len = bb.remaining();
        if (len > 0)
        {
            byte[] bytes = new byte[len];
            bb.get(bytes);
            output.append(new String(bytes, UTF_8));
        }
    }

    @Override
    public synchronized String result()
    {
        return output.toString();
    }

    @Override
    public String toString()
    {
        return result();
    }

}
