package com.telenav.cactus.maven;

import com.mastfrog.function.throwing.ThrowingRunnable;
import static com.mastfrog.util.preconditions.Checks.notNull;
import org.apache.maven.project.MavenProject;

/**
 * A project family a Maven project may belong to, as determined by the last
 * dot-delimited portion of a maven group id, omitting any hyphen-delimited
 * suffix. <code>^.*\.(\S+)-?.*</code>.
 *
 * @author Tim Boudreau
 */
public final class ProjectFamily implements Comparable<ProjectFamily>
{

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
            notNull("run", run).toRunnable().run();
            return true;
        }
        return false;
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
            code.toRunnable().run();
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
        String gid = notNull("prj", prj).getGroupId();
        int ix = gid.lastIndexOf('.');
        if (ix > 0)
        {
            gid = gid.substring(0, ix);
        }
        return fromGroupId(gid).equals(this);
    }

    /**
     * Get a project family with an explicit name.
     *
     * @param name
     * @return
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
            throw new IllegalArgumentException("Leading and trailing - not allowed: '"
                    + tail + "'");
        }
        if (dashIndex > 0)
        {
            tail = tail.substring(0, dashIndex);
        }
        return new ProjectFamily(tail);
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

    @Override
    public int hashCode()
    {
        return name.hashCode() * 71;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        } else
        {
            if (o == null || o.getClass() != ProjectFamily.class)
            {
                return false;
            }
        }
        return ((ProjectFamily) o).name.equals(name);
    }

    @Override
    public int compareTo(ProjectFamily o)
    {
        return name.compareToIgnoreCase(o.name);
    }
}
