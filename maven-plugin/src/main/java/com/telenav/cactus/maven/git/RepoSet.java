package com.telenav.cactus.maven.git;

import com.telenav.cactus.maven.util.ThrowingOptional;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A set of Git repositories that our plugin knows how to find. The default
 * implementation is SubmodulesRepoSet, which simply gets all the submodules.
 * <p>
 * This is implemented as an interface, since if we do need (optionally or not)
 * environment-variable-provided locations, it could be implemented as a wrapper
 * for SubmodulesRepoSet which includes those.
 * </p>
 *
 * @author Tim Boudreau
 */
public interface RepoSet extends Iterable<GitCheckout>
{

    ThrowingOptional<GitCheckout> top();

    default ThrowingOptional<GitCheckout> child(String name)
    {
        return ThrowingOptional.ofNullable(repositories().get(name));
    }

    default Collection<? extends GitCheckout> children()
    {
        return repositories().values();
    }

    @Override
    default Iterator<GitCheckout> iterator()
    {
        return repositories().values().iterator();
    }

    default Set<String> names()
    {
        return repositories().keySet();
    }

    Map<String, GitCheckout> repositories();
}
