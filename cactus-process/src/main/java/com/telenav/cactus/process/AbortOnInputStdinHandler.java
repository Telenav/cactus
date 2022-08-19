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

import java.nio.ByteBuffer;

/**
 * In the case of running a cli application that <i>might</i> try to request
 * interactive input (effectively hanging the process) to kill the process
 * immediately, with an optional notification callback that will be run if that
 * happens.
 *
 * @author Tim Boudreau
 */
final class AbortOnInputStdinHandler implements StandardInputHandler
{

    private final Runnable callback;

    AbortOnInputStdinHandler(Runnable callback)
    {
        this.callback = callback;
    }

    AbortOnInputStdinHandler()
    {
        this(() ->
        {
        });
    }

    @Override
    public boolean onStdinReady(ProcessControl process, ByteBuffer bb)
    {
        if (bb.remaining() == 0)
        {
            return true;
        }
        try
        {
            callback.run();
        }
        finally
        {
            process.kill();
        }
        return false;
    }

}
