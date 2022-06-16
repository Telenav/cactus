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
package com.telenav.cactus.maven;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.tree.ProjectTree;
import org.apache.maven.plugin.MojoExecutionException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.telenav.cactus.maven.util.EnumMatcher.enumMatcher;

/**
 * A number of cross repository git operations can operate on one of several
 * scopes.
 *
 * @author Tim Boudreau
 */
public enum Scope
{
    /**
     * Operate only on the git submodule the project maven was invoked against
     * belongs to.
     */
    JUST_THIS,
    /**
     * Operate on all git submodules within the tree of the project maven was
     * invoked against that contain a maven project with the same project
     * family.
     *
     * @see ProjectFamily
     */
    FAMILY,
    /**
     * Operate on all git submodules within the tree of the project maven was
     * invoked against that contain a maven project with the same project
     * family, or where the project family is the parent family of that project
     * (e.g. the groupId is com.foo.bar, the family is "bar" and the parent
     * family is "foo").
     *
     * @see ProjectFamily
     */
    FAMILY_OR_CHILD_FAMILY,
    /**
     * Operate on all git submodules within the tree of the project maven was
     * invoked against that contains the same group id as the project maven was
     * invoked against.
     */
    SAME_GROUP_ID,
    /**
     * Operate on all git submodules containing a root pom.xml within any
     * submodule below the root of the project tree the project maven was
     * invoked against lives in.
     */
    ALL;

    public static Scope find(String prop) throws MojoExecutionException
    {
        if (prop == null)
        {
            return FAMILY;
        }
        Optional<Scope> result = enumMatcher(Scope.class).match(prop);
        if (result.isEmpty())
        {
            String msg = "Unknown scope " + prop + " is not one of " + Arrays
                    .toString(Scope.values());
            throw new MojoExecutionException(Scope.class, msg, msg);
        }
        return result.get();
    }

    public boolean appliesFamily()
    {
        return this == FAMILY || this == FAMILY_OR_CHILD_FAMILY;
    }

    /**
     * Get a depth-first list of checkouts matching this scope, given the passed
     * contextual criteria.
     *
     * @param tree A project tree
     * @param callingProjectsCheckout The checkout of the a mojo is currently
     * being run against.
     * @param includeRoot If true, include the root (submodule parent) checkout
     * in the returned list regardless of whether it directly contains a maven
     * project matching the other criteria (needed for operations that change
     * the head commit of a submodule, which will generate modifications in the
     * submodule parent project.
     * @param callingProjectsGroupId The group id of the project whose mojo is
     * being invoked
     */
    public List<GitCheckout> matchCheckouts(ProjectTree tree,
            GitCheckout callingProjectsCheckout, boolean includeRoot,
            ProjectFamily family, String callingProjectsGroupId)
    {
        Set<GitCheckout> checkouts;
        switch (this)
        {
            case FAMILY:
                checkouts = tree.checkoutsInProjectFamily(family);
                break;
            case FAMILY_OR_CHILD_FAMILY:
                checkouts = tree.checkoutsInProjectFamilyOrChildProjectFamily(
                        family);
                break;
            case SAME_GROUP_ID:
                checkouts = tree.checkoutsContainingGroupId(
                        callingProjectsGroupId);
                break;
            case JUST_THIS:
                checkouts = new HashSet<>(Arrays.asList(callingProjectsCheckout));
                break;
            case ALL:
                checkouts = new HashSet<>(tree.allCheckouts());
                checkouts.addAll(tree.nonMavenCheckouts());
                break;
            default:
                throw new AssertionError(this);
        }
        checkouts = new LinkedHashSet<>(checkouts);
        if (!includeRoot)
        {
            callingProjectsCheckout.submoduleRoot().ifPresent(checkouts::remove);
        }
        else
        {
            if (!checkouts.isEmpty()) // don't generate a push of _just_ the root checkout
            {
                callingProjectsCheckout.submoduleRoot()
                        .ifPresent(checkouts::add);
            }
        }
        return GitCheckout.depthFirstSort(checkouts);
    }
}
