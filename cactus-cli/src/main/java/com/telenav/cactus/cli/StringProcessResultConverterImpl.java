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
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.process.ProcessControl;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import static com.telenav.cactus.cli.CliCommand.completionStageForProcess;

/**
 *
 * @author Tim Boudreau
 */
final class StringProcessResultConverterImpl implements
        StringProcessResultConverter
{
    final IntPredicate exitCodeTest;
    private final BuildLog log = BuildLog.get();

    public StringProcessResultConverterImpl(IntPredicate exitCodeTest)
    {
        this.exitCodeTest = exitCodeTest;
    }

    public StringProcessResultConverterImpl()
    {
        this(code -> code == 0);
    }

    @Override
    public AwaitableCompletionStage<String> onProcessStarted(
            Supplier<String> description, ProcessControl<String, String> process)
    {
        Exception launched = new Exception();
        // Note:  This really needs to be thenApplyAsync(), or you sometimes get
        // immediately called back before the process has *started*.
        return completionStageForProcess(process).thenApply(result ->
        {
            log.debug(() -> "exit "
                    + result.exitValue()
                    + ":\n"
                    + result.standardOutput() + "\n"
                    + (result.exitValue() != 0
                        ? result.standardError()
                        : ""));
            if (exitCodeTest.test(result.exitValue()))
            {
                return result.standardOutput();
            }
            var ex = new ProcessFailedException(description,
                    process, result.standardOutput(),
                    result.standardError());
            ex.initCause(launched);
            throw ex;
        });
    }

}
