////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// © 2011-2022 Telenav, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
package com.telenav.cactus.process;

import com.telenav.cactus.process.internal.ProcessCallback;
import com.zaxxer.nuprocess.NuProcessBuilder;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.telenav.cactus.process.OutputHandler.NULL;

/**
 * Control interface for interacting with a process.
 *
 * @author Tim Boudreau
 * @param <O> The standard output type
 * @param <E> The standard error type
 */
public interface ProcessControl<O, E>
{
    /**
     * Complete the passed CompletableFuture on exit.
     *
     * @param future A future
     */
    void onExit(CompletableFuture<ProcessResult<O, E>> future);

    ProcessState state();

    default ProcessControl<O, E> killAfter(Duration maximumRunTime)
    {
        KillQueue.enqueue(maximumRunTime, this);
        return this;
    }

    /**
     * Returns the PID of the process, if there is one, -1 if not.
     *
     * @return A pid
     */
    default int processIdentifier()
    {
        return -1;
    }

    public static <O, E> ProcessControl<O, E> failure(Exception thrown)
    {
        return new FailedProcessControl<>(thrown);
    }

    default CompletionStage<ProcessResult<O, E>> onExit()
    {
        CompletableFuture<ProcessResult<O, E>> result = new CompletableFuture<>();
        onExit(result);
        return result;
    }

    /**
     * Create a new ProcessControl that uses strings for output, attaching it to
     * the passed NuProcessBuilder.
     *
     * @param bldr A builder
     * @return A ProcessControl
     */
    public static ProcessControl<String, String> create(NuProcessBuilder bldr)
    {
        notNull("bldr", bldr);
        ProcessCallback<String, String> result = ProcessCallback.create();
        notNull("result", result);
        bldr.setProcessListener(result);
        return result;
    }

    /**
     * Replaces the StandardInputHandler with one which will kill the process if
     * it requests input - this is useful which invoking command-line
     * applications which could possibly attempt to request interactive input,
     * when that is impossible.
     *
     * @return this
     */
    default ProcessControl<O, E> abortOnInput()
    {
        return withStandardInputHandler(new AbortOnInputStdinHandler(), true);
    }

    /**
     * Replaces the StdinHandler with one which will notify the passed Runnable
     * and kill the process if it requests input - this is useful which invoking
     * command-line applications which could possibly attempt to request
     * interactive input, when that is impossible.
     *
     * @param notificationCallback A callback
     * @return this
     */
    default ProcessControl<O, E> abortOnInput(Runnable notificationCallback)
    {
        return withStandardInputHandler(new AbortOnInputStdinHandler(
                notificationCallback), true);
    }

    /**
     * Provide a different OutputHandler for standard output; this method must
     * be called before this control has been attached to a process.
     *
     * @param <T> The new output result type
     * @param oh A handler
     * @return a new ProcessControl whose state is shared with this, which uses
     * the new output handler
     */
    <T> ProcessControl<T, E> withOutputHandler(OutputHandler<T> oh);

    /**
     * Provide a different OutputHandler for standard error; this method must be
     * called before this control has been attached to a process.
     *
     * @param <T> The new error result type
     * @param oe A handler
     * @return a new ProcessControl whose state is shared with this, which uses
     * the new output handler
     */
    <T> ProcessControl<O, T> withErrorHandler(OutputHandler<T> oe);

    /**
     * Returns a new ProcessControl that replaces the error and output handlers
     * with ones which ignore all output.
     *
     * @return A new ProcessControl
     */
    default ProcessControl<Void, Void> ignoringOutput()
    {
        return withOutputHandler(NULL).withErrorHandler(NULL);
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
     * at any time, but may not return a useful result until process exit. The
     * output objects in the result of a still-running process may be null - the
     * behavior is a contract between the {@link OutputHandler} used and the
     * caller. The default String-based implementation will return partial
     * output if there is any, and the empty string otherwise.
     *
     * @return A result
     */
    ProcessResult<O, E> result();

    /**
     * Set up a handler for requests from the process for input.
     *
     * @param handler A handler
     * @param wantIn If true, notify the process
     * @return this
     */
    ProcessControl<O, E> withStandardInputHandler(StandardInputHandler handler,
            boolean wantIn);

    /**
     * Get the exit value; follows the contract of
     * {@link ProcessState#effectiveExitCode() }
     *
     * @return An integer value
     */
    int exitValue();
}
