package com.telenav.cactus.github;

import java.util.List;
import java.util.function.Consumer;

/**
 * Options accepted by GitCheckout.mergePullRequest().
 *
 * @author Tim Boudreau
 */
public enum MergePullRequestOptions implements Consumer<List<String>>
{
    AUTO,
    DELETE_BRANCH,
    MERGE,
    SQUASH,
    REBASE,
    ADMIN;

    @Override
    public void accept(List<String> args)
    {
        args.add(toString());
    }

    @Override
    public String toString()
    {
        return "--" + name().toLowerCase().replace('_', '-');
    }
}
