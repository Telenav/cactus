package com.telenav.cactus.maven.tree;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.util.ThrowingOptional;
import com.telenav.cactus.maven.xml.PomInfo;
import java.nio.file.Path;
import java.util.ArrayList;
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

    public Optional<PomInfo> findProject(String groupId, String artifactId)
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
        Set<String> branches = new HashSet<>();
        this.allCheckouts().forEach(checkout ->
        {
            branches.add(checkout.branch());
        });
        return branches;
    }

    public Set<String> allBranches()
    {
        Set<String> branches = new HashSet<>();
        this.allCheckouts().forEach(checkout ->
        {
            branches.add(checkout.branch());
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

    public Set<String> allVersions(Predicate<PomInfo> test)
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

    public Set<PomInfo> allProjects()
    {
        return withCache(Cache::allPoms);
    }

    public Set<PomInfo> projectsForGroupId(String groupId)
    {
        Set<PomInfo> result = new TreeSet<>();
        allProjects().forEach(project ->
        {
            if (groupId.equals(project.coords.groupId))
            {
                result.add(project);
            }
        });
        return result;
    }

    public Map<String, Set<String>> branchesByGroupId()
    {
        Map<String, Set<String>> result = new TreeMap<>();
        allProjects().forEach(pom ->
        {
            pom.checkout().ifPresent(checkout ->
            {
                Set<String> branches = result.computeIfAbsent(pom.coords.groupId, g -> new TreeSet<>());
                branches.add(checkout.branch());
            });
        });
        return result;
    }

    public Map<String, Map<String, Set<PomInfo>>> projectsByBranchByGroupId(Predicate<PomInfo> filter)
    {
        return withCache(c ->
        {
            Map<String, Map<String, Set<PomInfo>>> result = new TreeMap<>();
            for (PomInfo pom : c.allPoms())
            {
                if (!filter.test(pom))
                {
                    continue;
                }
                Map<String, Set<PomInfo>> infosByBranch = result.computeIfAbsent(
                        pom.coords.groupId, id -> new TreeMap<>());
                pom.checkout().ifPresent(checkout ->
                {
                    String branch = c.branchFor(checkout);
                    Set<PomInfo> set = infosByBranch.computeIfAbsent(branch,
                            b -> new TreeSet<>());
                    set.add(pom);
                });
            }
            return result;
        });
    }

    public Map<String, Map<String, Set<PomInfo>>> projectsByGroupIdAndVersion()
    {
        Map<String, Map<String, Set<PomInfo>>> result = new TreeMap<>();
        projectsByGroupId().forEach((gid, poms) ->
        {
            Map<String, Set<PomInfo>> subMap = result.computeIfAbsent(gid, g -> new TreeMap<>());
            for (PomInfo info : poms)
            {
                Set<PomInfo> pomSet = subMap.computeIfAbsent(info.coords.version, v -> new TreeSet<>());
                pomSet.add(info);
            }
        });
        return result;
    }

    public Map<String, Set<PomInfo>> projectsByGroupId()
    {
        Map<String, Set<PomInfo>> result = new TreeMap<>();
        allProjects().forEach(pom ->
        {
            Set<PomInfo> set = result.computeIfAbsent(pom.coords.groupId, x -> new TreeSet<>());
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

    public Map<String, Set<PomInfo>> projectsByVersion(Predicate<PomInfo> filter)
    {
        return withCache(c ->
        {
            Map<String, Set<PomInfo>> result = new TreeMap<>();
            c.allPoms().forEach(pom ->
            {
                if (filter.test(pom))
                {
                    Set<PomInfo> infos = result.computeIfAbsent(pom.coords.version, v -> new TreeSet<>());
                    infos.add(pom);
                }
            });
            return result;
        });
    }

    public Optional<PomInfo> projectOf(Path file)
    {
        withCache(c ->
        {
            List<Path> paths = new ArrayList<>();
            Map<Path, PomInfo> candidateItems = new HashMap<>();
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

    public GitCheckout checkoutFor(PomInfo info)
    {
        return withCache(c -> c.checkoutForPom.get(info));
    }

    public Set<GitCheckout> allCheckouts()
    {
        return withCache(Cache::allCheckouts);
    }

    public String branchFor(GitCheckout checkout)
    {
        return withCache(c -> c.branchFor(checkout));
    }
    
    public boolean isDirty(GitCheckout checkout) {
        return withCache(c -> c.isDirty(checkout));
    }

    final class Cache
    {

        private final Map<String, Map<String, PomInfo>> infoForGroupAndArtifact
                = new HashMap<>();
        private final Map<GitCheckout, Set<PomInfo>> projectsByRepository
                = new HashMap<>();
        private final Map<PomInfo, GitCheckout> checkoutForPom = new HashMap<>();
        private final Map<GitCheckout, String> branches = new HashMap<>();
        private final Map<GitCheckout, Boolean> dirty = new HashMap<>();
        
        public boolean isDirty(GitCheckout checkout) {
            return dirty.computeIfAbsent(checkout, GitCheckout::isDirty);
        }

        public Set<GitCheckout> allCheckouts()
        {
            return Collections.unmodifiableSet(projectsByRepository.keySet());
        }

        public String branchFor(GitCheckout checkout)
        {
            return branches.computeIfAbsent(checkout, GitCheckout::branch);
        }

        public Map<Path, PomInfo> projectFolders()
        {
            Map<Path, PomInfo> infos = new HashMap<>();
            allPoms().forEach(pom -> infos.put(pom.pom.getParent(), pom));
            return infos;
        }

        public Set<PomInfo> allPoms()
        {
            Set<PomInfo> set = new HashSet<>();
            projectsByRepository.forEach((repo, infos) -> set.addAll(infos));
            return set;
        }

        Optional<PomInfo> project(String groupId, String artifactId)
        {
            Map<String, PomInfo> map = infoForGroupAndArtifact.get(groupId);
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
        }

        void populate()
        {
            root.pomFiles(true).forEach(path ->
            {
                PomInfo.from(path).ifPresent(info ->
                {
                    Map<String, PomInfo> subcache
                            = infoForGroupAndArtifact.computeIfAbsent(info.coords.groupId,
                                    id -> new HashMap<>());
                    subcache.put(info.coords.artifactId, info);
                    GitCheckout.repository(info.pom).ifPresent(co ->
                    {
                        Set<PomInfo> poms = projectsByRepository.computeIfAbsent(co, c -> new HashSet<>());
                        poms.add(info);
                    });
                });
            });
        }
    }

}
