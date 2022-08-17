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
import java.util.Optional;

/**
 * Specialization of OutputHandler for Strings which can perform some common
 * transforms on the output.
 *
 * @author Tim Boudreau
 */
public interface StringOutputHandler extends OutputHandler<String>
{

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
                String res = StringOutputHandler.this.result();
                return res == null || res.isBlank()
                       ? Optional.empty()
                       : Optional.of(res.trim());
            }
        };
    }

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
                return Optional.ofNullable(StringOutputHandler.this.result());
            }
        };
    }

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
