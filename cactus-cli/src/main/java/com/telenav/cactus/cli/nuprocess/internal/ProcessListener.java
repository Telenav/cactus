package com.telenav.cactus.cli.nuprocess.internal;

/**
 *
 * @author Tim Boudreau
 */
public interface ProcessListener
{
    void processExited(int exitCode, CharSequence stdout, CharSequence stderr);

}
