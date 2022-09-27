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

import org.junit.jupiter.api.Test;
import com.zaxxer.nuprocess.NuProcessBuilder;
import com.zaxxer.nuprocess.NuProcess;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Tim Boudreau
 */
public class KillQueueTest
{

    @Test
    public void testProcessIsKilled() throws InterruptedException
    {
        AtomicReference<ProcessControl<?, ?>> ref = new AtomicReference<>();
        KillQueue.VERIFIER = ref::set;

        NuProcessBuilder nu = new NuProcessBuilder("sleep", "20000");
        ProcessControl<String, String> ctrl = ProcessControl.create(nu)
                .killAfter(Duration.ofMillis(400));
        NuProcess proc = nu.start();
        assertTrue(KillQueue.isStarted());

        ctrl.await(Duration.ofSeconds(21));

        assertNotEquals(0, ctrl.state().exitCode());
        assertNotNull(ref.get());
        assertSame(ctrl, ref.get());
        assertFalse(proc.isRunning());
    }

}
