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
import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

/**
 * Specialization of OutputHandler for Strings which can perform some common
 * transforms on the output.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
public interface StringOutputHandler extends OutputHandler<String>
{

    /**
     * Converts this into an OutputHandler which returns
     * <code>Optional.empty()</code> if the output is empty, all whitespace or
     * null.
     *
     * @return An output handler
     */
    default OutputHandler<Optional<String>> ifNonEmpty()
    {
        return new OutputHandler<>()
        {
            @Override
            public void onOutput(
                    ProcessControl<?, ?> process, ByteBuffer output,
                    boolean closed)
            {
                StringOutputHandler.this.onOutput(process, output, closed);
            }

            @Override
            public Optional<String> result()
            {
                String result = StringOutputHandler.this.result();
                return result == null || result.isBlank()
                       ? empty()
                       : of(result.trim());
            }
        };
    }

    /**
     * Converts to an output handler that handles nulls (which you can get if
     * the process times out or is killed).
     *
     * @return An output handler
     */
    default OutputHandler<Optional<String>> optional()
    {
        return new OutputHandler<>()
        {
            @Override
            public void onOutput(
                    ProcessControl<?, ?> process, ByteBuffer output,
                    boolean closed)
            {
                StringOutputHandler.this.onOutput(process, output, closed);
            }

            @Override
            public Optional<String> result()
            {
                return ofNullable(StringOutputHandler.this.result());
            }
        };
    }

    /**
     * Returns a StringOutputHandler that trims whitespace from output before
     * returning it; many if not most cli tools emit a trailing newline.
     *
     * @return A string output handler that transforms output to its trimmed
     * form
     */
    default StringOutputHandler trimmed()
    {
        return new StringOutputHandler()
        {
            @Override
            public void onOutput(
                    ProcessControl<?, ?> process, ByteBuffer output,
                    boolean closed)
            {
                StringOutputHandler.this.onOutput(process, output, closed);
            }

            @Override
            public String result()
            {
                String originalResult = StringOutputHandler.this.result();
                return originalResult == null
                       ? null
                       : originalResult.trim();
            }
        };
    }
}
