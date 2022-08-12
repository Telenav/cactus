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
package com.telenav.cactus.cli.nuprocess;

import com.telenav.cactus.cli.nuprocess.internal.ProcessCallback;
import com.zaxxer.nuprocess.NuProcessBuilder;
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

    public static ProcessControl create(NuProcessBuilder bldr)
    {
        ProcessCallback result = new ProcessCallback();
        bldr.setProcessListener(result);
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
