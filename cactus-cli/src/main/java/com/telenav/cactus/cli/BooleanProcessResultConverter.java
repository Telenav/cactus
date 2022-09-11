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

import com.mastfrog.concurrent.future.AwaitableCompletionStage;
import com.telenav.cactus.process.ProcessControl;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import static com.telenav.cactus.cli.CliCommand.completionStageForProcess;

/**
 * Simple process result conversion when the only thing you care about is the
 * exit code.
 *
 * @author Tim Boudreau
 */
final class BooleanProcessResultConverter implements
        ProcessResultConverter<Boolean>
{

    private final IntPredicate exitCodeTest;

    BooleanProcessResultConverter(IntPredicate exitCodeTest)
    {
        this.exitCodeTest = exitCodeTest;
    }

    BooleanProcessResultConverter()
    {
        this(code -> code == 0);
    }

    @Override
    public AwaitableCompletionStage<Boolean> onProcessStarted(
            Supplier<String> supp, ProcessControl<String, String> process)
    {
        return completionStageForProcess(process).thenApply(proc ->
        {
            return exitCodeTest.test(proc.exitValue());
        });
    }

}
