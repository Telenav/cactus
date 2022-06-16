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

package com.telenav.cactus.maven.scope;

import com.mastfrog.function.optional.ThrowingOptional;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.cli.PathUtils;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * A project family a Maven project may belong to, as determined by the last
 * dot-delimited portion of a maven group id, omitting any hyphen-delimited
 * suffix. <code>^.*\.(\S+)-?.*</code>.
 *
 * @author Tim Boudreau
 */
public final class ProjectFamily implements Comparable<ProjectFamily>
{
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

    /**
     * Get the logical project family for a maven project.
     *
     * @param prj A project
     * @return A family
     */
    public static ProjectFamily of(MavenProject prj)
    {
        return fromGroupId(notNull("prj", prj).getGroupId());
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

    public String assetsEnvironmentVariable()
    {
        return name.toUpperCase() + "_ASSETS_HOME";
    }

    public ThrowingOptional<Path> assetsPath(GitCheckout checkout)
    {
        String envVar = System.getenv(assetsEnvironmentVariable());
        Path path = null;
        if (envVar != null)
        {
            path = Paths.get(envVar);
            ThrowingOptional<Path> result = ThrowingOptional.from(PathUtils
                    .ifDirectory(path));
            // If the directory pointed to by the environment variable does not
            // actually exist, use the search strategy instead
            if (result.isPresent())
            {
                return result;
            }
        }
        return checkout.submoduleRoot().flatMap(root ->
        {
            return PathUtils.ifDirectory(root.checkoutRoot().resolve(
                    name + "-assets"));
        });
    }

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
    public boolean ifMember(MavenProject prj, ThrowingRunnable run)
    {
        if (is(prj))
        {
            // Pending - use toNonThrowing w/ next maven release:
            notNull("run", run).toRunnable().run();
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
    public boolean ifMemberOrParent(MavenProject prj, ThrowingRunnable code)
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
    public boolean ifParentFamily(MavenProject prj, ThrowingRunnable code)
    {
        if (isParentFamilyOf(prj))
        {
            // Pending - use toNonThrowing w/ next maven release:
            code.toRunnable().run();
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
    public boolean is(MavenProject prj)
    {
        return of(prj).equals(this);
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
    public boolean isParentFamilyOf(MavenProject prj)
    {
        return isParentFamilyOf(notNull("prj", prj).getGroupId());
    }

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
}
