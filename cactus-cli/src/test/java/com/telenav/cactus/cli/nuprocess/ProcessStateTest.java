package com.telenav.cactus.cli.nuprocess;

import org.junit.jupiter.api.Test;

import static com.telenav.cactus.cli.nuprocess.ProcessState.RunningStatus.EXITED;
import static com.telenav.cactus.cli.nuprocess.ProcessState.RunningStatus.RUNNING;
import static com.telenav.cactus.cli.nuprocess.ProcessState.RunningStatus.UNINITIALIZED;
import static org.junit.jupiter.api.Assertions.*;
import static com.telenav.cactus.cli.nuprocess.ProcessState.RunningStatus.STARTING;

/**
 *
 * @author Tim Boudreau
 */
public class ProcessStateTest
{

    @Test
    public void testTransitions()
    {
        assertTrue(true);
        ProcessState state = ProcessState.INITIAL;

        assertFalse(state.isExited(), state::toString);
        assertFalse(state.isRunning(), state::toString);
        assertEquals(0, state.exitCode());
        assertFalse(state.wantsInput());
        assertFalse(state.wasKilled());
        assertSame(UNINITIALIZED, state.state(), state::toString);

        ProcessState next = state.toState(STARTING);

        assertSame(STARTING, next.state(), next::toString);

        next = next.wantingInput();

        next = next.toState(RUNNING);
        assertSame(RUNNING, next.state(), next::toString);
        assertEquals(0, next.exitCode(), next::toString);
        assertTrue(next.wantsInput(), next::toString);
        assertFalse(next.wasKilled(), next::toString);

        next = next.killed();
        assertSame(RUNNING, next.state(), next::toString);
        assertEquals(0, next.exitCode(), next::toString);
        assertTrue(next.wantsInput(), next::toString);
        assertTrue(next.wasKilled(), next::toString);

        next = next.notWantingInput();
        assertFalse(next.wantsInput(), next::toString);
        assertTrue(next.wasKilled(), next::toString);
        assertEquals(0, next.exitCode(), next::toString);
        assertSame(RUNNING, next.state(), next::toString);

        next = next.withExitCode(113);
        assertEquals(113, next.exitCode(), next::toString);
        assertSame(EXITED, next.state(), next::toString);
        assertFalse(next.wantsInput(), next::toString);
        assertTrue(next.wasKilled(), next::toString);
    }

}
