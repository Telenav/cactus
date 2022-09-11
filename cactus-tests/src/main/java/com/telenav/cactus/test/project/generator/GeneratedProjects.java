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
package com.telenav.cactus.test.project.generator;

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.maven.model.ArtifactIdentifiers;
import com.telenav.cactus.maven.model.resolver.Poms;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * A set of projects and project families with a structure much like telenav's,
 * generated into a folder on disk.
 *
 * @author timb
 */
public class GeneratedProjects
{
    public final RepositoriesGenerator.CloneSet clones;
    private final Set<ProjectsGenerator.Superpom> superpoms;
    private final Set<ProjectsGenerator.FakeProject> projects;
    private final Poms poms;

    GeneratedProjects(RepositoriesGenerator.CloneSet clones,
            Set<ProjectsGenerator.Superpom> superpoms,
            Set<ProjectsGenerator.FakeProject> projects) throws IOException
    {
        this.clones = clones;
        this.superpoms = new HashSet<>(superpoms);
        this.projects = new HashSet<>(projects);
        poms = Poms.in(clones.workspaceClone);
        sanityCheck();
    }

    public GeneratedProjects newClone() throws IOException
    {
        return new GeneratedProjects(clones.newClone(), superpoms, projects);
    }

    public Path cloneRoot()
    {
        return clones.workspaceClone;
    }

    private void sanityCheck() throws IOException
    {
        Set<ArtifactIdentifiers> absent = new HashSet<>();
        for (ProjectsGenerator.Superpom sup : superpoms)
        {
            poms.get(sup.groupId(), sup.artifactId())
                    .ifPresentOrElse(pom ->
                    {
                    },
                            () ->
                    {
                        absent.add(sup.toArtifactIdentifiers());
                    });
        }
        for (ProjectsGenerator.FakeProject sup : projects)
        {
            poms.get(sup.groupId(), sup.artifactId())
                    .ifPresentOrElse(pom ->
                    {
                    },
                            () ->
                    {
                        absent.add(sup.toArtifactIdentifiers());
                    });
        }
        if (!absent.isEmpty())
        {
            throw new IOException(
                    "The following artifacts should have been created but were found under "
                    + clones.workspaceClone + ": "
                    + absent);
        }
    }

    public RepositoriesGenerator repos()
    {
        return clones.repos();
    }

    public ThrowingOptional<ProjectsGenerator.Superpom> findSuperpom(String aid)
    {
        for (ProjectsGenerator.Superpom s : superpoms)
        {
            if (s.artifactId().is(aid))
            {
                return ThrowingOptional.of(s);
            }
        }
        return ThrowingOptional.empty();
    }

    public ThrowingOptional<ProjectsGenerator.Superpom> findSuperpom(String gid,
            String aid)
    {
        for (ProjectsGenerator.Superpom s : superpoms)
        {
            if (s.groupId().is(gid) && s.artifactId().is(aid))
            {
                return ThrowingOptional.of(s);
            }
        }
        return ThrowingOptional.empty();
    }

    public ThrowingOptional<ProjectsGenerator.FakeProject> findProject(
            String aid)
    {
        for (ProjectsGenerator.FakeProject p : projects)
        {
//            System.out.println("TEST '" + p.artifactId() + "' and " + aid + " " + p.artifactId().is(aid));
            if (p.artifactId().is(aid))
            {
                return ThrowingOptional.of(p);
            }
        }
        return ThrowingOptional.empty();
    }

    public ThrowingOptional<ProjectsGenerator.FakeProject> findProject(
            String gid,
            String aid)
    {
        for (ProjectsGenerator.FakeProject p : projects)
        {
            if (p.groupId().is(gid) && p.artifactId().is(aid))
            {
                return ThrowingOptional.of(p);
            }
        }
        return ThrowingOptional.empty();
    }

    public ThrowingOptional<Boolean> runMavenCommand(String aid, String... args)
    {
        return pathOf(aid).map(path ->
        {
            return new MavenCommand(path, args).run().awaitQuietly(
                    Duration.ofMinutes(2));
        });
    }

    public ThrowingOptional<Path> pathOf(String aid)
    {
        return findProject(aid).map(project -> clones.workspaceClone.resolve(
                project.relativePathInRoot()));
    }

}
