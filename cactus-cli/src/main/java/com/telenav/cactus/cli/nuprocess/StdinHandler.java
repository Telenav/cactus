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

import java.nio.ByteBuffer;

/**
 * Callback interface for handling stdin when the process requests it.
 *
 * @author Tim Boudreau
 */
public interface StdinHandler
{
    /**
     * Returns a do-nothing StdinHandler.
     */
    static final StdinHandler DEFAULT = new DefaultStdinHandler();

    /**
     * Called with a byte buffer that can be written into (up to its remainder)
     * when the application requests input. Return true to notify the
     * application that there is more input if the buffer is not large enough to
     * accommodate all you want to write.
     * <p>
     * If the buffer is written to, it must be flipped before returning.
     * </p>
     *
     * @param process A process
     * @param bb A byte buffer
     * @return true if there is more data to write
     */
    boolean onStdinReady(ProcessControl process, ByteBuffer bb);

}
