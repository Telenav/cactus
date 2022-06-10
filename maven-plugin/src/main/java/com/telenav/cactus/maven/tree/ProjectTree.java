package com.telenav.cactus.maven.tree;

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.maven.ProjectFamily;
import com.telenav.cactus.maven.git.Branches;
import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.git.Heads;
import com.telenav.cactus.maven.model.Pom;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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

    private final GitCheckout root;
    private volatile boolean upToDate;
    private final Cache cache = new Cache();

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
        return ThrowingOptional.from(GitCheckout.repository(fileOrFolder))
                .flatMapThrowing(repo -> repo.submoduleRoot())
                .map(ProjectTree::new);
    }

    public void invalidateCache()
    {
        if (upToDate)
        {
            synchronized (this)
            {
                upToDate = false;
                cache.clear();
            }
        }
    }

    private synchronized <T> T withCache(Function<Cache, T> func)
    {
        if (!upToDate)
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
            result.add(pom.coords.version);
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
                result.add(pom.coords.version);
            }
        });
        return result;
    }

    public Set<Pom> allProjects()
    {
        return withCache(Cache::allPoms);
    }

    public Set<Pom> projectsForGroupId(String groupId)
    {
        Set<Pom> result = new TreeSet<>();
        allProjects().forEach(project ->
        {
            if (groupId.equals(project.coords.groupId))
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
            if (fam.equals(ProjectFamily.fromGroupId(project.coords.groupId)))
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
                GitCheckout.repository(pom.pom).ifPresent(checkout ->
                {
                    Set<String> branches = result.computeIfAbsent(pom.coords.groupId, g -> new TreeSet<>());

                });
            });
            return result;
        });
    }

    public Map<String, Map<String, Set<Pom>>> projectsByBranchByGroupId(Predicate<Pom> filter)
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
                        pom.coords.groupId, id -> new TreeMap<>());
                GitCheckout.repository(pom.pom).ifPresent(checkout ->
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
            Map<String, Set<Pom>> subMap = result.computeIfAbsent(gid, g -> new TreeMap<>());
            for (Pom info : poms)
            {
                Set<Pom> pomSet = subMap.computeIfAbsent(info.coords.version, v -> new TreeSet<>());
                pomSet.add(info);
            }
        });
        return result;
    }

    public Set<String> groupIdsIn(GitCheckout checkout)
    {
        return withCache(c ->
        {
            return c.projectsWithin(checkout).stream().map(info -> info.coords.groupId)
                    .collect(Collectors.toCollection(HashSet::new));
        });
    }

    public Map<String, Set<Pom>> projectsByGroupId()
    {
        Map<String, Set<Pom>> result = new TreeMap<>();
        allProjects().forEach(pom ->
        {
            Set<Pom> set = result.computeIfAbsent(pom.coords.groupId, x -> new TreeSet<>());
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
                    Set<Pom> infos = result.computeIfAbsent(pom.coords.version, v -> new TreeSet<>());
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
            List<Path> paths = new ArrayList<>();
            Map<Path, Pom> candidateItems = new HashMap<>();
            c.projectFolders().forEach((dir, pomInfo) ->
            {
                if (file.startsWith(dir))
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
        return null;
    }

    public GitCheckout checkoutFor(Pom info)
    {
        return withCache(c -> c.checkoutForPom.get(info));
    }

    public Set<GitCheckout> allCheckouts()
    {
        return withCache(Cache::allCheckouts);
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
        return withCache(c -> c.nonMavenCheckouts());
    }

    public Set<GitCheckout> checkoutsContainingGroupId(String groupId)
    {
        return withCache(c -> c.checkoutsContainingGroupId(groupId));
    }

    public Set<GitCheckout> checkoutsInProjectFamily(ProjectFamily family)
    {
        return withCache(c -> c.checkoutsInProjectFamily(family));
    }

    public Set<GitCheckout> checkoutsInProjectFamilyOrChildProjectFamily(ProjectFamily family)
    {
        return withCache(c -> c.checkoutsInProjectFamilyOrChildProjectFamily(family));
    }

    public Heads remoteHeads(GitCheckout checkout)
    {
        return withCache(c -> c.remoteHeads(checkout));
    }

    final class Cache
    {

        private final Map<String, Map<String, Pom>> infoForGroupAndArtifact
                = new HashMap<>();
        private final Map<GitCheckout, Set<Pom>> projectsByRepository
                = new HashMap<>();
        private final Map<Pom, GitCheckout> checkoutForPom = new HashMap<>();
        private final Map<GitCheckout, Optional<String>> branches = new HashMap<>();
        private final Map<GitCheckout, Boolean> dirty = new HashMap<>();
        private final Map<GitCheckout, Branches> allBranches = new HashMap<>();
        private final Map<String, Optional<String>> branchByGroupId = new HashMap<>();
        private final Map<GitCheckout, Boolean> detachedHeads = new HashMap<>();
        private final Set<GitCheckout> nonMavenCheckouts = new HashSet<>();
        private final Map<GitCheckout, Heads> remoteHeads = new HashMap<>();

        public Heads remoteHeads(GitCheckout checkout)
        {
            return remoteHeads.computeIfAbsent(checkout, ck -> ck.remoteHeads());
        }

        public Set<GitCheckout> checkoutsContainingGroupId(String groupId)
        {
            Set<GitCheckout> all = new HashSet<>();
            projectsByRepository.forEach((repo, projectSet) ->
            {
                for (Pom project : projectSet)
                {
                    if (groupId.equals(project.coords.groupId))
                    {
                        all.add(repo);
                        break;
                    }
                }
            });
            return all;
        }

        public Set<GitCheckout> checkoutsInProjectFamily(ProjectFamily family)
        {
            Set<GitCheckout> all = new HashSet<>();
            projectsByRepository.forEach((repo, projectSet) ->
            {
                for (Pom project : projectSet)
                {
                    if (family.equals(ProjectFamily.fromGroupId(project.coords.groupId)))
                    {
                        all.add(repo);
                        break;
                    }
                }
            });
            return all;
        }

        public Set<GitCheckout> checkoutsInProjectFamilyOrChildProjectFamily(ProjectFamily family)
        {
            Set<GitCheckout> all = new HashSet<>();
            projectsByRepository.forEach((repo, projectSet) ->
            {
                for (Pom project : projectSet)
                {
                    ProjectFamily pomFamily = ProjectFamily.fromGroupId(project.coords.groupId);
                    if (family.equals(pomFamily) || family.isParentFamilyOf(project.coords.groupId))
                    {
                        all.add(repo);
                        break;
                    }
                }
            });
            return all;
        }

        public Set<GitCheckout> nonMavenCheckouts()
        {
            return Collections.unmodifiableSet(nonMavenCheckouts);
        }

        public boolean isDetachedHead(GitCheckout checkout)
        {
            return detachedHeads.computeIfAbsent(checkout, GitCheckout::isDetachedHead);
        }

        public Optional<String> mostCommonBranchForGroupId(String groupId)
        {
            // Cache these since they are expensive to compute
            return branchByGroupId.computeIfAbsent(groupId, this::_mostCommonBranchForGroupId);
        }

        private Optional<String> _mostCommonBranchForGroupId(String groupId)
        {
            // Collect the number of times a branch name is used in a checkout
            // we have
            Map<String, Integer> branchNameCounts = new HashMap<>();
            Set<GitCheckout> seen = new HashSet<>();
            // Count each checkout exactly once, if it is on a branch
            checkoutForPom.forEach((pom, checkout) ->
            {
                // Filter out any irrelevant or already examined checkouts
                if (seen.contains(checkout) || !groupId.equals(pom.coords.groupId))
                {
                    return;
                }
                // If we are on a branch, collect its name and add to the number
                // of times it has been seen
                branchFor(checkout).ifPresent(branch ->
                {
                    seen.add(checkout);
                    branchNameCounts.compute(branch, (b, old) ->
                    {
                        if (old == null)
                        {
                            return 1;
                        }
                        return old + 1;
                    });
                });
            });
            // If we found nothing, we're done
            if (branchNameCounts.isEmpty())
            {
                return Optional.empty();
            }
            // Reverse sort the map entries by the count
            List<Map.Entry<String, Integer>> entries = new ArrayList<>(branchNameCounts.entrySet());
            Collections.sort(entries, (a, b) ->
            {
                return b.getValue().compareTo(a.getValue());
            });
            // And take the greatest
            return Optional.of(entries.get(0).getKey());
        }

        public Set<Pom> projectsWithin(GitCheckout checkout)
        {
            Set<Pom> infos = projectsByRepository.get(checkout);
            return infos == null ? Collections.emptySet() : infos;
        }

        public Branches branches(GitCheckout checkout)
        {
            return allBranches.computeIfAbsent(checkout, co -> co.branches());
        }

        public Optional<GitCheckout> checkoutFor(Pom info)
        {
            return Optional.ofNullable(checkoutForPom.get(info));
        }

        public boolean isDirty(GitCheckout checkout)
        {
            return dirty.computeIfAbsent(checkout, GitCheckout::isDirty);
        }

        public Set<GitCheckout> allCheckouts()
        {
            return Collections.unmodifiableSet(projectsByRepository.keySet());
        }

        public Optional<String> branchFor(GitCheckout checkout)
        {
            return branches.computeIfAbsent(checkout, GitCheckout::branch);
        }

        public Map<Path, Pom> projectFolders()
        {
            Map<Path, Pom> infos = new HashMap<>();
            allPoms().forEach(pom -> infos.put(pom.pom.getParent(), pom));
            return infos;
        }

        public Set<Pom> allPoms()
        {
            Set<Pom> set = new HashSet<>();
            projectsByRepository.forEach((repo, infos) -> set.addAll(infos));
            return set;
        }

        Optional<Pom> project(String groupId, String artifactId)
        {
            Map<String, Pom> map = infoForGroupAndArtifact.get(groupId);
            if (map == null)
            {
                return Optional.empty();
            }
            return Optional.ofNullable(map.get(artifactId));
        }

        void clear()
        {
            infoForGroupAndArtifact.clear();
            projectsByRepository.clear();
            checkoutForPom.clear();
            branches.clear();
            dirty.clear();
            allBranches.clear();
            branchByGroupId.clear();
            nonMavenCheckouts.clear();
            detachedHeads.clear();
        }

        void populate()
        {
            root.submodules().ifPresent(statii ->
            {
                statii.forEach(status ->
                {
                    status.repository().ifPresent(repo ->
                    {
                        if (!repo.hasPomInRoot())
                        {
                            nonMavenCheckouts.add(repo);
                        }
                    });
                });
            });

            root.pomFiles(true).forEach(path ->
            {
                Pom.from(path).ifPresent(info ->
                {
                    Map<String, Pom> subcache
                            = infoForGroupAndArtifact.computeIfAbsent(info.coords.groupId,
                                    id -> new HashMap<>());
                    subcache.put(info.coords.artifactId, info);
                    GitCheckout.repository(info.pom).ifPresent(co ->
                    {
                        Set<Pom> poms = projectsByRepository.computeIfAbsent(co, c -> new HashSet<>());
//                        System.out.println("CACHE: " + info + " is in " + co.checkoutRoot().getFileName());
                        poms.add(info);
                    });
                });
            });
        }
    }
}
