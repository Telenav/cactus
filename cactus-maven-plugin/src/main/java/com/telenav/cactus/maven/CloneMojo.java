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
import com.telenav.cactus.git.GitCommand;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.cli.ProcessResultConverter.strings;
import static com.telenav.cactus.git.GitCheckout.checkout;
import static com.telenav.cactus.maven.PrintMessageMojo.publishMessage;
import static com.telenav.cactus.maven.trigger.RunPolicies.LAST_CONTAINING_GOAL;
import static com.telenav.cactus.util.PathUtils.deleteFolderTree;
import static com.telenav.cactus.util.PathUtils.ifExists;
import static com.telenav.cactus.util.PathUtils.temp;
import static java.lang.Math.abs;
import static java.lang.System.currentTimeMillis;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * Clones the origin fetch url of the submodule root of whatever checkout it is
 * run from into a temporary folder, or a directory specified by
 * cactus.clone.dest/cloneDest, to create a fresh checkout of that content.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = VALIDATE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = SINGLETON,
        name = "clone", threadSafe = true)
@BaseMojoGoal("clone")
public class CloneMojo extends BaseMojo
{

    @Parameter(property = "cactus.clone.dest")
    private String cloneDest;

    @Parameter(property = "cactus.delete.clone.dest.if.exists")
    private boolean deleteIfExists;

    @Parameter(property = "cactus.development.branch", defaultValue = "develop")
    private String developmentBranch;

    @Parameter(property = "cactus.assets.branch", defaultValue = "publish")
    private String assetsBranch;

    public CloneMojo()
    {
        super(LAST_CONTAINING_GOAL);
    }

    private Path globalTempIfPossible()
    {
        // Use the global temp
        Path result = Paths.get("/tmp");
        return ifExists(result).orElse(temp());
    }

    private Path cloneDest()
    {
        if (cloneDest != null)
        {
            return Paths.get(cloneDest);
        }
        return globalTempIfPossible().resolve("cactus-clone-" + Long.toString(currentTimeMillis(), 36)
                + "-" + Integer.toString(abs(ThreadLocalRandom.current()
                        .nextInt()), 36));
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        checkout(project.getBasedir()).flatMap(co -> co
                .submoduleRoot().toOptional())
                .flatMap(root -> root.defaultRemote()).ifPresentOrElse(remote ->
        {
            quietly(() ->
            {
                Path dest = cloneDest();
                log.info("Clone dest is " + dest);
                log.info("Clone URL is " + remote.fetchUrl);
                if (deleteIfExists && exists(dest))
                {
                    deleteFolderTree(dest);
                }
                if (!exists(dest.getParent()))
                {
                    createDirectories(dest.getParent());
                }
                GitCommand<String> clo = new GitCommand<>(strings(),
                        dest.getParent(), "clone", remote.fetchUrl, dest
                        .getFileName().toString());

                String output = clo.run().awaitQuietly();
                log.info(output);

                log.info("Initializing submodules");
                log.info(new GitCommand<>(strings(), dest, "submodule", "init")
                        .run().awaitQuietly());

                log.info("Updating submodules");
                log.info(
                        new GitCommand<>(strings(), dest, "submodule", "update")
                                .run().awaitQuietly());

                log.warn(
                        "Cloned " + remote.fetchUrl + " into " + dest + ". Getting all submodules on development branches.");

                GitCheckout co = checkout(dest).get();
                co.switchToBranch(developmentBranch);
                co.submodules().ifPresent(subs ->
                {
                    subs.forEach(s ->
                    {
                        s.checkout().ifPresent(submodule ->
                        {
                            if (submodule.hasPomInRoot())
                            {
                                log.info(
                                        "Move submodule " + submodule.name() + " to branch '" + developmentBranch + "'");
                                submodule.switchToBranch(developmentBranch);
                            }
                            else
                            {
                                log.info(
                                        "Move submodule " + submodule.name() + " to branch '" + assetsBranch + "'");
                                submodule.switchToBranch(assetsBranch);
                            }
                            submodule.pull();
                        });
                    });
                });
                publishMessage(
                        "Cloned " + remote.fetchUrl + " into\ncheckout-root: " + dest,
                        session(), false);
                emitMessage(dest);
            });
        }, failingWith(
                "No git repository, or missing remote for submodule root of "
                + project.getBasedir()));
    }

}
