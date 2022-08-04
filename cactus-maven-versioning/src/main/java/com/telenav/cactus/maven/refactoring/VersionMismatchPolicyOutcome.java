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
package com.telenav.cactus.maven.refactoring;

import com.telenav.cactus.maven.model.VersionChange;
import com.telenav.cactus.maven.model.Pom;
import java.util.Set;

/**
 * What to do when a version mismatch is encountered.
 *
 * @author Tim Boudreau
 */
public enum VersionMismatchPolicyOutcome implements VersionMismatchPolicy
{
    /**
     * Just do nothing to the non-matching pom.
     */
    SKIP,
    /**
     * Clobber the version with whatever it's supposed to be regardless of its
     * current value.
     */
    COERCE_TO_TARGET_VERSION,
    /**
     * Bump the version, using the change for the family (if any) as a basis
     * (magnitude, flavor).
     */
    BUMP,
    /**
     * Throw an exception and don't proceed in the case of this mismatch.
     */
    ABORT;

    @Override
    public String toString()
    {
        return name().replace('_', '-').toLowerCase();
    }

    @Override
    public VersionMismatchPolicyOutcome onMismatch(Pom what,
            VersionChange expectedVersionChange, Set<PomRole> roles)
    {
        return this;
    }

    @Override
    public VersionMismatchPolicy or(VersionMismatchPolicy other)
    {
        throw new IllegalStateException("Cannot or with " + this
                + " - it will never return null.");
    }
}
