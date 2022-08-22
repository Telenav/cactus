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

package com.telenav.cactus.git;

import com.mastfrog.util.preconditions.Checks;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.cli.CliCommand;
import com.telenav.cactus.cli.ProcessResultConverter;
import com.telenav.cactus.process.ProcessControl;
import com.zaxxer.nuprocess.NuProcessBuilder;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
public final class GitCommand<T> extends CliCommand<T>
{
    private final Path workingDir;
    private final String[] args;
    private final BuildLog log = BuildLog.get()
            .child(getClass().getSimpleName());

    public GitCommand(ProcessResultConverter<T> resultCreator, String... args)
    {
        this(resultCreator, null, args);
    }

    public GitCommand(ProcessResultConverter<T> resultCreator, Path workingDir,
            String... args)
    {
        super("git", resultCreator);
        this.workingDir = workingDir;
        this.args = Checks.notNull("args", args);
    }

    public GitCommand<T> withWorkingDir(Path dir)
    {
        return new GitCommand<>(resultCreator, dir, args);
    }

    @Override
    protected Optional<Path> workingDirectory()
    {
        return Optional.ofNullable(workingDir);
    }

    @Override
    protected void onLaunch(ProcessControl<String, String> proc)
    {
        log.debug(() -> "started: " + this);
        super.onLaunch(proc);
    }

    @Override
    protected void configureProcessBulder(NuProcessBuilder bldr,
            ProcessControl callback)
    {
        // As a sanity measure, if some command inadvertently tries
        // to invoke an interactive pager, ensure it is something that
        // exits immediately
        bldr.environment().put("GIT_PAGER", "/bin/cat");
        // Same reason - if something is going to pause asking for a password,
        // ensure we simply abort immediately
        bldr.environment().put("GIT_ASKPASS", "/usr/bin/false");
        // We do not want /etc/gitconfig to alter the behavior of the
        // plugin
        bldr.environment().put("GIT_CONFIG_NOSYSTEM", "1");
    }

    @Override
    protected void validate()
    {
        if (workingDir == null)
        {
            throw new IllegalStateException("Command is a template. Use "
                    + "withWorkingDir() to get an instance that has "
                    + "somewhere to run.");
        }
    }

    @Override
    protected void configureArguments(List<String> list)
    {
        // Want this for everything
        list.add("--no-pager");
        // Pending - we should probably modify GitCheckout.push() and
        // friends to either explicitly pass what they intend, or to
        // use this there.  But for our purposes, we are assuming remote
        // branches match local branches.
        list.add("-c");
        list.add("push.default=current");
        list.addAll(Arrays.asList(args));
    }

}
