package com.telenav.cactus.maven.model;

import com.mastfrog.function.optional.ThrowingOptional;
import com.mastfrog.function.throwing.ThrowingSupplier;
import com.telenav.cactus.maven.model.internal.PomFile;
import org.w3c.dom.Node;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * @author Tim Boudreau
 */
public class MavenCoordinates extends MavenId implements
        Comparable<MavenCoordinates>,
        MavenIdentified, MavenVersioned
{
    static final String PLACEHOLDER = "---";

    public static Optional<MavenCoordinates> from(Path pomFile)
    {
        // Pending - wipeable cache?
        try
        {
            return Optional.of(new PomFile(pomFile).coordinates());
        }
        catch (Exception ex)
        {
            return Optional.empty();
        }
    }

    public final String version;

    public MavenCoordinates(Node groupId, Node artifactId, Node version)
    {
        this(textOrPlaceholder(groupId), textOrPlaceholder(artifactId),
                textOrPlaceholder(version));
    }

    public MavenCoordinates(String groupId, String artifactId, String version)
    {
        super(notNull("groupId", groupId), notNull("artifactId", artifactId));
        this.version = version == null
                       ? PLACEHOLDER
                       : version;
    }

    public MavenCoordinates withVersion(String newVersion)
    {
        return new MavenCoordinates(groupId, artifactId, newVersion);
    }

    public MavenId toMavenId()
    {
        return new MavenId(groupId(), artifactId());
    }

    @Override
    public String groupId()
    {
        return groupId;
    }

    @Override
    public String artifactId()
    {
        return artifactId;
    }

    boolean isVersionResolved()
    {
        return !PLACEHOLDER.equals(version) && version != null
                && PropertyResolver.isResolved(version);
    }
    
    private MavenCoordinates resolveGidAid(PropertyResolver res) {
        if (!PropertyResolver.isResolved(groupId) || !PropertyResolver.isResolved(artifactId)) {
            String gid = res.resolve(groupId);
            String aid = res.resolve(artifactId);
            if (aid == null) {
                aid = artifactId;
            }
            if (gid == null) {
                gid = groupId;
            }
            if (!aid.equals(artifactId) || !gid.equals(groupId)) {
                return new MavenCoordinates(gid, aid, version);
            }
        }
        return this;
    }

    public MavenCoordinates resolve(PropertyResolver res, PomResolver poms)
    {
        if (PLACEHOLDER.equals(version))
        {
            ThrowingOptional<Pom> pom = poms.get(groupId, artifactId);
            if (pom.isPresent())
            {
                MavenCoordinates cds = pom.get().coords.resolveGidAid(res);
                if (cds != this)
                {
                    return cds.resolve(res, poms).resolveGidAid(res);
                }
            }
        }
        else
            if (!PropertyResolver.isResolved(version))
            {
                return resolveGidAid(res).withResolvedVersion(() -> res.resolve(version));
            }
        return resolveGidAid(res);
    }

    @Override
    public ThrowingOptional<String> version()
    {
        return isVersionResolved()
               ? ThrowingOptional.of(version)
               : ThrowingOptional.empty();
    }

    public MavenCoordinates withResolvedVersion(Supplier<String> versionResolver)
    {
        if (!isVersionResolved())
        {
            String newVersion = versionResolver.get();
            if (newVersion != null)
            {
                return withVersion(newVersion);
            }
        }
        return this;
    }

    public MavenCoordinates withResolvedVersionThrowing(
            ThrowingSupplier<String> versionResolver) throws Exception
    {
        if (!isVersionResolved())
        {
            String newVersion = versionResolver.get();
            if (newVersion != null)
            {
                return withVersion(newVersion);
            }
        }
        return this;
    }

    @Override
    public int compareTo(MavenCoordinates o)
    {
        int result = groupId.compareTo(o.groupId);
        if (result == 0)
        {
            result = artifactId.compareTo(o.artifactId);
        }
        if (result == 0)
        {
            result = version.compareTo(o.version);
        }
        return result;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        else
            if (o == null || o.getClass() != MavenCoordinates.class)
            {
                return false;
            }
        MavenCoordinates other = (MavenCoordinates) o;
        return version.equals(other.version) && artifactId.equals(
                other.artifactId)
                && groupId.equals(other.groupId);
    }

    @Override
    public int hashCode()
    {
        return (137 * artifactId.hashCode())
                + (3 * groupId.hashCode())
                + (11 * version.hashCode());
    }

    public boolean is(MavenCoordinates other)
    {
        return other.groupId.equals(groupId) && other.artifactId.equals(
                artifactId);
    }

    @Override
    public String toString()
    {
        return groupId + ":" + artifactId + ":" + version;
    }
}
