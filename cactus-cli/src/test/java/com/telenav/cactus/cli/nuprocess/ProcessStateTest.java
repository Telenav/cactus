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

import com.telenav.cactus.process.ProcessState;
import org.junit.jupiter.api.Test;

import static com.telenav.cactus.process.ProcessState.RunningStatus.EXITED;
import static com.telenav.cactus.process.ProcessState.RunningStatus.RUNNING;
import static com.telenav.cactus.process.ProcessState.RunningStatus.UNINITIALIZED;
import static org.junit.jupiter.api.Assertions.*;
import static com.telenav.cactus.process.ProcessState.RunningStatus.STARTING;

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
