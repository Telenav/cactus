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

import static com.telenav.cactus.maven.refactoring.VersionMismatchPolicyOutcome.ABORT;

/**
 * If we are updating versions for things, and we encounter a case where we are
 * updating the version from A to B, but the version we find is X, determines
 * what to do. For convenience, VersionMismatchPolicyOutcome implements this
 * interface to return itself, either for use directly or as a fallback to a
 * more complex implementation when it cannot compute a result.
 *
 * @author Tim Boudreau
 */
public interface VersionMismatchPolicy
{

    /**
     * Determine what to do in the event of a mismatch.
     *
     * @param what The pom with a version mismatch - its current version is not
     * the version we were told to expect
     * @param expectedVersionChange The version change we are supposed to be
     * making in this pom or its family
     * @param roles The roles of this pom, which may determine what action is
     * taken
     * @return An outcome
     */
    default VersionMismatchPolicyOutcome mismatchEncountered(Pom what,
            VersionChange expectedVersionChange,
            Set<PomRole> roles)
    {
        VersionMismatchPolicyOutcome outcome = onMismatch(what,
                expectedVersionChange, roles);
        if (outcome == null)
        {
            outcome = ABORT;
        }
        return outcome;
    }

    /**
     * Cobine this policy with another which will be used if this one returns
     * null.
     *
     * @param other Another policy
     * @return a wrapper VersionMismatchPolicy that delegates to this and the
     * passed instance
     */
    default VersionMismatchPolicy or(VersionMismatchPolicy other)
    {
        return (pom, expected, roles) ->
        {
            VersionMismatchPolicyOutcome outcome = onMismatch(pom, expected,
                    roles);
            if (outcome == null)
            {
                outcome = other.onMismatch(pom, expected, roles);
            }
            return outcome;
        };
    }

    /**
     * Determine what to do in the event of a mismatch - since instances support
     * delegation, return null if there is no good answer.
     *
     * @param what The pom with a version mismatch - its current version is not
     * the version we were told to expect
     * @param expectedVersionChange The version change we are supposed to be
     * making in this pom or its family
     * @param roles The roles of this pom, which may determine what action is
     * taken
     * @return An outcome
     */
    VersionMismatchPolicyOutcome onMismatch(Pom what,
            VersionChange expectedVersionChange,
            Set<PomRole> roles);
}
