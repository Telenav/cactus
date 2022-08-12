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

/**
 *
 * @author Tim Boudreau
 */
public final class ProcessResult
{
    public final ProcessState state;
    public final String stdout;
    public final String stderr;

    public ProcessResult(ProcessState state, CharSequence stdout,
            CharSequence stderr)
    {
        this.state = state;
        synchronized (stdout)
        {
            this.stdout = stdout.toString();
        }
        synchronized (stderr)
        {
            this.stderr = stderr.toString();
        }
    }

    public boolean isSuccess()
    {
        return state.isExited() && state.exitCode() == 0;
    }

    public boolean hasExited()
    {
        return state.isExited();
    }

    public boolean wasKilled()
    {
        return state.wasKilled();
    }

    @Override
    public String toString()
    {
        return state + "\n" + stdout + "\n" + stderr;
    }

    /**
     * Get a (modified) exit value.  Negative if still running,
     * Integer.MAX_VALUE (above any possible 16 bit exit code) for killed,
     * otherwise the actual exit code.
     * 
     * @return An exit code
     */
    public int exitValue()
    { 
        return state.effectiveExitCode();
    }

}
