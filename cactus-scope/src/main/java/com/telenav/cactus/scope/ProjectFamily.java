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

import com.mastfrog.function.optional.ThrowingOptional;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.strings.LevenshteinDistance;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.MavenArtifactCoordinates;
import com.telenav.cactus.maven.model.MavenIdentified;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.util.PathUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.telenav.cactus.maven.model.PomVersion.mostCommonVersion;
import static java.util.stream.Collectors.toCollection;

/**
 * A project family a Maven project may belong to, as determined by the last
 * dot-delimited portion of a maven group id, omitting any hyphen-delimited
 * suffix - roughly <code>^.*\.(\S+)-?.*</code>.
 *
 * @author Tim Boudreau
 */
public final class ProjectFamily implements Comparable<ProjectFamily>
{
    /**
     * Suffix for an environment variable that indicates the destination on disk
     * for "assets" files (documentation and similar) that may be emitted by the
     * build - for example, for use with github pages - which should be the the
     * preferred output location for such files if it is set and if the
     * directory it points to actually exists on disk.
     */
    public static final String ASSETS_HOME_SUFFIX = "_ASSETS_HOME";
    /**
     * Suffix for a folder or git checkout in the submodule root (if there is
     * one) that indicates the destination on disk for "assets" files
     * (documentation and similar) that may be emitted by the build - for
     * example, for use with github pages - which should be the the preferred
     * output location for such files if it is set and if the directory it
     * points to actually exists on disk.
     */
    public static final String ASSETS_CHECKOUT_SUFFIX = "-assets";

    /**
     * Get the project family for a maven group-id.
     *
     * @param groupId A group id
     * @return A family
     */
    public static ProjectFamily familyOf(GroupId groupId)
    {
        return fromGroupId(groupId.text());
    }

    /**
     * Get the logical project family for a maven artifact.
     *
     * @param artifact A project
     * @return A family
     */
    public static ProjectFamily familyOf(MavenIdentified artifact)
    {
        return familyOf(artifact.groupId());
    }

    /**
     * Get the project family for a maven group-id string.
     *
     * @param groupId A group id
     * @return A family
     */
    public static ProjectFamily fromGroupId(String groupId)
    {
        if (notNull("groupId", groupId).indexOf('.') < 0)
        {
            return named(groupId);
        }
        int lastDot = groupId.lastIndexOf('.');
        if (lastDot == groupId.length() - 1)
        {
            throw new IllegalArgumentException("Trailing . not allowed: '"
                    + groupId + "'");
        }
        String tail = groupId.substring(lastDot + 1);
        int dashIndex = tail.indexOf('-');
        if (dashIndex == 0 || dashIndex == tail.length() - 1)
        {
            throw new IllegalArgumentException(
                    "Leading and trailing - not allowed: '"
                    + tail + "'");
        }
        if (dashIndex > 0)
        {
            tail = tail.substring(0, dashIndex);
        }
        return new ProjectFamily(tail);
    }

    /**
     * Get a project family with an explicit name.
     */
    public static ProjectFamily named(String name)
    {
        return new ProjectFamily(notNull("name", name));
    }

    private final String name;

    private ProjectFamily(String name)
    {
        if (name.isEmpty())
        {
            throw new IllegalArgumentException("Empty family name");
        }
        if (name.indexOf('.') >= 0)
        {
            throw new IllegalArgumentException("Family may not contain dots: '"
                    + name + "'");
        }
        this.name = name;
    }

    /**
     * Get an environment variable based on the family name in upper-case with
     * the suffix {@link ProjectFamily#ASSETS_HOME} which may determine the
     * destination for "assets" (non-maven documentation files and similar)
     * which are emitted by the build (lexakai, codeflowers, javadoc) should be
     * put.
     *
     * @return
     */
    public String assetsEnvironmentVariable()
    {
        return name.toUpperCase() + ASSETS_HOME_SUFFIX;
    }

    /**
     * Get a path to an "assets" (non-maven documentation files and similar)
     * destination for projects in this project family, using either the
     * environment variable provided by {@link ProjectFamily#assetsEnvironmentVariable()
     * } or as a git submodule named <code>$FAMILY-assets</code> in the
     * submodule root of the passed path, if present.
     *
     * @param submoduleRoot A submodule root
     * @return An assets path, if one can be determined and if it exists on disk
     */
    public ThrowingOptional<Path> assetsPath(
            ThrowingOptional<Path> submoduleRoot)
    {
        String envVar = System.getenv(assetsEnvironmentVariable());
        Path path = null;
        if (envVar != null)
        {
            path = Paths.get(envVar);
            ThrowingOptional<Path> result
                    = ThrowingOptional.from(PathUtils.ifDirectory(path));
            // If the directory pointed to by the environment variable does not
            // actually exist, use the search strategy instead
            if (result.isPresent())
            {
                return result;
            }
        }
        return submoduleRoot.flatMap(root ->
        {
            return PathUtils.ifDirectory(root.resolve(
                    name + ASSETS_CHECKOUT_SUFFIX));
        });
    }

    /**
     * Case insensitive comparison on the text value.
     *
     * @param o Another project family
     * @return A comparison result
     */
    @Override
    public int compareTo(ProjectFamily o)
    {
        return name.compareToIgnoreCase(o.name);
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        else
        {
            if (o == null || o.getClass() != ProjectFamily.class)
            {
                return false;
            }
        }
        return ((ProjectFamily) o).name.equals(name);
    }

    @Override
    public int hashCode()
    {
        return name.hashCode() * 71;
    }

    /**
     * Runs some code only if the passed project is a member of this family.
     *
     * @param prj A project
     * @param run Something to run
     * @return true if the code was run
     */
    public boolean ifMember(MavenIdentified prj, ThrowingRunnable run)
    {
        if (is(prj))
        {
            notNull("run", run).toNonThrowing().run();
            return true;
        }
        return false;
    }

    /**
     * Run some code if this family is the family or parent family of the passed
     * project.
     *
     * @param prj A project
     * @param code Some code
     * @return True if the code was run.
     */
    public boolean ifMemberOrParent(MavenIdentified prj, ThrowingRunnable code)
    {
        boolean result = ifMember(prj, code);
        if (!result)
        {
            result = ifParentFamily(prj, code);
        }
        return result;
    }

    /**
     * Run some code only if the passed project's <i>parent family</i> is the
     * same as this ProjectFamily.
     *
     * @param prj A project
     * @param code Something to run
     * @return true if the code was run
     */
    public boolean ifParentFamily(MavenIdentified prj, ThrowingRunnable code)
    {
        if (isParentFamilyOf(prj))
        {
            code.toNonThrowing().run();
            return true;
        }
        return false;
    }

    /**
     * Determine if the passed project is a member of this family.
     *
     * @param prj A project
     * @return true if it is a member
     */
    public boolean is(MavenIdentified prj)
    {
        return familyOf(prj).equals(this);
    }

    /**
     * Determine if this family is the <i>parent family</i> of the passed
     * project - the next-to-last dot-delimited portion is a match for this
     * family name - for example, the parent family of <code>com.foo.bar</code>
     * is <code>foo</code>. This is useful when a bill-of-materials POM uses the
     * parent name, but we want to run actions for all sub-families of that
     * family.
     *
     * @param prj A project
     * @return A family
     */
    public boolean isParentFamilyOf(MavenIdentified prj)
    {
        return isParentFamilyOf(notNull("prj", prj).groupId());
    }

    /**
     * Determine if this family is the <i>parent family</i> of the passed
     * project - the next-to-last dot-delimited portion is a match for this
     * family name - for example, the parent family of <code>com.foo.bar</code>
     * is <code>foo</code>. This is useful when a bill-of-materials POM uses the
     * parent name, but we want to run actions for all sub-families of that
     * family.
     *
     * @param groupId A group id
     * @return A family
     */
    public boolean isParentFamilyOf(GroupId groupId)
    {
        return isParentFamilyOf(groupId.text());
    }

    /**
     * Run the passed code if this family is the parent family of the passed
     * group id.
     *
     * @param groupId A group id
     */
    public void ifParentFamilyOf(GroupId groupId, Runnable run)
    {
        if (isParentFamilyOf(groupId))
        {
            run.run();
        }
    }

    /**
     * Determine if this family is the parent family of the passed group id
     *
     * @param gid
     * @return
     */
    public boolean isParentFamilyOf(String gid)
    {
        int ix = notNull("gid", gid).lastIndexOf('.');
        if (ix > 0)
        {
            gid = gid.substring(0, ix);
        }
        return fromGroupId(gid).equals(this);
    }

    /**
     * Get the name of this project family.
     *
     * @return A name
     */
    public String name()
    {
        return name;
    }

    @Override
    public String toString()
    {
        return name;
    }

    /**
     * Find the version of a family, as best it can be determined. Uses, the
     * most prevalent version found in the collections, with one twist: If each
     * version occurs exactly once, then the version picked up will be that with
     * the least levenshtein distance to the name of this family (we have
     * exactly this situation with lexakai, lexakai-annotations and
     * lexakai-superpom, each of which has a different version that appears only
     * once).
     *
     * @param coords
     * @return
     */
    public Optional<PomVersion> probableFamilyVersion(
            Collection<? extends MavenArtifactCoordinates> coords)
    {
        List<MavenArtifactCoordinates> filtered
                = coords.stream().filter(this::is)
                        .collect(toCollection(ArrayList::new));

        Comparator<String> nameDistance
                = LevenshteinDistance.distanceComparator(name());

        Comparator<MavenArtifactCoordinates> c = (a, b) ->
        {
            return nameDistance.compare(a.artifactId().text(), b.artifactId()
                    .text());
        };
        return mostCommonVersion(filtered, c);
    }
}
