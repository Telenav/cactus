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
import com.telenav.cactus.test.project.generator.ProjectsGenerator;
import com.telenav.cactus.test.project.starwars.StarWars;
import java.nio.file.Path;

import static java.lang.System.currentTimeMillis;

/**
 * Wrapper for a non-pom, java source project.
 *
 * @author Tim Boudreau
 */
final class WrappedProjectImpl implements ProjectWrapper
{
    final ProjectsGenerator.FakeProject project;
    private GitCheckout checkout;
    private final GeneratedProjectTree<?> outer;
    private boolean pomsChanged;
    private Pom pom;

    WrappedProjectImpl(ProjectsGenerator.FakeProject project,
            final GeneratedProjectTree<?> outer)
    {
        this.outer = outer;
        this.project = project;
    }

    @Override
    public String currentBranch()
    {
        return getCheckout().branch().get();
    }

    @Override
    public ArtifactIdentifiers identifiers()
    {
        return project.toArtifactIdentifiers();
    }

    @Override
    public GeneratedProjectTree<?> owner()
    {
        return outer;
    }

    @Override
    public Path path()
    {
        return outer.pathOf(project.artifactId().text());
    }

    @Override
    public boolean build()
    {
        return runMaven("clean", "install");
    }

    @Override
    public boolean runMaven(String... args)
    {
        long then = currentTimeMillis();
        MavenCommand cmd = new MavenCommand(path(), args);
        Boolean result = cmd.run().awaitQuietly();
        if (result == null)
        {
            throw new IllegalStateException(
                    "Null result from " + cmd + " - probable timeout? "
                    + (currentTimeMillis() - then) + " ms execution time.");
        }
        return result;
    }

    @Override
    public GitCheckout getCheckout()
    {
        return checkout == null
               ? checkout = GitCheckout.checkout(path()).get()
               : checkout;
    }

    @Override
    public boolean commit()
    {
        return commit("Some commit message");
    }

    @Override
    public boolean commit(String msg)
    {
        return getCheckout().addAll() && checkout.commit(msg);
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
    public Path sourceRoot()
    {
        if ("pom".equals(project.packaging))
        {
            throw new IllegalArgumentException(
                    "This is a POM project: " + identifiers());
        }
        return path().resolve("src/main/java");
    }

    @Override
    public Path pomFile()
    {
        return path().resolve("pom.xml");
    }

    public Pom pom()
    {
        return pom == null
               ? pom = Pom.from(pomFile()).get()
               : pom;
    }

    @Override
    public GroupId groupId()
    {
        if (pomsChanged)
        {
            return pom().groupId();
        }
        return project.groupId();
    }

    @Override
    public ArtifactId artifactId()
    {
        if (pomsChanged)
        {
            return pom().artifactId();
        }
        return project.artifactId();
    }

    @Override
    public ThrowingOptional<String> resolvedVersion()
    {
        return project.resolvedVersion();
    }

    @Override
    public PomVersion version()
    {
        // the GeneratedProject will always return the version it was generated
        // with
        if (pomsChanged)
        {
            return pom().version();
        }
        return project.version();
    }

    @Override
    public String toString()
    {
        return identifiers() + " in " + path();
    }

    @Override
    public void pomsChanged()
    {
        pomsChanged = true;
        pom = null;
    }

}
