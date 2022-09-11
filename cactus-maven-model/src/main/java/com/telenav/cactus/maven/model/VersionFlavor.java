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
package com.telenav.cactus.maven.model;

import java.util.Optional;

import static com.telenav.cactus.maven.model.VersionFlavorChange.TO_RELEASE;
import static com.telenav.cactus.maven.model.VersionFlavorChange.TO_SNAPSHOT;
import static com.telenav.cactus.maven.model.VersionFlavorChange.UNCHANGED;

/**
 * Describes the kind of suffix a version has:
 * <ul>
 * <li>No suffix = RELEASE</li>
 * <li>-SNAPSHOT = SNAPSHOT</li>
 * <li>Anything else = OTHER</li>
 * </ul>
 *
 * @author Tim Boudreau
 */
public enum VersionFlavor
{
    /**
     * A snapshot version.
     */
    SNAPSHOT,
    /**
     * A release version (no suffix).
     */
    RELEASE,
    /**
     * A version which has a suffix after the decimals which is not the string
     * <code>-SNAPSHOT</code>.
     */
    OTHER;

    @Override
    public String toString()
    {
        return name().replace('_', '-').toLowerCase();
    }

    public VersionFlavorChange toThis()
    {
        switch (this)
        {
            case SNAPSHOT:
                return TO_SNAPSHOT;
            case RELEASE:
                return TO_RELEASE;
            default:
                return UNCHANGED;
        }
    }

    public boolean isSnapshot()
    {
        return this == SNAPSHOT;
    }

    public boolean isRelease()
    {
        return this == RELEASE;
    }

    public boolean isSuffixed()
    {
        return this != RELEASE;
    }

    /**
     * Get the opposite of this flavor - release for snapshot, snapshot for
     * release. OTHER has no opposite and returns itself.
     *
     * @return A flavor
     */
    public VersionFlavor opposite()
    {
        switch (this)
        {
            case RELEASE:
                return SNAPSHOT;
            case SNAPSHOT:
                return RELEASE;
            default:
                return this;
        }
    }

    Optional<String> suffixFor(PomVersion ver)
    {
        switch (this)
        {
            case SNAPSHOT:
                return Optional.of("-SNAPSHOT");
            case RELEASE:
                return Optional.empty();
            default:
                return ver.suffix();
        }
    }

    /**
     * Get the version flavor for a string.
     *
     * @param what A version string
     * @return A flavor
     */
    public static VersionFlavor of(String what)
    {
        return PomVersion.suffixOf(what)
                .map(sfx ->
                {
                    switch (sfx)
                    {
                        case "-SNAPSHOT":
                            return VersionFlavor.SNAPSHOT;
                        default:
                            return VersionFlavor.OTHER;
                    }
                }).orElse(VersionFlavor.RELEASE);
    }
}
