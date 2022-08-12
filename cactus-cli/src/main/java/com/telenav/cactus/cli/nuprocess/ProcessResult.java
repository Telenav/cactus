package com.telenav.cactus.cli.nuprocess;

/**
 *
 * @author Tim Boudreau
 */
public final class ProcessResult
{
    public final int exitCode;
    public final String stdout;
    public final String stderr;

    public ProcessResult(int exitCode, CharSequence stdout, CharSequence stderr)
    {
        this.exitCode = exitCode;
        synchronized (stdout)
        {
            this.stdout = stdout.toString();
        }
        synchronized (stderr)
        {
            this.stderr = stderr.toString();
        }
    }

    public boolean isOk()
    {
        return exitCode == 0;
    }

    public boolean hasExited()
    {
        return exitCode >= 0;
    }
    
    public boolean wasKilled() {
        return Integer.MAX_VALUE == exitCode;
    }

    @Override
    public String toString()
    {
        return exitCode + "\n" + stdout + "\n" + stderr;
    }
    
    public int exitValue() { // for compatibility with Process
        return exitCode;
    }

}
