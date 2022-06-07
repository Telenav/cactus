package com.telenav.cactus.maven.tree;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.util.ThrowingOptional;
import org.apache.maven.project.MavenProject;

import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks a number of dimensions of consistency in the project, and can detect if there are only a few outliers (in
 * which case - if, say, all projects but one are on the same branch and that branch exists)
 *
 * @author Tim Boudreau
 */
public class ConsistencyChecker
{
    /**
     * Partition key for on-a-branch state of a repository. Git submodules, on checkout, put you in detached head state,
     * which may not be where you want to stay.
     */
    public static final String ON_BRANCH = "on-branch";

    /**
     * Partition key for detached-head state of a repository. Git submodules, on checkout, put you in detached head
     * state, which may not be where you want to stay.
     */
    public static final String DETACHED = "detached";

    /**
     * Partition key for clean state of a repository - no local modifications.
     */
    public static final String CLEAN = "clean";

    /**
     * Partition key for dirty state of a repository - has local modifications.
     */
    public static final String DIRTY = "dirty";

    private final Set<String> ignoreInBranchConsistencyCheck;

    private final Set<String> ignoreInVersionConsistencyCheck;

    private final String targetGroupId;

    private final boolean forbidDirty;

    public ConsistencyChecker(
            String ignoreInBranchConsistencyCheckCommaOrSpaceDelimitedList,
            String ignoreInVersionConsistencyCheckCommaOrSpaceDelimitedList,
            String targetGroupId,
            boolean forbidDirty)
    {
        this.ignoreInBranchConsistencyCheck = splitToSet(ignoreInBranchConsistencyCheckCommaOrSpaceDelimitedList);
        this.ignoreInVersionConsistencyCheck = splitToSet(ignoreInVersionConsistencyCheckCommaOrSpaceDelimitedList);
        this.targetGroupId = targetGroupId;
        this.forbidDirty = forbidDirty;
    }

    public ConsistencyChecker()
    {
        this(null, null, null, false);
    }

    public Set<Inconsistency<?>> checkBranchConsistency(ProjectTree tree, MavenProject project, BuildLog log)
    {
        Set<Inconsistency<?>> branchInconsistencies = new HashSet<>();
        checkBranchConsistency(tree, project, log, branchInconsistencies);
        return branchInconsistencies;
    }

    public Set<Inconsistency<?>> checkConsistency(MavenProject project, BuildLog log) throws Exception
    {
        log = log.child("consistency");
        ThrowingOptional<ProjectTree> treeOpt = ProjectTree.from(project.getBasedir().toPath());
        if (!treeOpt.isPresent())
        {
            log.child("checkConsistency").error("Could not find a project tree for " + project.getBasedir());
            return Collections.emptySet();
        }
        Set<Inconsistency<?>> result = new LinkedHashSet<>();
        checkBranchConsistency(treeOpt.get(), project, log.child("branch"), result);
        checkVersionConsistency(treeOpt.get(), project, log.child("versions"), result);
        checkDirtyAndDetached(treeOpt.get(), project, log.child(DIRTY), result, forbidDirty);

        return result;
    }

    public Set<Inconsistency<?>> checkDetached(ProjectTree tree, MavenProject project,
                                               BuildLog log)
    {
        Set<Inconsistency<?>> result = new HashSet<>();
        checkDirtyAndDetached(tree, project, log, result, false);
        return result;
    }

    public ConsistencyChecker forbiddingDirty()
    {
        return new ConsistencyChecker(toString(ignoreInBranchConsistencyCheck), toString(ignoreInVersionConsistencyCheck), targetGroupId, true);
    }

    public ConsistencyChecker onlyCheckingGroupId(String targetGroupId)
    {
        if (this.targetGroupId != null)
        {
            throw new IllegalStateException("Target group id already set to " + this.targetGroupId + " - cannot set to " + targetGroupId);
        }
        return new ConsistencyChecker(toString(ignoreInBranchConsistencyCheck), toString(ignoreInVersionConsistencyCheck), targetGroupId, forbidDirty);
    }

    public ConsistencyChecker withIgnoreBranchConsistencySuffixes(String commaOrSpaceDelimitedList)
    {
        if (!ignoreInBranchConsistencyCheck.isEmpty())
        {
            throw new IllegalStateException("Ignore in branches check is already set to " + ignoreInVersionConsistencyCheck + " - cannot set to " + commaOrSpaceDelimitedList);
        }
        return new ConsistencyChecker(commaOrSpaceDelimitedList, toString(ignoreInVersionConsistencyCheck), targetGroupId, forbidDirty);
    }

    public ConsistencyChecker withIgnoreVersionConsistencySuffixes(String commaOrSpaceDelimitedList)
    {
        if (!ignoreInVersionConsistencyCheck.isEmpty())
        {
            throw new IllegalStateException("Ignore in versions check is already set to " + ignoreInVersionConsistencyCheck + " - cannot set to " + commaOrSpaceDelimitedList);
        }
        return new ConsistencyChecker(toString(ignoreInBranchConsistencyCheck), commaOrSpaceDelimitedList, targetGroupId, forbidDirty);
    }

    private static Set<String> splitToSet(String what)
    {
        if (what == null || what.isBlank())
        {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        for (String item : what.split("[,\\s]+"))
        {
            if (item.isEmpty())
            {
                continue;
            }
            result.add(item.trim());
        }
        return result;
    }

    private static String toString(Set<String> what)
    {
        if (what.isEmpty())
        {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String s : what)
        {
            if (sb.length() > 0)
            {
                sb.append(',');
            }
            sb.append(s);
        }
        return sb.toString();
    }

    private void checkBranchConsistency(ProjectTree tree, MavenProject project,
                                        BuildLog log, Set<Inconsistency<?>> into)
    {
        log.info("Checking consistency of branches" + (targetGroupId == null ? "" : " for projects with the group id " + targetGroupId));
        Map<String, Map<String, Set<Pom>>> found
                = tree.projectsByBranchByGroupId(
                this::isVersionRequiredToBeConsistent);

        found.forEach((groupId, pomInfosForBranch) ->
        {
            if (pomInfosForBranch.size() > 1)
            {
                Inconsistency<Pom> issue = new Inconsistency<>(
                        pomInfosForBranch, Inconsistency.Kind.BRANCH,
                        Pom::projectFolder);
                into.add(issue);
            }
        });
    }

    private void checkDirtyAndDetached(ProjectTree tree, MavenProject project,
                                       BuildLog log, Set<? super Inconsistency<?>> into, boolean forbidDirty)
    {
        log.info("Checking for detached-head checkouts" + (targetGroupId == null ? "" : " for projects with the group id " + targetGroupId));
        log.info("Checking for dirty checkouts" + (targetGroupId == null ? "" : " for projects with the group id " + targetGroupId));
        Map<String, Set<GitCheckout>> dirtyNotDirty = new HashMap<>();
        Map<String, Set<GitCheckout>> detachedNotDetached = new HashMap<>();
        for (GitCheckout checkout : tree.allCheckouts())
        {
            if (isRelevantCheckout(tree, checkout))
            {
                if (forbidDirty)
                {
                    String cleanDirtyKey = tree.isDirty(checkout) ? DIRTY : CLEAN;
                    Set<GitCheckout> checkouts = dirtyNotDirty.computeIfAbsent(
                            cleanDirtyKey, k -> new TreeSet<>());
                    checkouts.add(checkout);
                }

                String detachedKey = checkout.isDetachedHead() ? DETACHED : ON_BRANCH;
                Set<GitCheckout> detachedCheckouts = detachedNotDetached.computeIfAbsent(
                        detachedKey, k -> new TreeSet<>());
                detachedCheckouts.add(checkout);
            }
        }
        if (dirtyNotDirty.containsKey(DIRTY))
        {
            into.add(new Inconsistency<GitCheckout>(dirtyNotDirty,
                    Inconsistency.Kind.CONTAINS_MODIFIED_SOURCES, GitCheckout::checkoutRoot));
        }
        if (detachedNotDetached.containsKey("detached"))
        {
            into.add(new Inconsistency<GitCheckout>(detachedNotDetached,
                    Inconsistency.Kind.NOT_ON_A_BRANCH, GitCheckout::checkoutRoot));
        }
    }

    private void checkVersionConsistency(ProjectTree tree, MavenProject project,
                                         BuildLog log, Set<? super Inconsistency<?>> into)
    {
        log.info("Checking consistency of versions" + (targetGroupId == null ? "" : " for projects with the group id " + targetGroupId));
        Map<String, Set<Pom>> projectsByVersion = tree.projectsByVersion(this::isVersionRequiredToBeConsistent);
        if (projectsByVersion.size() > 1)
        {
            into.add(new Inconsistency<>(projectsByVersion, Inconsistency.Kind.VERSION, Pom::projectFolder));
        }
    }

    private boolean isRelevantCheckout(ProjectTree tree, GitCheckout checkout)
    {
        boolean result = isRelevantCheckout(checkout);
        if (result)
        {
            if (targetGroupId != null)
            {
                boolean found = false;
                for (Pom pom : tree.allProjects())
                {
                    GitCheckout co = GitCheckout.repository(pom.pom).get();
                    if (co.equals(checkout))
                    {
                        if (targetGroupId.equals(pom.coords.groupId))
                        {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found)
                {
                    result = false;
                }
            }
        }
        return result;
    }

    private boolean isRelevantCheckout(GitCheckout checkout)
    {
        if (!Files.exists(checkout.checkoutRoot().resolve("pom.xml")))
        {
            return false;
        }
        if (targetGroupId != null)
        {
            Pom info = Pom.from(checkout.checkoutRoot().resolve("pom.xml")).get();
            if (!targetGroupId.equals(info.coords.groupId))
            {
                return false;
            }
        }
        Set<String> names = new HashSet<>();
        names.add(checkout.checkoutRoot().getFileName().toString());
        names.addAll(checkout.remoteProjectNames());
        for (String name : names)
        {
            for (String suffix : ignoreInBranchConsistencyCheck)
            {
                if (name.endsWith(suffix) || name.equals(suffix))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isVersionRequiredToBeConsistent(Pom info)
    {
        if (targetGroupId != null && !targetGroupId.equals(info.coords.groupId))
        {
            return false;
        }
        return ignoreInVersionConsistencyCheck.contains(info.coords.artifactId);
    }
}
