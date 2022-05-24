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

import com.telenav.cactus.maven.cli.Git;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * A Maven mojo.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        name = "do-something", threadSafe = true)
public class TestMojo extends BaseMojo
{

    @Parameter(property = "thing", defaultValue = "not really a thing")
    private String thing;

    @Override
    public void performTasks(BuildLog buildLog, MavenProject project) throws Exception
    {
        buildLog.child("blee").child("blah").child("blorg").info("This is the build log:");

        System.out.println("\n--------------------- Cactus Maven Plugin Says ---------------------");
        System.out.println("You are building " + project.getGroupId() + ":"
                + project.getArtifactId() + ":" + project.getVersion());
        System.out.println("The thing is '" + thing + "'");

        Optional<Git> repoOpt = Git.repository(project.getBasedir());
        if (!repoOpt.isPresent())
        {
            throw new MojoFailureException("Uh oh, no git in " + project.getBasedir());
        }
        Git repo = repoOpt.get();

        System.out.println("You are on branch: " + repo.branch());
        System.out.println("Submodule root: " + repo.submoduleRoot());

        System.out.println("Submodules:");
        repo.submodules().ifPresent(subs ->
        {
            subs.forEach(sub ->
            {
                System.out.println(" * " + sub);
                try
                {
                    System.out.println("   * " + sub.repository().get().branch()
                            + " dirty? " + sub.repository().get().hasUncommitedChanges());
                } catch (InterruptedException ex)
                {
                    Logger.getLogger(TestMojo.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        });

        System.out.println("---------------------------------------------------------------------\n");
    }
}
