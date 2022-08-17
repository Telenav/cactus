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

import com.mastfrog.function.throwing.io.IOBiConsumer;
import com.mastfrog.function.throwing.io.IOConsumer;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.git.GitCommand;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static com.telenav.cactus.cli.ProcessResultConverter.strings;
import static com.telenav.cactus.git.GitCheckout.checkout;
import static com.telenav.cactus.scope.ProjectFamily.fromGroupId;
import static com.telenav.cactus.test.project.generator.RepositoriesGenerator.ProjectInfoKind.INTERMEDIATE;
import static com.telenav.cactus.test.project.generator.RepositoriesGenerator.ProjectInfoKind.REGULAR;
import static com.telenav.cactus.test.project.generator.RepositoriesGenerator.ProjectInfoKind.SUPERPOM;
import static com.telenav.cactus.util.PathUtils.deleteFolderTree;
import static com.telenav.cactus.util.PathUtils.temp;
import static java.lang.Math.abs;
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.write;
import static java.nio.file.Paths.get;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.concurrent.ThreadLocalRandom.current;

/**
 * Will generate a set of git repositories in as subdir of the system temp dir,
 * and a workspace git repository which contains all of them as git submodules,
 * with the "remote" repos set up so they can be pushed to as if they were on a
 * remote git server.
 *
 * @author Tim Boudreau
 */
public final class RepositoriesGenerator
{
    private static final String DEFAULT_BRANCH = "develop";
    private final Path contentPath;
    private final String rootGroupId;
    private final Map<String, Set<String>> subfamiliesForFamily = new HashMap<>();
    private final Set<ProjectInfo> infos = new HashSet<>();
    private int workspaces;

    public RepositoriesGenerator(String rootGroupId)
    {
        this.rootGroupId = rootGroupId;
        contentPath = temp().resolve(getClass().getSimpleName() + "-"
                + Long.toString(currentTimeMillis(), 36) + "-" + Integer
                .toString(current().nextInt(), 36));
    }

    String groupId()
    {
        return rootGroupId;
    }

    public void delete() throws IOException
    {
        deleteFolderTree(contentPath);
    }

    public RepositoriesGenerator addSubfamily(String family,
            Collection<String> subfamilies)
    {
        Set<String> result = subfamiliesForFamily.computeIfAbsent(family,
                f -> new HashSet<>());
        result.addAll(subfamilies);
        return this;
    }

    public ProjectInfo addProject(String family, String subfamily,
            Path relativePath, String packaging)
    {
        return addProject(family, subfamily, relativePath, packaging, REGULAR);
    }

    public ProjectInfo addProject(String family, String subfamily,
            Path relativePath, String packaging, ProjectInfoKind kind)
    {

        assert !relativePath.isAbsolute() : relativePath.toString();
        addSubfamily(family, singleton(subfamily));
        ProjectInfo info = new ProjectInfo(family, subfamily, relativePath,
                packaging, kind);
        if (relativePath.getNameCount() > 1)
        {
            relativePath = relativePath.getParent();
            while (relativePath != null && relativePath.getNameCount() > 1)
            {
                ProjectInfo intermediate = new ProjectInfo(family, subfamily,
                        relativePath, "pom", INTERMEDIATE);
                relativePath = relativePath.getParent();
                infos.add(intermediate);
            }
        }
        infos.add(info);
        return info;
    }

    private Path workspaceOrigin()
    {
        return contentPath.resolve("workspace");
    }

    public CloneSet build() throws IOException
    {
        return build((_ignored, _ignored2) ->
        {
        });
    }

    public CloneSet build(IOBiConsumer<Path, ProjectInfo> c) throws IOException
    {
        ensureDir(contentPath);
        Map<String, Path> repoDirs = new HashMap<>();
        Set<ProjectInfo> synthetic = new HashSet<>();
        for (ProjectInfo info : infos)
        {
            Path dir = contentPath.resolve(info.familyCheckoutName());
            if (!repoDirs.containsKey(info.familyCheckoutName()))
            {
                initOriginRepo(info.toString(), dir);
                repoDirs.put(info.familyCheckoutName(), dir);

                ProjectInfo fam = new ProjectInfo(info.family, info.subfamily,
                        get(info.family), "pom", INTERMEDIATE);
                c.accept(dir, fam);
                synthetic.add(fam);
            }
            Path projectDir = dir.resolve(info.relativePathInSubfamily());
            createDirectories(projectDir);
            write(projectDir.resolve("README.md"), info.toString()
                    .getBytes(UTF_8), CREATE, WRITE, TRUNCATE_EXISTING);

            c.accept(projectDir, info);

            Path rp = info.relativePath;
            while (rp.getNameCount() > 1)
            {
                rp = rp.getParent();
                ProjectInfo synth = new ProjectInfo(info.family, info.subfamily,
                        rp, "pom", INTERMEDIATE);
                c.accept(dir.resolve(rp), synth);
                synthetic.add(synth);
            }

            new GitCommand<>(strings(), projectDir, "add", "-A").run()
                    .awaitQuietly();
            new GitCommand<>(strings(), projectDir, "commit", "-m",
                    "README for " + info.artifactId())
                    .run().awaitQuietly();
        }
        Path submodulesRepo = workspaceOrigin();
        initSubmodulesRepo(submodulesRepo, repoDirs.values());

        ProjectInfo rootInfo = new ProjectInfo("", "", get(""), "pom", INTERMEDIATE);
        synthetic.add(rootInfo);
        c.accept(submodulesRepo, rootInfo);
        GitCheckout rootCheckout = checkout(submodulesRepo).get();
        c.accept(submodulesRepo, rootInfo);
        rootCheckout.addAll();
        rootCheckout.commit("Initial content for root repo");

        Path clone = workspaceClonePath();
        cloneSubmodulesRepo(submodulesRepo, clone);

        // Now get us out of detached head state
        new GitCommand<>(strings(), clone, "submodule", "foreach", "git",
                "pull", "origin", DEFAULT_BRANCH)
                .run().awaitQuietly();
        new GitCommand<>(strings(), clone, "submodule", "foreach", "git",
                "checkout", DEFAULT_BRANCH)
                .run().awaitQuietly();

        Set<ProjectInfo> includingSynthetic = new HashSet<>(infos);
        includingSynthetic.addAll(synthetic);
        return new CloneSet(clone, includingSynthetic, submodulesRepo);
    }

    Path inSubfamilyRepo(String family, String subfamily, IOConsumer<Path> c)
            throws IOException
    {
        String s = family + (subfamily.isEmpty()
                             ? ""
                             : "-" + subfamily);
        Path result = contentPath.resolve(s);
        c.accept(result);
        return result;
    }

    private Path workspaceClonePath()
    {
        return contentPath.resolve("workspace-clone-" + ++this.workspaces);
    }

    public final class CloneSet
    {

        public final Path workspaceClone;
        public final Set<ProjectInfo> infos;
        private final Path submodulesOriginRepo;

        public CloneSet(Path workspaceClone, Set<ProjectInfo> infos,
                Path submodulesOriginRepo)
        {
            this.workspaceClone = workspaceClone;
            this.infos = infos;
            this.submodulesOriginRepo = submodulesOriginRepo;
        }
        
        public Path workspaceClone() {
            return workspaceClone;
        }

        @SuppressWarnings("empty-statement")
        public CloneSet newClone()
        {
            Path pdir = workspaceClone.getParent();
            Path target;
            for (int sfx = 2; exists(target = pdir.resolve(
                    "workspace-" + sfx)); sfx++);
            new GitCommand<>(strings(), pdir, "clone", submodulesOriginRepo
                    .getFileName().toString(),
                    target.getFileName().toString()).run().awaitQuietly();

            new GitCommand<>(strings(), target, "submodule", "init")
                    .run().awaitQuietly();
            new GitCommand<>(strings(), target, "submodule", "update")
                    .run().awaitQuietly();
            // Now get us out of detached head state
            new GitCommand<>(strings(), target, "submodule", "foreach", "git",
                    "pull", "origin", DEFAULT_BRANCH)
                    .run().awaitQuietly();
            new GitCommand<>(strings(), target, "submodule", "foreach", "git",
                    "checkout", DEFAULT_BRANCH)
                    .run().awaitQuietly();

            return new CloneSet(target, infos, submodulesOriginRepo);
        }

        public Path root(ProjectInfo info)
        {
            return workspaceClone.resolve(info.relativePathInRoot());
        }

        RepositoriesGenerator repos()
        {
            return RepositoriesGenerator.this;
        }

    }

    public static Path initOriginRepo(String what, Path dir) throws IOException
    {
        if (!exists(dir))
        {
            createDirectories(dir);
        }
        new GitCommand<>(strings(), dir,
                "init").run().awaitQuietly();
        new GitCommand<>(strings(), dir, "checkout", "-b", DEFAULT_BRANCH)
                .run().awaitQuietly();
        write(dir.resolve("README.md"), what.getBytes(UTF_8), CREATE,
                TRUNCATE_EXISTING, WRITE);

        write(dir.resolve(".gitignore"), "target/\n*.class\n*.orig\n"
                .getBytes(UTF_8), CREATE,
                TRUNCATE_EXISTING, WRITE);

        new GitCommand<>(strings(), dir, "add", "README.md")
                .run().awaitQuietly();
        new GitCommand<>(strings(), dir, "commit", "-m", " Add README.\n")
                .run().awaitQuietly();
        // Allows clones to push to it
        new GitCommand<>(strings(), dir, "config",
                "receive.denyCurrentBranch", "ignore")
                .run().awaitQuietly();
        return dir;
    }

    static Path initSubmodulesRepo(Path dir,
            Collection<? extends Path> submodules)
            throws IOException
    {
        if (!exists(dir))
        {
            createDirectories(dir);
        }
        new GitCommand<>(strings(), dir,
                "init").run().awaitQuietly();
        new GitCommand<>(strings(), dir, "checkout", "-b", DEFAULT_BRANCH)
                .run().awaitQuietly();
        submodules.forEach(path ->
        {
            new GitCommand<>(strings(), dir, "submodule", "add",
                    path.toString())
                    .run().awaitQuietly();
        });
        new GitCommand<>(strings(), dir, "submodule", "init")
                .run().awaitQuietly();
        new GitCommand<>(strings(), dir, "submodule", "update")
                .run().awaitQuietly();
        new GitCommand<>(strings(), dir, "config",
                "receive.denyCurrentBranch", "ignore")
                .run().awaitQuietly();
        new GitCommand<>(strings(), dir, "commit", "-m", "Add submodules").run()
                .awaitQuietly();
        return dir;
    }

    public static Path cloneSubmodulesRepo(Path origin, Path into) throws IOException
    {
        if (!exists(into.getParent()))
        {
            createDirectories(into.getParent());
        }
        new GitCommand<>(strings(), into.getParent(), "clone", origin.toString(),
                into.getFileName().toString())
                .run().awaitQuietly();
        new GitCommand<>(strings(), into, "submodule", "init").run()
                .awaitQuietly();
        new GitCommand<>(strings(), into, "submodule", "update").run()
                .awaitQuietly();
        return into;
    }

    String superpomsDirName()
    {
        return fromGroupId(rootGroupId).name() + "-superpoms";
    }

    Path ensureDir(Path what) throws IOException
    {
        if (!exists(what))
        {
            createDirectories(what);
        }
        return what;
    }

    public enum ProjectInfoKind
    {
        REGULAR,
        INTERMEDIATE,
        SUPERPOM;

        String packaging()
        {
            if (this == REGULAR)
            {
                return "jar";
            }
            return "pom";
        }
    }

    public class ProjectInfo
    {
        final String family;
        final String subfamily;
        final Path relativePath;
        final String packaging;
        final ProjectInfoKind kind;

        public ProjectInfo(String family, String subfamily, Path relativePath,
                String packaging, ProjectInfoKind kind)
        {
            this.family = family;
            this.subfamily = subfamily;
            this.relativePath = relativePath;
            this.packaging = "jar".equals(packaging)
                             ? kind.packaging()
                             : packaging;
            this.kind = kind;
            String s = relativePath.toString();
            if (s.length() > 0 && s.charAt(s.length() - 1) == '-')
            {
                throw new IllegalArgumentException(
                        "Huh? " + s + " for '" + family + "' '" + subfamily + "' " + kind);
            }
        }

        public Path relativePathIfWithinSameCheckout(String groupId,
                String artifactId)
        {
            if (kind != SUPERPOM)
            {
                return null;
            }
            for (ProjectInfo ifo : infos)
            {
                if (groupId.equals(ifo.groupId()) && artifactId.equals(ifo
                        .artifactId()))
                {
                    if (ifo.kind == SUPERPOM)
                    {
                        Path mine = relativePathInRoot();
                        Path theirs = ifo.relativePathInRoot();
                        return mine.relativize(theirs);
                    }
                    break;
                }
            }
            return null;
        }

        boolean isRoot()
        {
            return family.isEmpty() && subfamily.isEmpty();
        }

        public Set<ProjectInfo> intermediateChildren()
        {
            if (kind != INTERMEDIATE)
            {
                return emptySet();
            }
            Set<ProjectInfo> result = new HashSet<>();
            Path myPath = relativePathInRoot();
            if (isRoot())
            {
                for (ProjectInfo i : infos)
                {
                    if (i.isSubfamilyRoot())
                    {
                        result.add(i);
                    }
                }
            }
            else
            {
                for (ProjectInfo i : infos)
                {
                    Path rp = i.relativePathInRoot();
                    if (rp.startsWith(myPath) && rp.getNameCount() == myPath
                            .getNameCount() + 1)
                    {
                        result.add(i);
                    }
                }
            }
            return result;
        }

        public Set<String> moduleNames()
        {
            Set<String> result = new TreeSet<>();
            if (isRoot())
            {
                for (ProjectInfo ifo : infos)
                {
                    if (ifo != this)
                    {
                        result.add(ifo.family + (ifo.subfamily.isEmpty()
                                                 ? ""
                                                 : ("-" + ifo.subfamily)));
                    }
                }
                return result;
            }
            Set<ProjectInfo> infos = intermediateChildren();
            if (infos.isEmpty())
            {
                return emptySet();
            }

            infos.forEach(pi ->
            {
                result.add(pi.relativePath.getFileName().toString());
            });
            if (isSubfamilyRoot())
            {
                for (ProjectInfo pi : RepositoriesGenerator.this.infos)
                {
                    if (!infos.contains(pi))
                    {
                        if (pi.family.equals(family) && pi.subfamily.equals(
                                subfamily))
                        {
                            if (pi.relativePath.getNameCount() > 1)
                            {
                                result.add(pi.relativePath.getName(0)
                                        .toString());
                            }
                        }
                    }
                }
            }
            return result;
        }

        private String forcedGroupId;

        public ProjectInfo forceGroupId(String gid)
        {
            forcedGroupId = gid;
            return this;
        }

        public String groupId()
        {
            if (forcedGroupId != null)
            {
                return forcedGroupId;
            }
            if (isRoot())
            {
                return rootGroupId;
            }
            return RepositoriesGenerator.this.rootGroupId + "." + family;
        }

        boolean isIntermediate()
        {
            return kind == INTERMEDIATE;
        }

        String familyCheckoutName()
        {
            return family + (subfamily.isEmpty()
                             ? ""
                             : "-" + subfamily);
        }

        public Path relativePathInSubfamily()
        {
            return relativePath;
        }

        boolean isSubfamilyRoot()
        {
            return relativePath.getNameCount() == 1 && relativePath.toString()
                    .equals(family);
        }

        public Path relativePathInRoot()
        {
            if (isRoot())
            {
                return get("");
            }
            if (isSubfamilyRoot())
            {
                return get(familyCheckoutName());
            }
            return get(familyCheckoutName()).resolve(
                    relativePathInSubfamily());
        }

        public String artifactId()
        {
            if (isRoot())
            {
                return fromGroupId(rootGroupId).name() + "-bom";
            }
            if (isSubfamilyRoot())
            {
                return family + (subfamily.isEmpty()
                                 ? ""
                                 : "-" + subfamily);
            }
            if (kind == INTERMEDIATE)
            {
                String h = gidHash();
                return family + "-" + relativePath
                        .getFileName() + "-" + h;
            }
            else
                if (kind == SUPERPOM)
                {
                    return relativePath.getFileName().toString();
                }
            return family + "-" + relativePath.getFileName();
        }

        private String gidHash()
        {
            // We need something unique but which will be consistent
            // across all runs to uniquify the group id of intermediate poms
            long hash = abs(
                    (family.hashCode() * 71) + (subfamily.hashCode() * 129) + (relativePath
                    .hashCode() * 51));
            String s = Long.toString(hash);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++)
            {
                int v = s.charAt(i) - (int) '0';
                // Why 'd'? Because the results have a better chance of being amusing
                sb.append((char) ('d' + v));
            }
            if (sb.length() > 7)
            {
                sb.setLength(7);
            }
            return sb.toString();
        }

        @Override
        public String toString()
        {
            return "ProjectInfo{" + "family=" + family + ", subfamily=" + subfamily + ", relativePath=" + relativePath + ", packaging=" + packaging + "}\n";
        }

        @Override
        public int hashCode()
        {
            int hash = 3;
            hash = 53 * hash + Objects.hashCode(this.family);
            hash = 53 * hash + Objects.hashCode(this.subfamily);
            hash = 53 * hash + Objects.hashCode(this.relativePath);
            hash = 53 * hash + Objects.hashCode(this.packaging);
            return hash;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ProjectInfo other = (ProjectInfo) obj;
            if (!Objects.equals(this.family, other.family)) {
                return false;
            }
            if (!Objects.equals(this.subfamily, other.subfamily)) {
                return false;
            }
            if (!Objects.equals(this.packaging, other.packaging)) {
                return false;
            }
            return Objects.equals(this.relativePath, other.relativePath);
        }

    }
}
