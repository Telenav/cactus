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
 * Encapsulates the state of a process in a single 32 bit int that can be used
 * with atomics. The first 2 bits encapsulate whether or not the process is
 * running, covering pre-initialization, initialized, running and exited,
 * whether it has been killed, and if input is expected to be requested. This
 * ensures that the reported state of a process is always consistent, without
 * any risk of deadlock.
 *
 * @author Tim Boudreau
 */
public final class ProcessState
{

    private static final int STATE_MASK = 0b11;
    private final int value;
    private static final RunningStatus[] STATII = RunningStatus.values();
    public static final ProcessState INITIAL = new ProcessState();

    private ProcessState(int value)
    {
        this.value = value;
    }

    public static ProcessState processState(int value)
    {
        switch (value)
        {
            case 0:
                return INITIAL;
            default:
                int stateOrdinal = value & STATE_MASK;
                if (stateOrdinal >= STATII.length)
                {
                    throw new IllegalArgumentException(
                            "State ordinal out of range: " + stateOrdinal);
                }
                return new ProcessState(value);
        }
    }

    ProcessState()
    {
        this(0);
    }

    /**
     * Get the numeric exit code of the process. Note this will be zero for a
     * process that is still running - check <code>isExited()</code> before
     * using.
     *
     * @return An exit code
     */
    public int exitCode()
    {
        return value >> 16;
    }

    /**
     * Returns an exit code suitable for numeric tests, as with code written for
     * calls to <code>Process.exitValue()</code>. Returns -1 if the process has
     * not started or is running, Integer.MAX_VALUE if it was killed, and
     * otherwise the actual exit code. Note that the killed flag may be set
     * before a process has actually terminated and obtained a real exit code.
     *
     * @return An exit code that is non-zero when running and can indicate
     * having been killed
     */
    public int effectiveExitCode()
    {
        if (wasKilled())
        {
            return Integer.MAX_VALUE;
        }
        else
            if (isBeforeStart() || isRunning())
            {
                return -1;
            }
        return exitCode();
    }

    /**
     * Copy this state, replacing the run status with EXITED and the exit code
     * with the passed value.
     *
     * @param code A value no less than zero or greater than 32768.
     * @return A new process state
     */
    public ProcessState withExitCode(int code)
    {
        if (code < 0 || code > 32768)
        {
            throw new IllegalArgumentException("Exit code out of range: " + code);
        }
        int masked = value & 0x00FF;
        int newValue = masked
                | RunningStatus.EXITED.ordinal()
                | code << 16;
        return new ProcessState(newValue);
    }

    /**
     * Create a copy of this state with this stdin request bit set.
     *
     * @return A state
     */
    public ProcessState wantingInput()
    {
        if (wantsInput())
        {
            return this;
        }
        return new ProcessState(value | 0b1000);
    }

    /**
     * Create a copy of this state with the input request bit cleared.
     *
     * @return a state
     */
    public ProcessState notWantingInput()
    {
        if (!wantsInput())
        {
            return this;
        }
        return new ProcessState(value & ~0b1000);
    }

    /**
     * Determine if the input request bit is set.
     *
     * @return Whether or not input is requested
     */
    public boolean wantsInput()
    {
        return (value & 0b1000) != 0;
    }

    /**
     * Create a copy of this state with the killed bit set.
     *
     * @return A state
     */
    public ProcessState killed()
    {
        return (value & 0b100) == 0
               ? new ProcessState(value | 0b100)
               : this;
    }

    /**
     * Determine if the killed bit is set.
     *
     * @return Whether or not the process was killed (which does not necessarily
     * mean it has already exited)
     */
    public boolean wasKilled()
    {
        return (value & 0b100) != 0;
    }

    /**
     * Get the current lifecycle state of the process.
     *
     * @return The lifecycle state
     */
    public RunningStatus state()
    {
        return STATII[value & 0b11];
    }

    /**
     * Create a copy of this state with the passed lifecycle status.
     *
     * @param nue The new status
     * @return A state
     */
    public ProcessState toState(RunningStatus nue)
    {
        if (state() == nue)
        {
            return this;
        }
        int mask = ~0b11;
        int newValue = (value & mask) | nue.ordinal();
        return value == newValue
               ? this
               : new ProcessState(newValue);
    }

    /**
     * Get the raw integer value this state represents.
     *
     * @return An integer state
     */
    public int intValue()
    {
        return value;
    }

    /**
     * Determine if the process was running at the time of this instance's
     * creation.
     *
     * @return Whether or not the process was running
     */
    public boolean isRunning()
    {
        return state().isRunning();
    }

    /**
     * Determine if the process had exited at the time of this instance's
     * creation.
     *
     * @return Whether or not it had exited
     */
    public boolean isExited()
    {
        return state().isExited();
    }

    @Override
    public String toString()
    {
        int code = exitCode();
        return state() + (wantsInput()
                          ? " input"
                          : "")
                + (wasKilled()
                   ? " killed"
                   : "")
                + " " + code
                + " (" + Integer.toHexString(value) + ")";
    }

    /**
     * WHether or not the process has been started.
     *
     * @return true if it has not yet entered the running state.
     */
    public boolean isBeforeStart()
    {
        return state().isBeforeStart();
    }

    @Override
    public boolean equals(Object o)
    {
        return o != null && ProcessState.class == o.getClass()
                && ((ProcessState) o).intValue() == value;
    }

    @Override
    public int hashCode()
    {
        return value;
    }

    /**
     * A phase of a runnable process's lifecycle - note these are states a
     * process <i>can be in</i> - whether or not the process was killed is not
     * one of them, since it is a thing that results in a state of EXITED.
     */
    public enum RunningStatus
    {
        /**
         * No attempt has yet been made to start the process.
         */
        UNINITIALIZED,
        /**
         * No failure has been encountered preparing to start the process, but
         * it has not yet been launched.
         */
        STARTING,
        /**
         * The process has been launched and is running.
         */
        RUNNING,
        /**
         * The process has exited in some manner.
         */
        EXITED;

        @Override
        public String toString()
        {
            return name().toLowerCase().replace('_', '-');
        }

        public boolean isBeforeStart()
        {
            return this == UNINITIALIZED || this == STARTING;
        }

        public boolean isRunning()
        {
            return this == RUNNING;
        }

        public boolean isExited()
        {
            return this == EXITED;
        }

        public boolean isStarted()
        {
            return isRunning() || isExited();
        }
    }
}
