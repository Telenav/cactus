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

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Default implementation of an output handler which concatenates a string.
 *
 * @author Tim Boudreau
 */
final class StringOutputHandlerImpl implements StringOutputHandler
{
    private final StringBuilder output = new StringBuilder();

    @Override
    public synchronized void onOutput(ProcessControl<?, ?> process,
            ByteBuffer bb, boolean closed)
    {
        int len = bb.remaining();
        if (len > 0)
        {
            byte[] bytes = new byte[len];
            bb.get(bytes);
            output.append(new String(bytes, UTF_8));
        }
    }

    @Override
    public synchronized String result()
    {
        return output.toString();
    }

    @Override
    public String toString()
    {
        return result();
    }

}
