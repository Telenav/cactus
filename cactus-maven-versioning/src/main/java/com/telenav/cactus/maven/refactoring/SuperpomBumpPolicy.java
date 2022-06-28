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

import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.VersionChange;
import com.telenav.cactus.maven.model.VersionChangeMagnitude;
import com.telenav.cactus.maven.model.VersionFlavorChange;

import static com.telenav.cactus.maven.model.VersionFlavorChange.UNCHANGED;

/**
 * Superpoms - poms that have no modules and provide configuration to other
 * project - may have to be updated because they contain properties named
 * <code>$FAMILY.version</code> or <code>$SOME_ARTIFACT.version</code>, and
 * often do not share their own version with the version of those projects that
 * use them as parents.
 * <p>
 * If we are modifying such a file, we need to decide in what manner we will
 * alter the version of such superpoms.
 * </p>
 *
 * @author Tim Boudreau
 */
public enum SuperpomBumpPolicy
{
    /**
     * Ignore and do not update superpoms - this may result in the build failing
     * with version-mismatches if the VersionMismatchPolicy returns ABORT.
     */
    IGNORE,
    /**
     * Bump the superpom's version by one dot-revision, whether or not it has
     * anything to do with the new version for its project family, and if the
     * superpom is <i>part of</i> a family which is being changed (not one that
     * simply references projects from that family), change its flavor to
     * whatever we are setting for the family - so if family X is becoming
     * 1.0.2-SNAPSHOT, and the superpom's version is 2.1.1, it becomes
     * 2.1.2-SNAPSHOT.
     */
    BUMP_ACQUIRING_NEW_FAMILY_FLAVOR,
    /**
     * Bump the superpom's version by one dot-revision, whether or not it has
     * anything to do with the new version for its project family, but do not
     * change whether it is or is not a snapshot version - leave the suffix
     * alone for superpoms in the same family.
     */
    BUMP_WITHOUT_CHANGING_FLAVOR;

    public VersionChangeMagnitude magnitudeFor(VersionChange newFamilyVersion)
    {
        switch (this)
        {
            case IGNORE:
                return VersionChangeMagnitude.NONE;
            case BUMP_ACQUIRING_NEW_FAMILY_FLAVOR:
            case BUMP_WITHOUT_CHANGING_FLAVOR:
                return newFamilyVersion.magnitudeChange().notNone();
            default:
                throw new AssertionError(this);
        }
    }

    public VersionFlavorChange changeFor(PomVersion newFamilyVersion)
    {
        switch (this)
        {
            case IGNORE:
            case BUMP_WITHOUT_CHANGING_FLAVOR:
                return UNCHANGED;
            case BUMP_ACQUIRING_NEW_FAMILY_FLAVOR:
                return newFamilyVersion.flavor().toThis();
            default:
                throw new AssertionError(this);
        }
    }

    @Override
    public String toString()
    {
        return name().toLowerCase().replace('_', '-');
    }

    boolean isBumpVersion()
    {
        switch (this)
        {
            case BUMP_WITHOUT_CHANGING_FLAVOR:
            case BUMP_ACQUIRING_NEW_FAMILY_FLAVOR:
                return true;
            case IGNORE:
                return false;
            default:
                throw new AssertionError(this);
        }
    }
}
