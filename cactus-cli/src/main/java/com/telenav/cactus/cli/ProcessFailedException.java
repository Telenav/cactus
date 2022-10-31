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

package com.telenav.cactus.cli;

import com.telenav.cactus.process.ProcessControl;
import java.util.function.Supplier;

/**
 * Aggregates process output and status as an exception.
 *
 * @author Tim Boudreau
 */
public final class ProcessFailedException extends RuntimeException
{
    public final ProcessControl<?, ?> process;
    public final String stdout;
    public final String stderr;
    public final String command;

    public ProcessFailedException(Supplier<String> command,
                                  ProcessControl<?, ?> process,
                                  String stdout,
                                  String stderr)
    {
        this.process = process;
        this.stdout = stdout;
        this.stderr = stderr;
        this.command = command.get();
    }

    @Override
    public String getMessage()
    {
        StringBuilder result = new StringBuilder(command);
        result.append(" exited ").append(process.exitValue());
        if (!stdout.isBlank())
        {
            result.append('\n').append(stdout);
        }
        if (!stderr.isBlank())
        {
            result.append('\n').append(stderr);
        }
        return result.toString();
    }
}
