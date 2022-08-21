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
package com.telenav.cactus.maven.tree;

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.scope.ProjectFamily;
import com.telenav.cactus.git.Branches;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.git.Heads;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.scope.Scope;
import java.nio.file.Files;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author Tim Boudreau
 */
public class ProjectTree
{

    final GitCheckout root;
    private final AtomicBoolean upToDate = new AtomicBoolean();
    private final ProjectTreeCache cache = new ProjectTreeCache(this);

    static
    {
        try
        {
            // Force this into the maven classloader as well
            Object o = ProjectTree.class.getClassLoader().loadClass(
                    "com.telenav.cactus.maven.tree.ProjectTree$1");
        }
        catch (ClassNotFoundException ex)
        {
            ex.printStackTrace(System.out);
        }
    }

    ProjectTree(GitCheckout root)
    {
        this.root = root;
    }

    public GitCheckout root()
    {
        return root;
    }

    public static ThrowingOptional<ProjectTree> from(MavenProject project)
    {
        return from(project.getBasedir().toPath());
    }

    public static ThrowingOptional<ProjectTree> from(Path fileOrFolder)
    {
        return ThrowingOptional.from(GitCheckout.checkout(fileOrFolder))
                .flatMapThrowing(GitCheckout::submoduleRoot)
                .map(ProjectTree::new);
    }

    public void invalidateCache()
    {
        if (upToDate.compareAndSet(true, false))
        {
            cache.clear();
        }
    }

    private synchronized <T> T withCache(Function<ProjectTreeCache, T> func)
    {
        if (upToDate.compareAndSet(false, true))
        {
            cache.populate();
        }
        return func.apply(cache);
    }

    public Optional<Pom> findProject(String groupId, String artifactId)
    {
        return withCache(c ->
        {
            return c.project(groupId, artifactId);
        });
    }

    public boolean areVersionsConsistent()
    {
        return allVersions().size() <= 1;
    }

    public Set<String> allBranches(Predicate<GitCheckout> pred)
    {

        return allCheckouts().stream()
                // filter to the checkouts we want
                .filter(pred)
                // map to the branch, which may not be present
                .map(co -> co.branch().orElse(""))
                // prune those that are not on a branch
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    public Set<String> allBranches()
    {
        Set<String> branches = new HashSet<>();
        this.allCheckouts().forEach(checkout ->
        {
            checkout.branch().ifPresent(branches::add);
        });
        return branches;
    }

    public Set<String> allVersions()
    {
        Set<String> result = new HashSet<>();
        allProjects().forEach(pom ->
        {
            result.add(pom.version().text());
        });
        return result;
    }

    public Set<String> allVersions(Predicate<Pom> test)
    {
        Set<String> result = new HashSet<>();
        allProjects().forEach(pom ->
        {
            if (test.test(pom))
            {
                result.add(pom.version().text());
            }
        });
        return result;
    }

    public Set<Pom> allProjects()
    {
        return withCache(ProjectTreeCache::allPoms);
    }

    public Set<Pom> projectsForGroupId(String groupId)
    {
        Set<Pom> result = new TreeSet<>();
        allProjects().forEach(project ->
        {
            if (groupId.equals(project.coordinates().groupId))
            {
                result.add(project);
            }
        });
        return result;
    }

    public Set<Pom> projectsForFamily(ProjectFamily fam)
    {
        Set<Pom> result = new TreeSet<>();
        allProjects().forEach(project ->
        {
            if (fam.equals(ProjectFamily.fromGroupId(project.groupId().text())))
            {
                result.add(project);
            }
        });
        return result;

    }

    public Map<String, Set<String>> branchesByGroupId()
    {
        return withCache(c ->
        {
            Map<String, Set<String>> result = new TreeMap<>();
            c.allPoms().forEach(pom ->
            {
                GitCheckout.checkout(pom.path()).ifPresent(checkout ->
                {
                    Set<String> branches = result.computeIfAbsent(
                            pom.groupId().text(), g -> new TreeSet<>());
                    checkout.branches().localBranches().forEach(br ->
                    {
                        branches.add(br.trackingName());
                    });
                    checkout.branches().remoteBranches().forEach(br ->
                    {
                        branches.add(br.trackingName());
                    });
                });
            });
            return result;
        });
    }

    public Map<String, Map<String, Set<Pom>>> projectsByBranchByGroupId(
            Predicate<Pom> filter)
    {
        return withCache(c ->
        {
            Map<String, Map<String, Set<Pom>>> result = new TreeMap<>();
            for (Pom pom : c.allPoms())
            {
                if (!filter.test(pom))
                {
                    continue;
                }
                Map<String, Set<Pom>> infosByBranch = result.computeIfAbsent(
                        pom.groupId().text(), id -> new TreeMap<>());
                GitCheckout.checkout(pom.path()).ifPresent(checkout ->
                {
                    c.branchFor(checkout).ifPresent(branch ->
                    {
                        Set<Pom> set = infosByBranch.computeIfAbsent(branch,
                                b -> new TreeSet<>());
                        set.add(pom);
                    });
                });
            }
            return result;
        });
    }

    public Map<String, Map<String, Set<Pom>>> projectsByGroupIdAndVersion()
    {
        Map<String, Map<String, Set<Pom>>> result = new TreeMap<>();
        projectsByGroupId().forEach((gid, poms) ->
        {
            Map<String, Set<Pom>> subMap = result.computeIfAbsent(gid,
                    g -> new TreeMap<>());
            for (Pom info : poms)
            {
                Set<Pom> pomSet = subMap.computeIfAbsent(info.version()
                        .text(),
                        v -> new TreeSet<>());
                pomSet.add(info);
            }
        });
        return result;
    }

    public Set<String> groupIdsIn(GitCheckout checkout)
    {
        return withCache(c ->
        {
            return c.projectsWithin(checkout).stream().map(
                    info -> info.groupId().toString())
                    .collect(Collectors.toCollection(HashSet::new));
        });
    }

    public Map<String, Set<Pom>> projectsByGroupId()
    {
        Map<String, Set<Pom>> result = new TreeMap<>();
        allProjects().forEach(pom ->
        {
            Set<Pom> set = result.computeIfAbsent(pom.groupId().text(),
                    x -> new TreeSet<>());
            set.add(pom);
        });
        return result;
    }

    public Set<Path> allProjectFolders()
    {
        return withCache(c ->
        {
            Set<Path> result = new HashSet<>();
            c.allPoms().forEach(pom -> result.add(pom.projectFolder()));
            return result;
        });
    }

    public Map<String, Set<Pom>> projectsByVersion(Predicate<Pom> filter)
    {
        return withCache(c ->
        {
            Map<String, Set<Pom>> result = new TreeMap<>();
            c.allPoms().forEach(pom ->
            {
                if (filter.test(pom))
                {
                    Set<Pom> infos = result.computeIfAbsent(pom.version()
                            .text(),
                            v -> new TreeSet<>());
                    infos.add(pom);
                }
            });
            return result;
        });
    }

    public Optional<Pom> projectOf(Path file)
    {
        withCache(c ->
        {
            Path realFile;
            if (Files.isDirectory(file) && Files.exists(file.resolve("pom.xml")))
            {
                realFile = file.resolve("pom.xml");
            }
            else
            {
                realFile = file;
            }
            if ("pom.xml".equals(realFile.getFileName()))
            {
                for (Pom pom : c.allPoms())
                {
                    if (pom.path().equals(realFile))
                    {
                        return Optional.of(pom);
                    }
                }
            }
            List<Path> paths = new ArrayList<>();
            Map<Path, Pom> candidateItems = new HashMap<>();
            c.projectFolders().forEach((dir, pomInfo) ->
            {
                if (realFile.startsWith(dir))
                {
                    candidateItems.put(dir, pomInfo);
                    paths.add(dir);
                }
            });
            if (paths.isEmpty())
            {
                return Optional.empty();
            }
            Collections.sort(paths, (a, b) ->
            {
                // reverse sort
                return Integer.compare(b.getNameCount(), a.getNameCount());
            });
            return Optional.of(candidateItems.get(paths.get(0)));
        });
        return Optional.empty();
    }

    public GitCheckout checkoutFor(Pom info)
    {
        return withCache(c -> c.checkoutForPom.get(info));
    }

    public Set<GitCheckout> allCheckouts()
    {
        return withCache(ProjectTreeCache::allCheckouts);
    }

    public Optional<String> branchFor(GitCheckout checkout)
    {
        return withCache(c -> c.branchFor(checkout));
    }

    public boolean isDirty(GitCheckout checkout)
    {
        return withCache(c -> c.isDirty(checkout));
    }

    public Set<GitCheckout> checkoutsFor(Collection<? extends Pom> infos)
    {
        return withCache(c ->
        {
            Set<GitCheckout> result = new TreeSet<>();
            infos.forEach(pom -> c.checkoutFor(pom).ifPresent(result::add));
            return result;
        });
    }

    public Branches branches(GitCheckout checkout)
    {
        return withCache(c -> c.branches(checkout));
    }

    public Optional<String> mostCommonBranchForGroupId(String groupId)
    {
        return withCache(c -> c.mostCommonBranchForGroupId(groupId));
    }

    public boolean isDetachedHead(GitCheckout checkout)
    {
        return withCache(c -> c.isDetachedHead(checkout));
    }

    public Set<Pom> projectsWithin(GitCheckout checkout)
    {
        return withCache(c -> c.projectsWithin(checkout));
    }

    public Set<GitCheckout> nonMavenCheckouts()
    {
        return withCache(ProjectTreeCache::nonMavenCheckouts);
    }

    public Set<GitCheckout> checkoutsContainingGroupId(String groupId)
    {
        return withCache(c -> c.checkoutsContainingGroupId(groupId));
    }

    public Set<GitCheckout> checkoutsInProjectFamily(Set<ProjectFamily> family)
    {
        return withCache(c -> c.checkoutsInProjectFamily(family));
    }

    public Set<GitCheckout> checkoutsInProjectFamilyOrChildProjectFamily(
            String gid, Set<ProjectFamily> family)
    {
        return withCache(c -> c.checkoutsInProjectFamilyOrChildProjectFamily(
                gid));
    }

    public Set<GitCheckout> checkoutsInProjectFamily(ProjectFamily family)
    {
        return withCache(c -> c.checkoutsInProjectFamily(family));
    }

    public Heads remoteHeads(GitCheckout checkout)
    {
        return withCache(c -> c.remoteHeads(checkout));
    }
    
    public Set<ProjectFamily> allProjectFamilies() {
        return withCache(ProjectTreeCache::allProjectFamilies);
    }
    
    public void invalidateBranches(GitCheckout co) {
        withCache(cache -> cache.invalidateBranches(co));
    }

    /**
     * Get a depth-first list of checkouts matching this scope, given the passed
     * contextual criteria.
     *
     * @param tree A project tree
     * @param callingProjectsCheckout The checkout of the a mojo is currently
     * being run against.
     * @param includeRoot If true, include the root (submodule parent) checkout
     * in the returned list regardless of whether it directly contains a maven
     * project matching the other criteria (needed for operations that change
     * the head commit of a submodule, which will generate modifications in the
     * submodule parent project.
     * @param callingProjectsGroupId The group id of the project whose mojo is
     * being invoked
     */
    public List<GitCheckout> matchCheckouts(Scope scope,
            GitCheckout callingProjectsCheckout, boolean includeRoot,
            Set<ProjectFamily> family, String callingProjectsGroupId)
    {
        Set<GitCheckout> checkouts;
        switch (scope)
        {
            case FAMILY:
                checkouts = checkoutsInProjectFamily(family);
                break;
            case FAMILY_OR_CHILD_FAMILY:
                checkouts = checkoutsInProjectFamilyOrChildProjectFamily(
                        callingProjectsGroupId,
                        family);
                break;
            case SAME_GROUP_ID:
                checkouts = checkoutsContainingGroupId(
                        callingProjectsGroupId);
                break;
            case JUST_THIS:
                checkouts = new HashSet<>(Arrays.asList(callingProjectsCheckout));
                break;
            case ALL_PROJECT_FAMILIES:
                checkouts = new HashSet<>(allCheckouts());
                break;
            case ALL:
                checkouts = new HashSet<>(allCheckouts());
                checkouts.addAll(nonMavenCheckouts());
                break;
            default:
                throw new AssertionError(this);
        }
        checkouts = new LinkedHashSet<>(checkouts);
        if (!includeRoot)
        {
            callingProjectsCheckout.submoduleRoot().ifPresent(checkouts::remove);
        }
        else
        {
            if (!checkouts.isEmpty()) // don't generate a push of _just_ the root checkout
            {
                callingProjectsCheckout.submoduleRoot()
                        .ifPresent(checkouts::add);
            }
        }
        return GitCheckout.depthFirstSort(checkouts);
    }

}
