package com.telenav.cactus.maven.tree;

import com.mastfrog.util.preconditions.Exceptions;
import com.telenav.cactus.git.Branches;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.git.Heads;
import com.telenav.cactus.git.SubmoduleStatus;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.scope.ProjectFamily;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.telenav.cactus.scope.ProjectFamily.familyOf;

/**
 * Internal cache for the ProjectTree, so we don't re-execute expensive git
 * operations if nothing has changed.
 *
 * @author Tim Boudreau
 */
final class ProjectTreeCache
{
    final Map<String, Map<String, Pom>> infoForGroupAndArtifact = new ConcurrentHashMap<>();
    final Map<GitCheckout, Set<Pom>> projectsByRepository = new ConcurrentHashMap<>();
    final Map<Pom, GitCheckout> checkoutForPom = new ConcurrentHashMap<>();
    final Map<GitCheckout, Optional<String>> branches = new HashMap<>();
    final Map<GitCheckout, Boolean> dirty = new ConcurrentHashMap<>();
    final Map<GitCheckout, Branches> allBranches = new ConcurrentHashMap<>();
    final Map<String, Optional<String>> branchByGroupId = new HashMap<>();
    final Map<GitCheckout, Boolean> detachedHeads = new ConcurrentHashMap<>();
    final Set<GitCheckout> nonMavenCheckouts = new HashSet<>();
    final Map<GitCheckout, Heads> remoteHeads = new HashMap<>();
    final Map<ProjectFamily, Set<GitCheckout>> checkoutsForProjectFamily = new ConcurrentHashMap<>();
    private final ProjectTree outer;

    ProjectTreeCache(final ProjectTree outer)
    {
        this.outer = outer;
    }

    public Heads remoteHeads(GitCheckout checkout)
    {
        return remoteHeads.computeIfAbsent(checkout,
                GitCheckout::remoteHeads);
    }

    public Set<GitCheckout> checkoutsContainingGroupId(String groupId)
    {
        Set<GitCheckout> all = new HashSet<>();
        projectsByRepository.forEach((repo, projectSet) ->
        {
            for (Pom project : projectSet)
            {
                if (groupId.equals(project.coordinates().groupId))
                {
                    all.add(repo);
                    break;
                }
            }
        });
        return all;
    }

    public Set<GitCheckout> checkoutsInProjectFamily(Set<ProjectFamily> family)
    {
        switch (family.size())
        {
            case 0:
                return new HashSet<>(allCheckouts());
            case 1:
                return checkoutsInProjectFamily(family.iterator().next());
            default:
                Set<GitCheckout> result = new HashSet<>();
                for (ProjectFamily f : family)
                {
                    result.addAll(checkoutsInProjectFamily(f));
                }
                return result;
        }
    }

    public Set<GitCheckout> checkoutsInProjectFamily(ProjectFamily family)
    {
        return checkoutsForProjectFamily.computeIfAbsent(family,
                key ->
        {
            Set<GitCheckout> all = new HashSet<>();
            projectsByRepository.forEach((repo, projectSet) ->
            {
                for (Pom project : projectSet)
                {
                    if (family.equals(ProjectFamily.fromGroupId(project
                            .groupId()
                            .text())))
                    {
                        all.add(repo);
                        break;
                    }
                }
            });
            return all;
        });
    }

    private static boolean containsParentFamilyOf(GroupId groupId,
            Set<ProjectFamily> s)
    {
        for (ProjectFamily p : s)
        {
            if (p.isParentFamilyOf(groupId))
            {
                return true;
            }
        }
        return false;
    }

    public Set<GitCheckout> checkoutsInProjectFamilyOrChildProjectFamily(
            ProjectFamily family)
    {
        Set<GitCheckout> all = new HashSet<>();
        projectsByRepository.forEach((repo, projectSet) ->
        {
            for (Pom p : projectSet)
            {
                if (familyOf(p).equals(family)
                        || family.isParentFamilyOf(p.groupId()))
                {
                    all.add(repo);
                    break;
                }
            }
        });
        return all;
    }

    public Set<GitCheckout> checkoutsInProjectFamilyOrChildProjectFamily(
            Set<ProjectFamily> family)
    {
        Set<GitCheckout> all = new HashSet<>();
        projectsByRepository.forEach((repo, projectSet) ->
        {
            if (family.isEmpty())
            {
                all.add(repo);
                return;
            }
            for (Pom project : projectSet)
            {
                ProjectFamily pomFamily = ProjectFamily.familyOf(project
                        .groupId());
                if (family.contains(pomFamily) || containsParentFamilyOf(project
                        .groupId(), family))
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
        return detachedHeads.computeIfAbsent(checkout,
                GitCheckout::isDetachedHead);
    }

    public Optional<String> mostCommonBranchForGroupId(String groupId)
    {
        // Cache these since they are expensive to compute
        return branchByGroupId.computeIfAbsent(groupId,
                this::_mostCommonBranchForGroupId);
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
            if (seen.contains(checkout) || !pom.groupId().is(groupId))
            {
                return;
            }
            // If we are on a branch, collect its name and add to the number
            // of times it has been seen
            branchFor(checkout)
                    .ifPresent(branch ->
                    {
                        seen.add(checkout);
                        branchNameCounts.compute(branch,
                                (b, old) ->
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
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(
                branchNameCounts.entrySet());
        Collections.sort(entries,
                (a, b) ->
        {
            return b.getValue().compareTo(a.getValue());
        });
        // And take the greatest
        return Optional.of(entries.get(0).getKey());
    }

    public Set<Pom> projectsWithin(GitCheckout checkout)
    {
        Set<Pom> infos = projectsByRepository.get(checkout);
        return infos == null
               ? Collections.emptySet()
               : infos;
    }

    public Branches branches(GitCheckout checkout)
    {
        return allBranches.computeIfAbsent(checkout,
                GitCheckout::branches);
    }

    public Optional<GitCheckout> checkoutFor(Pom info)
    {
        return Optional.ofNullable(checkoutForPom.get(info));
    }

    public boolean isDirty(GitCheckout checkout)
    {
        return dirty.computeIfAbsent(checkout,
                GitCheckout::isDirty);
    }

    public Set<GitCheckout> allCheckouts()
    {
        return Collections.unmodifiableSet(projectsByRepository.keySet());
    }

    public Optional<String> branchFor(GitCheckout checkout)
    {
        return branches.computeIfAbsent(checkout,
                GitCheckout::branch);
    }

    public Map<Path, Pom> projectFolders()
    {
        Map<Path, Pom> infos = new HashMap<>();
        allPoms()
                .forEach(pom -> infos.put(pom.path().getParent(), pom));
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
        checkoutsForProjectFamily.clear();
        remoteHeads.clear();
    }

    synchronized void populate()
    {
        try
        {
            outer.root.allPomFilesInSubtreeParallel(this::cacheOnePomFile);
            outer.root.submodules().ifPresent(statii ->
            {
                for (SubmoduleStatus stat : statii)
                {
                    stat.repository().ifPresent(nonMavenCheckouts::add);
                }
            });
        }
        catch (IOException ex)
        {
            Exceptions.chuck(ex);
        }
    }
    private final Map<GitCheckout, GitCheckout> repoInternTable = new ConcurrentHashMap<>();

    private GitCheckout intern(GitCheckout co)
    {
        GitCheckout result = repoInternTable.putIfAbsent(co, co);
        if (result == null)
        {
            result = co;
        }
        return result;
    }

    private void cacheOnePomFile(Path path)
    {
        //            System.out.println(
        //                    "C1 " + Thread.currentThread().getName() + "\t" + path
        //                    .getParent().getFileName());
        Pom.from(path)
                .ifPresent(info ->
                {
                    Map<String, Pom> subcache = infoForGroupAndArtifact
                            .computeIfAbsent(info.groupId()
                                    .text(),
                                    id -> new ConcurrentHashMap<>());
                    subcache.put(info.coordinates().artifactId.text(), info);
                    GitCheckout.repository(info.path())
                            .ifPresent(co ->
                            {
                                co = intern(co);
                                Set<Pom> poms = projectsByRepository
                                        .computeIfAbsent(co,
                                                c -> new HashSet<>());
                                poms.add(info);
                                checkoutForPom.put(info, co);
                            });
                });
    }

}
