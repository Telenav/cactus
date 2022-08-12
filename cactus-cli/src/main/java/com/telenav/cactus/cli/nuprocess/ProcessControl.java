package com.telenav.cactus.cli.nuprocess;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Control interface for interacting with a process.
 *
 * @author Tim Boudreau
 */
public interface ProcessControl
{
    /**
     * Complete the passed CompletableFuture on exit.
     *
     * @param future A future
     */
    void onExit(CompletableFuture<ProcessResult> future);

    default CompletionStage<ProcessResult> onExit()
    {
        CompletableFuture<ProcessResult> result = new CompletableFuture<>();
        onExit(result);
        return result;
    }

    /**
     * Wait for process exit.
     *
     * @throws InterruptedException if something goes wrong
     */
    void await() throws InterruptedException;

    /**
     * Wait for process exit.
     *
     * @param dur How long to wait
     * @throws InterruptedException if something goes wrong
     */
    void await(Duration dur) throws InterruptedException;

    /**
     * Determine if a process is running. Will be false both before and after a
     * run.
     *
     * @return
     */
    boolean isRunning();

    /**
     * Forcibly kill the process.
     *
     * @return true if the process was killed
     */
    boolean kill();

    /**
     * Get the current exit code (will be -1 if still running, Integer.MAX_VALUE
     * if killed) and stdin / stdoout of the process. This method may be called
     * at any time, but may not return a useful result until process exit.
     *
     * @return A result
     */
    ProcessResult result();

    /**
     * Set up a handler for requests from the process for input.
     *
     * @param handler A handler
     * @param wantIn If true, notify the process
     * @return this
     */
    ProcessControl withStdinHandler(StdinHandler handler, boolean wantIn);

    int exitValue();
}
