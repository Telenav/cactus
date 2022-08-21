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

    public static final String PLUGIN_NAME = PLUGIN_FAMILY_NAME + "-maven-plugin";

    private static final String PREFIX = PLUGIN_FAMILY_NAME + '.';

    public static final String DEFAULT_DEVELOPMENT_BRANCH = "develop";

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
     * Family, to override the detected family of the target project and provide
     * multiple families the mojo applies to.
     */
    public static final String FAMILIES = PREFIX + "families";

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

    public static final String CREATE_BRANCHES = PREFIX + "create-branches";

    public static final String CREATE_LOCAL_BRANCHES = PREFIX + "create-local-branches";

    public static final String BASE_BRANCH = PREFIX + "base-branch";

    public static final String TARGET_BRANCH = PREFIX + "target-branch";

    public static final String PERMIT_LOCAL_CHANGES = PREFIX + "permit-local-changes";

    public static final String SKIP_CONFLICTS = PREFIX + "skip-conflicts";

    public static final String STABLE_BRANCH = PREFIX + "stable-branch";
    
    public static final String DEFAULT_STABLE_BRANCH = "stable";
    
    public static final String CREATE_AUTOMERGE_TAG = PREFIX + "create-automerge-tag";

    private CactusCommonPropertyNames()
    {
        throw new AssertionError();
    }
}
