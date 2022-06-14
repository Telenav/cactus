package com.telenav.cactus.maven.util;

import java.util.function.Supplier;

/**
 * Aggregates process output and status as an exception.
 *
 * @author Tim Boudreau
 */
public final class ProcessFailedException extends RuntimeException
{

    public final Process process;
    public final String stdout;
    public final String stderr;
    public final String command;

    public ProcessFailedException(Supplier<String> supp, Process process, String stdout, String stderr)
    {
        this.process = process;
        this.stdout = stdout;
        this.stderr = stderr;
        this.command = supp.get();
    }

    @Override
    public String getMessage()
    {
        StringBuilder result = new StringBuilder(command);

        result.append(" exited ").append(process.exitValue());
        if (!stdout.isBlank())
        {
            result.append('\n').append(stdout);
        }
        if (!stderr.isBlank())
        {
            result.append('\n').append(stderr);
        }
        return result.toString();
    }
}
