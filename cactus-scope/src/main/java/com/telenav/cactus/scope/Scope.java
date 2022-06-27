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
package com.telenav.cactus.scope;


import java.util.Arrays;
import java.util.Optional;

import static com.telenav.cactus.util.EnumMatcher.enumMatcher;

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
     * Operate on all checkouts which contain at least one pom.xml file.
     * 
     * @see ProjectFamily
     */
    ALL_PROJECT_FAMILIES,
    /**
     * Operate on all git submodules containing a root pom.xml within any
     * submodule below the root of the project tree the project maven was
     * invoked against lives in.
     */
    ALL;

    @Override
    public String toString()
    {
        return name().toLowerCase().replace('_', '-');
    }

    public static Scope find(String prop) throws Exception
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
            throw new IllegalStateException(msg);
        }
        return result.get();
    }

    public boolean appliesFamily()
    {
        return this == FAMILY || this == FAMILY_OR_CHILD_FAMILY;
    }

}
