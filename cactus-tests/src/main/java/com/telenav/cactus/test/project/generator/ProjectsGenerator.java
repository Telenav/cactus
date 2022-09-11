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
import com.telenav.cactus.maven.model.ArtifactId;
import com.telenav.cactus.maven.model.ArtifactIdentifiers;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.MavenArtifactCoordinates;
import com.telenav.cactus.maven.model.MavenCoordinates;
import com.telenav.cactus.maven.model.MavenIdentified;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.scope.ProjectFamily;
import com.telenav.cactus.test.project.generator.RepositoriesGenerator.CloneSet;
import com.telenav.cactus.test.project.generator.RepositoriesGenerator.ProjectInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import static com.telenav.cactus.scope.ProjectFamily.familyOf;
import static com.telenav.cactus.test.project.generator.RepositoriesGenerator.ProjectInfoKind.INTERMEDIATE;
import static com.telenav.cactus.test.project.generator.RepositoriesGenerator.ProjectInfoKind.SUPERPOM;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * 
 *
 * @author Tim Boudreau
 */
public final class ProjectsGenerator
{

    private RepositoriesGenerator repos;
    private final Set<FakeProject> projects = new HashSet<>();
    private final Set<Superpom> superpoms = new HashSet<>();

    public ProjectsGenerator(String rootGroupId)
    {
        repos = new RepositoriesGenerator(rootGroupId);
    }

    public FakeProject addProject(FakeProject prj)
    {
        projects.add(prj);
        return prj;
    }

    public Superpom addSuperpom(Superpom prj)
    {
        superpoms.add(prj);
        return prj;
    }

    public abstract class AbstractProjectBuilder<R extends AbstractProjectBuilder<R>>
    {
        final Map<String, String> properties = new HashMap<>();
        MavenArtifactCoordinates parent;
        final Set<MavenIdentified> dependencies = new HashSet<>();
        final Set<MavenArtifactCoordinates> dependencyMgmt = new HashSet<>();
        final Set<String> modules = new TreeSet<>();
        String packaging = "jar";
        ProjectInfo info;

        @SuppressWarnings("unchecked")
        R cast()
        {
            return (R) this;
        }

        abstract String version();

        public R withPackaging(String pkg)
        {
            this.packaging = pkg;
            return cast();
        }

        public R withModule(String module)
        {
            this.modules.add(module);
            return cast();
        }

        public R setParent(MavenArtifactCoordinates coords)
        {
            this.parent = coords;
            return cast();
        }

        public R addDependency(MavenIdentified id)
        {
            dependencies.add(id);
            return cast();
        }

        public R addDependencyManagement(MavenArtifactCoordinates coords)
        {
            ProjectFamily fam = familyOf(coords);
            String propName = fam.name() + ".version";
            addProperty(propName, coords.version().text());
            this.dependencyMgmt.add(coords.withVersion("${" + propName + "}"));
            return cast();
        }

        public R addDependencyManagement(String gid, String aid, String ver)
        {
            return addDependencyManagement(new MavenCoordinates(gid, aid, ver));
        }

        public R addDependency(String gid, String aid)
        {
            return addDependency(new ArtifactIdentifiers(gid, aid));
        }

        public R addProperty(String k, String v)
        {
            properties.put(k, v);
            return cast();
        }

        public R addVersionProperty(MavenArtifactCoordinates c)
        {
            String vProp = c.artifactId().text() + ".version";
            String vval = c.version().text();
            return addProperty(vProp, vval);
        }
    }

    public ProjectsGenerator superpom(String version, Consumer<SuperpomBuilder> c)
    {
        SuperpomBuilder bldr = new SuperpomBuilder(version);
        c.accept(bldr);
        return this;
    }

    public class SuperpomBuilder extends AbstractProjectBuilder<SuperpomBuilder>
    {
        private final String version;
        private String subpath;
        private Set<MavenArtifactCoordinates> imports = new HashSet<>();

        SuperpomBuilder(String version)
        {
            this.version = version;
        }

        public SuperpomBuilder importing(Superpom other)
        {
            String verProp = other.artifactId().text() + ".version";
            addProperty(verProp, other.version().text());
            imports.add(other.withVersion("${" + verProp + "}"));
            return this;
        }

        @Override
        String version()
        {
            return version;
        }

        @Override
        public SuperpomBuilder setParent(MavenArtifactCoordinates coords)
        {
            if (!(coords instanceof Superpom))
            {
                throw new IllegalStateException("Not an instance of Superpom");
            }
            return super.setParent(coords);
        }

        public Superpom withArtifactId(String aid)
        {
            Path relPath;
            if (subpath != null)
            {
                relPath = Paths.get(subpath).resolve(aid);
            }
            else
            {
                relPath = Paths.get(aid);
            }
            info = repos.addProject(repos.superpomsDirName(), "", relPath,
                    packaging, SUPERPOM);
            return addSuperpom(new Superpom(version, (Superpom) parent,
                    super.properties,
                    super.dependencies, super.dependencyMgmt,
                    super.modules,
                    super.packaging, super.info, imports));
        }
    }

    Superpom intermediateParent;

    public class Superpom implements MavenArtifactCoordinates
    {
        final String version;
        final Superpom parent;
        final Map<String, String> properties;
        final Set<MavenIdentified> dependencies;
        final Set<MavenArtifactCoordinates> dependencyMgmt;
        final Set<String> modules;
        final String packaging;
        final ProjectInfo info;
        final Set<MavenArtifactCoordinates> imports;

        Superpom(String version, Superpom parent,
                Map<String, String> properties,
                Set<MavenIdentified> dependencies,
                Set<MavenArtifactCoordinates> dependencyMgmt,
                Set<String> modules, String packaging, ProjectInfo info,
                Set<MavenArtifactCoordinates> imports)
        {
            this.imports = imports;
            this.version = version;
            this.parent = parent;
            this.properties = properties;
            this.dependencies = dependencies;
            this.dependencyMgmt = dependencyMgmt;
            this.modules = modules;
            this.packaging = packaging;
            this.info = info;
            if (parent != null && imports.contains(parent))
            {
                throw new IllegalArgumentException(
                        "Cannot import own parent: " + this + " imports " + imports);
            }
        }

        public Superpom forceGroupId(String gid)
        {
            info.forceGroupId(repos.groupId() + "." + gid);
            return this;
        }

        public Superpom setAsIntermediatePomParent()
        {
            intermediateParent = this;
            return this;
        }

        private Superpom generateProject(Path projectRoot) throws IOException
        {
            Path pomPath = projectRoot.resolve("pom.xml");
            new PomGenerator(this).generate(pomPath);
            return this;
        }

        @Override
        public GroupId groupId()
        {
            return GroupId.of(info.groupId());
        }

        @Override
        public ArtifactId artifactId()
        {
            return ArtifactId.of(info.artifactId());
        }

        @Override
        public ThrowingOptional<String> resolvedVersion()
        {
            return ThrowingOptional.of(version().text());
        }

        @Override
        public PomVersion version()
        {
            return PomVersion.of(version);
        }
    }

    public FakeProject projectFor(ProjectInfo info)
    {
        for (FakeProject p : this.projects)
        {
            if (p.info == info)
            {
                return p;
            }
        }
        return null;
    }

    public Superpom superpomFor(ProjectInfo info)
    {
        for (Superpom s : this.superpoms)
        {
            if (s.info == info)
            {
                return s;
            }
        }
        return null;
    }

    public GeneratedProjects build() throws IOException
    {
        CloneSet result = repos.build((projectDir, info) ->
        {
            FakeProject p = projectFor(info);
            boolean handled = false;
            if (p != null)
            {
                p.generateProject(projectDir);
                handled = true;
            }
            else
            {
                Superpom sup = superpomFor(info);
                if (sup != null)
                {
                    sup.generateProject(projectDir);
                    handled = true;
                }
            }
            if (!handled)
            {
                if (info.kind == INTERMEDIATE)
                {
//                    System.out.println("INTER: " + info + " ikids " + info
//                            .moduleNames());
                    PomGenerator gen = new PomGenerator(info.groupId(), info
                            .artifactId(), "pom", "1.0.0");
                    if (intermediateParent != null && !info.isRoot())
                    {
                        boolean hasSuperpoms = false;
                        for (ProjectInfo ch : info.intermediateChildren())
                        {
                            if (ch.kind == SUPERPOM)
                            {
                                hasSuperpoms = true;
                            }
                        }
                        // Don't make the bom of a superpom its own ancestor
                        if (!hasSuperpoms)
                        {
                            gen.setParent(intermediateParent);
                        }
                    }
                    for (String i : info.moduleNames())
                    {
                        gen.modules.add(i);
                    }
//                    System.out.println("GEN INTER " + projectDir.resolve(
//                            "pom.xml"));
                    gen.generate(projectDir.resolve("pom.xml"));
                }
            }
        });

//        System.out.println("GEN IN " + result.toString());
        return new GeneratedProjects(result, superpoms, projects);
    }

    public ProjectsGenerator family(String name, String version,
            Consumer<FamilyBuilder> c)
    {
        FamilyBuilder bldr = new FamilyBuilder(name, version);
        c.accept(bldr);
        return this;
    }

    public class FamilyBuilder
    {
        private final String family;
        private final String version;

        FamilyBuilder(String name, String version)
        {
            this.family = name;
            this.version = version;
        }

        public SubfamilyBuilder subfamily(String name)
        {
            return new SubfamilyBuilder(name);
        }

        public FamilyBuilder subfamily(String subfamily,
                Consumer<SubfamilyBuilder> c)
        {
            SubfamilyBuilder b = new SubfamilyBuilder(subfamily);
            c.accept(b);
            return this;
        }

        public class SubfamilyBuilder
        {
            private final String subfamily;

            SubfamilyBuilder(String name)
            {
                this.subfamily = name;
            }

            public SubfamilyBuilder project(Consumer<ProjectBuilder> c)
            {
                ProjectBuilder b = new ProjectBuilder();
                c.accept(b);
                return this;
            }

            public class ProjectBuilder extends AbstractProjectBuilder<ProjectBuilder>
            {
                String subpath;

                public ProjectBuilder withSubpath(String subpath)
                {
                    this.subpath = subpath;
                    return this;
                }

                @Override
                public String version()
                {
                    return version;
                }

                public FakeProject withArtifactId(String aid)
                {
                    Path relPath;
                    if (subpath != null)
                    {
                        relPath = Paths.get(subpath).resolve(aid);
                    }
                    else
                    {
                        relPath = Paths.get(aid);
                    }
                    info = repos.addProject(family, subfamily, relPath,
                            packaging);
                    return addProject(new FakeProject(
                            super.properties,
                            super.parent,
                            super.dependencies, super.dependencyMgmt,
                            super.modules,
                            super.packaging, super.info, version()));
                }
            }
        }
    }

    public class FakeProject implements MavenArtifactCoordinates
    {
        public final Map<String, String> properties;
        public final MavenArtifactCoordinates parent;
        public final Set<MavenIdentified> dependencies;
        public final Set<MavenArtifactCoordinates> dependencyMgmt;
        public final Set<String> modules;
        public final String packaging;
        public final ProjectInfo info;
        public final String version;

        public FakeProject(
                Map<String, String> properties,
                MavenArtifactCoordinates parent,
                Set<MavenIdentified> dependencies,
                Set<MavenArtifactCoordinates> dependencyMgmt,
                Set<String> modules,
                String packaging,
                ProjectInfo info,
                String version)
        {
            this.properties = properties;
            this.parent = parent;
            this.dependencies = dependencies;
            this.dependencyMgmt = dependencyMgmt;
            this.modules = modules;
            this.packaging = packaging;
            this.info = info;
            this.version = version;
        }
        
        public Path relativePathInRoot() {
            return info.relativePathInRoot();
        }

        FakeProject generate(Path subfamilyRoot) throws IOException
        {
            Path projectRoot = subfamilyRoot.resolve(info
                    .relativePathInSubfamily());
            return generateProject(projectRoot);
        }

        private FakeProject generateProject(Path projectRoot) throws IOException
        {
            Path pomPath = projectRoot.resolve("pom.xml");
            new PomGenerator(this).generate(pomPath);
            if (!"pom".equals(packaging))
            {
                generateJavaSource(projectRoot);
            }
            return this;
        }

        private void generateJavaSource(Path projectRoot) throws IOException
        {
            String pkg = (info.groupId() + "." + info.artifactId()).replace('-',
                    '.');
            String pth = pkg.replace('.', '/');
            Path filePath = projectRoot.resolve("src/main/java").resolve(pth)
                    .resolve("Main.java");
            Files.createDirectories(filePath.getParent());
            String txt = "package " + pkg + ";\npublic class Main {\n    "
                    + "public static void main(String[] args) {\n    }\n}\n";
            Files.write(filePath, txt.getBytes(UTF_8), WRITE, TRUNCATE_EXISTING,
                    CREATE);
        }

        @Override
        public GroupId groupId()
        {
            return GroupId.of(info.groupId());
        }

        @Override
        public ArtifactId artifactId()
        {
            return ArtifactId.of(info.artifactId());
        }

        @Override
        public ThrowingOptional<String> resolvedVersion()
        {
            return ThrowingOptional.of(version().text());
        }

        @Override
        public PomVersion version()
        {
            return PomVersion.of(version);
        }
    }

}
