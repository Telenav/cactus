package com.telenav.cactus.maven.git;

import com.telenav.cactus.maven.util.ThrowingOptional;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
final class SubmodulesRepoSet implements RepoSet
{

    private final GitCheckout top;

    public SubmodulesRepoSet(GitCheckout top)
    {
        this.top = top;
    }

    @Override
    public ThrowingOptional<GitCheckout> top()
    {
        return ThrowingOptional.of(top);
    }

    @Override
    public ThrowingOptional<GitCheckout> child(String name)
    {
        Path path = top.checkoutRoot().resolve(name);
        return ThrowingOptional.from(GitCheckout.repository(path));
    }

    @Override
    public Map<String, GitCheckout> repositories() {
        Map<String, GitCheckout> result = new HashMap<>();
        top.submodules().ifPresent(submodules ->
        {
            submodules.forEach(sub -> sub.repository().ifPresent(repo -> {
                result.put(sub.modulePath, repo);
            }));
        });
        return result;
    }
}
