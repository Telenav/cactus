package com.telenav.cactus.maven.mojobase;

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.shared.SharedDataKey;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.maven.trigger.RunPolicy;
import java.util.Optional;
import org.apache.maven.project.MavenProject;

/**
 * Subtype of SharedDataMojo which shares a single (expensive to create)
 * ProjectTree instance all other subtypes of it within a build.
 *
 * @author Tim Boudreau
 */
public abstract class SharedProjectTreeMojo extends BaseMojo
{
    protected SharedProjectTreeMojo(RunPolicy policy)
    {
        super(policy);
    }

    protected SharedProjectTreeMojo()
    {
    }

    protected SharedProjectTreeMojo(boolean oncePerSession)
    {
        super(oncePerSession);
    }

    private SharedDataKey<TreeHolder> key()
    {
        // It is possible for this mojo to be run either against no git checkout
        // at all, or against a POM where child projects are in non-submodule
        // raw git checkouts underneath a root POM being run.  So, we generate
        // a key for the tree based on the root checkout, so we don't accidentally
        // return the tree for the first requester in the case that there is
        // actually more than one independent git checkout involved in the
        // build.  99.9% of the time, there is only one, but the failure mode
        // if there isn't would be very difficult to diagnose.
        if (session().getAllProjects().size() > 1)
        {
            MavenProject prj = project();
            Optional<GitCheckout> co = GitCheckout.checkout(prj.getBasedir());
            if (!co.isPresent())
            {
                return TreeHolder.KEY;
            }
            ThrowingOptional<GitCheckout> subRoot = co.get().submoduleRoot();
            if (!subRoot.isPresent())
            {
                return TreeHolder.KEY;
            }
            return SharedDataKey.of("tree_" + subRoot.get().checkoutRoot(),
                    TreeHolder.class);
        }
        return TreeHolder.KEY;
    }

    @Override
    synchronized ThrowingOptional<ProjectTree> projectTreeInternal(
            boolean invalidateCache)
    {
        if (tree != null)
        {
            if (tree.isPresent() && invalidateCache)
            {
                tree.get().invalidateCache();;
            }
            return tree;
        }
        TreeHolder holder = sharedData().computeIfAbsent(key(), () ->
        {
            return new TreeHolder(super.projectTreeInternal(false));
        });
        return holder.tree;
    }

    /**
     * Ensures that the key for the project tree is one only this class can
     * construct or access.
     */
    private static final class TreeHolder
    {
        private static final SharedDataKey<TreeHolder> KEY = SharedDataKey.of(
                "///tree", TreeHolder.class);
        final ThrowingOptional<ProjectTree> tree;

        public TreeHolder(ThrowingOptional<ProjectTree> tree)
        {
            this.tree = tree;
        }
    }
}
