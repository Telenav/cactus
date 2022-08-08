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
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.tree.ProjectTree;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.List;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Starts and finishes branches according to git flow branching conventions.
 *
 * @author jonathanl (shibo)
 */
@SuppressWarnings(
        {
                "unused", "DuplicatedCode"
        })
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "git-merge-pull-request", threadSafe = true)
@BaseMojoGoal("git-merge-pull-request")
public class GitMergePullRequestMojo extends ScopedCheckoutsMojo
{
    @Parameter(property = "cactus.authentication-token", required = true)
    private String authenticationToken;

    @Parameter(property = "cactus.branch-name", required = true)
    private String branchName;

    @Override
    protected void execute(BuildLog log, MavenProject project,
                           GitCheckout myCheckout,
                           ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {
        if (checkouts.isEmpty())
        {
            log.info("No checkouts matched.");
            return;
        }

        for (var checkout : checkouts)
        {
            checkout.mergePullRequest(authenticationToken, branchName);
        }
    }

    @Override
    protected void onValidateParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        if (authenticationToken.isBlank())
        {
            throw new RuntimeException("Must supply github authentication token");
        }
    }
}
