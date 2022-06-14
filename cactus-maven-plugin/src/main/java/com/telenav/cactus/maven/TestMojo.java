////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// © 2022 Telenav, Inc.
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

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * A place holder for testing stuff.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        name = "do-something", threadSafe = true)
public class TestMojo extends BaseMojo
{
    @Parameter(property = "telenav.thing", defaultValue = "not really a thing")
    private String thing;

    @Override
    public void performTasks(BuildLog buildLog, MavenProject project) throws Exception
    {
        buildLog.child("blee").child("blah").child("blorg").info(
                "This is the build log:");

        buildLog.info(
                "\n--------------------- Cactus Maven Plugin Says ---------------------");
        buildLog.info("You are building " + project.getGroupId() + ":"
                + project.getArtifactId() + ":" + project.getVersion());
        buildLog.info("The thing is '" + thing + "'");

        if (true)
        {
            return;
        }

        Optional<GitCheckout> repoOpt = GitCheckout.repository(project
                .getBasedir());
        if (!repoOpt.isPresent())
        {
            throw new MojoFailureException("Uh oh, no git in " + project
                    .getBasedir());
        }
        GitCheckout repo = repoOpt.get();

        buildLog.error("Remotes: " + repo.defaultRemote().get());

        buildLog.error("Branches:\n" + repo.branches());
        buildLog.error("Remote Names:\n" + repo.remoteProjectNames());

        buildLog.info("You are on branch: " + repo.branch());
        buildLog.info("Submodule root: " + repo.submoduleRoot());

        buildLog.info("Submodules:");
        repo.submodules().ifPresent(subs ->
        {
            subs.forEach(sub ->
            {
                buildLog.info(" * " + sub);
                buildLog.info("   * " + sub.repository().get().branch()
                        + " dirty? " + sub.repository().get()
                                .hasUncommitedChanges());
                sub.repository().ifPresent(re ->
                {
                    System.out.println("  * " + re.remoteProjectNames());
                    re.scanForPomFiles(pom ->
                    {
                        Path relPath = re.checkoutRoot().relativize(pom
                                .getParent());
                        buildLog.info(
                                "    * " + (relPath.toString().length() == 0
                                            ? "(root)"
                                            : relPath.toString()));
                        Pom.from(pom).ifPresent(info ->
                        {
                            buildLog.info("      * " + info);
                        });
                    });
                });
            });
        });

        // ProjectTree is pretty well the thing that can tell us *everything* about
        // the entire checkout environment we're in - and caches results of running
        // git so it's fast (you can invalidate it if you need to).
        ProjectTree.from(project.getBasedir().toPath()).ifPresent(tree ->
        {
            buildLog.warn("Root: " + tree.root());
        });

        buildLog.info(
                "---------------------------------------------------------------------\n");
    }
}
