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
package com.telenav.cactus.test.project;

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.model.ArtifactId;
import com.telenav.cactus.maven.model.ArtifactIdentifiers;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.test.project.generator.MavenCommand;
import java.nio.file.Path;

import static com.telenav.cactus.git.GitCheckout.checkout;

/**
 * Wrapper for a pom project, which is less explicitly modeled by
 * GeneratedProjects.
 *
 * @author Tim Boudreau
 */
final class WrappedPomProject implements ProjectWrapper
{
    private Pom pom;
    private GitCheckout checkout;
    private final GeneratedProjectTree<?> outer;

    WrappedPomProject(Pom pom, final GeneratedProjectTree<?> outer)
    {
        this.outer = outer;
        this.pom = pom;
    }

    @Override
    public void pomsChanged()
    {
        pom = Pom.from(pom.path()).get();
    }

    @Override
    public GeneratedProjectTree<?> owner()
    {
        return outer;
    }

    @Override
    public Path path()
    {
        return pom.projectFolder();
    }

    @Override
    public ArtifactIdentifiers identifiers()
    {
        return pom.toArtifactIdentifiers();
    }

    @Override
    public boolean build()
    {
        return runMaven("clean", "install");
    }

    @Override
    public boolean runMaven(String... args)
    {
        MavenCommand cmd = new MavenCommand(path(), args);
        return cmd.run().awaitQuietly();
    }

    @Override
    public GitCheckout getCheckout()
    {
        return checkout == null
               ? checkout = checkout(path()).get()
               : checkout;
    }

    @Override
    public Path sourceRoot()
    {
        return path().resolve("src/main/java");
    }

    @Override
    public Path pomFile()
    {
        return pom.path();
    }

    @Override
    public boolean commit()
    {
        return commit("Some commit");
    }

    @Override
    public boolean commit(String msg)
    {
        return getCheckout().addAll() && getCheckout().commit(msg);
    }

    @Override
    public boolean push()
    {
        return getCheckout().push();
    }

    @Override
    public boolean pull()
    {
        return getCheckout().pull();
    }

    @Override
    public boolean pushCreatingBranch()
    {
        return getCheckout().pushCreatingBranch();
    }

    @Override
    public String currentBranch()
    {
        return getCheckout().branch().get();
    }

    @Override
    public Pom pom()
    {
        return pom;
    }

    @Override
    public GroupId groupId()
    {
        return pom.groupId();
    }

    @Override
    public ArtifactId artifactId()
    {
        return pom.artifactId();
    }

    @Override
    public ThrowingOptional<String> resolvedVersion()
    {
        return pom.resolvedVersion();
    }

    @Override
    public PomVersion version()
    {
        return pom.version();
    }

    @Override
    public String toString()
    {
        return identifiers() + " in " + path();
    }

}
