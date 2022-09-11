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

/**
 * Result of running a process; note results may be created while the process is
 * still running - the ProcessState describes the state of the process at the
 * time it was created.
 *
 * @author Tim Boudreau
 * @param <O> The type the standard output is converted into
 * @param <E> The type the standard error output is converted into
 */
public abstract class ProcessResult<O, E>
{

    ProcessResult()
    {

    }

    public static <O, E> ProcessResult<O, E> create(ProcessState state,
            O stdout, E stderr)
    {
        return new ProcessResultImpl<>(state, stdout, stderr);
    }

    /**
     * Get the snapshot of the state of the process at the time this result was
     * created.
     *
     * @return The state
     */
    public abstract ProcessState state();

    /**
     * Returns the standard output of the process.
     *
     * @return An object
     */
    public abstract O standardOutput();

    /**
     * Get the standard error output of the process.
     *
     * @return An object
     */
    public abstract E standardError();

    /**
     * Determine if the process has completed, unkilled, with an exit code of
     * zero.
     *
     * @return true if the process succeeded
     */
    public final boolean isSuccess()
    {
        return !wasKilled() && state().isExited() && state().exitCode() == 0;
    }

    /**
     * Determine if the process has exited.
     *
     * @return true if the process is no longer running
     */
    public final boolean hasExited()
    {
        return state().isExited();
    }

    /**
     * Determine if the process exited because it was killed programmatically.
     *
     * @return true if the process was killed
     */
    public final boolean wasKilled()
    {
        return state().wasKilled();
    }

    @Override
    public final String toString()
    {
        return state() + "\n" + standardOutput() + "\n" + standardError();
    }

    /**
     * Get a (modified) exit value. Negative if still running, Integer.MAX_VALUE
     * (above any possible 16 bit exit code) for killed, otherwise the actual
     * exit code.
     *
     * @return An exit code
     */
    public final int exitValue()
    {
        return state().effectiveExitCode();
    }

}
