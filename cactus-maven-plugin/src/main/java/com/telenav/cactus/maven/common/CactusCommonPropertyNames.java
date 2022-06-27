package com.telenav.cactus.maven.common;

/**
 * Property names used in &$064;Parameter annotations for cactus mojos, which
 * may be used by more than one mojo, to ensure consistency.
 *
 * @author Tim Boudreau
 */
public final class CactusCommonPropertyNames
{
    public static final String PLUGIN_FAMILY_NAME = "cactus";

    private static final String PREFIX = PLUGIN_FAMILY_NAME + '.';
    /**
     * Property for verbose mode, consumed by BaseMojo.
     */
    public static final String VERBOSE = PREFIX + "verbose";
    /**
     * Property for pretend mode, consumed by BaseMojo.
     */
    public static final String PRETEND = PREFIX + "pretend";
    /**
     * Scope, for plugins that apply to multiple projects, consumed by
     * ScopeMojo.
     */
    public static final String SCOPE = PREFIX + "scope";
    /**
     * Family, to override the detected family of the target project, consumed
     * by ScopeMojo.
     */
    public static final String FAMILY = PREFIX + "family";
    /**
     * Property for inclusion of the root checkout in the set of things to be
     * modified regardless of what its family is detected as, consumed by
     * ScopeMojo.
     */
    public static final String INCLUDE_ROOT = PREFIX + "include-root";

    /**
     * Boolean property for whether or not to perform a git push where that is
     * optional behavior.
     */
    public static final String PUSH = PREFIX + "push";
    /**
     * Boolean property for whether or not to perform a git commit where that is
     * optional behavior.
     */
    public static final String COMMIT_CHANGES = PREFIX + "commit-changes";
    /**
     * Commit message for mojos that generate a new commit.
     */
    public static final String COMMIT_MESSAGE = PREFIX + "commit-message";

    private CactusCommonPropertyNames()
    {
        throw new AssertionError();
    }
}
