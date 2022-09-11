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
package com.telenav.cactus.maven;

import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.commit.CommitMessage;
import com.telenav.cactus.maven.commit.CommitMessage.Section;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.SharedProjectTreeMojo;
import com.telenav.cactus.maven.shared.SharedDataKey;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.PUSH;
import static com.telenav.cactus.maven.trigger.RunPolicies.EVERY;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.KEEP_ALIVE;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VERIFY;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * A mojo specifically for generating a commit in dirty assets repositories.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings(
        {
            "unused", "DuplicatedCode"
        })
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = VERIFY,
        requiresDependencyResolution = NONE,
        instantiationStrategy = KEEP_ALIVE,
        name = "commit-assets", threadSafe = true)
@BaseMojoGoal("commit-assets")
public class CommitAssetsMojo extends SharedProjectTreeMojo
{

    /**
     * If true, also push those repositories that had commits generated.
     */
    @Parameter(property = PUSH, defaultValue = "false")
    boolean push;

    /**
     * If true, after committing, run <code>git gc --aggressive</code> - this
     * can make a substantial difference in how long the post-push back-end work
     * takes on Github's end before the push is complete and the connection is
     * closed.
     */
    @Parameter(property = "cactus.gc", defaultValue = "true")
    boolean gc;

    static SharedDataKey<Boolean> DONE = SharedDataKey.of("CommitAssetsMojo",
            Boolean.class);

    public CommitAssetsMojo()
    {
        super(EVERY);
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        Optional<Boolean> opt = sharedData().get(DONE);
        if (opt.isPresent() && opt.get())
        {
            return;
        }

        withProjectTree(tree ->
        {
            sharedData().put(DONE, true);
            Set<GitCheckout> assetsCheckouts = tree.nonMavenCheckouts();

            CommitMessage msg = new CommitMessage(CommitAssetsMojo.class,
                    "Update assets");
            Set<GitCheckout> toReallyCommit = new HashSet<>();
            try ( Section<CommitMessage> sect = msg.section(
                    "Assets Repositories"))
            {
                for (GitCheckout co : assetsCheckouts)
                {
                    boolean dirty = tree.isDirty(co) || co.hasUntrackedFiles();
                    if (dirty)
                    {
                        toReallyCommit.add(co);
                        log.info("Have dirty assets checkout " + co
                                .loggingName());
                        sect.bulletPoint(co.loggingName());
                    }
                }
            }
            if (!toReallyCommit.isEmpty())
            {
                String cm = msg.toString();
                for (GitCheckout co : toReallyCommit)
                {
                    log.info("Commit " + co.loggingName());
                    ifNotPretending(() ->
                    {
                        co.addAll();
                        co.commit(cm);
                    });
                    if (gc)
                    {
                        log.info(
                                "git gc --aggresive to improve push performance");
                        ifNotPretending(co::gc);
                    }
                    if (push)
                    {
                        log.info("Push " + co.loggingName());
                        ifNotPretending(co::push);
                    }
                    ifNotPretending(tree::invalidateCache);
                }
            }
        });
    }
}
