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
package com.telenav.cactus.maven.model.property;

import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.resolver.PomResolver;
import java.util.Iterator;
import java.util.List;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

/**
 * PropertyResolver which simply resolves the project's version, groupId and
 * artifactId, and those of its parent if it has one.
 *
 * @author Tim Boudreau
 */
public final class CoordinatesPropertyResolver extends AbstractPropertyResolver
{
    private static final String PARENT_VERSION = "parent.version";
    private static final String PROJECT_PARENT_VERSION = "project.parent.version";
    private static final String PARENT_ARTIFACT_ID = "parent.artifactId";
    private static final String PROJECT_PARENT_ARTIFACT_ID = "project.parent.artifactId";
    private static final String PARENT_GROUP_ID = "parent.groupId";
    private static final String PROJECT_PARENT_GROUP_ID = "project.parent.groupId";
    private static final String PROJECT_GROUP_ID = "project.groupId";
    private static final String PROJECT_ARTIFACT_ID = "project.artifactId";
    private static final String PROJECT_VERSION = "project.version";
    private static final String MAVEN_VERSION = "maven.version";
    private static final String JAVA_VERSION = "java.version";
    private static final String BASEDIR = "basedir";
    private static final String PROJECT_BASEDIR = "project.basedir";
    private static final String PARENT_BASEDIR = "parent.basedir";
    private static final String PROJECT_PARENT_BASEDIR = "project.parent.basedir";
    private static String FAKE_MAVEN_VERSION = "3.8.5";

    // Pending:  could also resolve basedir/project.basedir, if things need it
    private static final List<String> NO_PARENT_KEYS = unmodifiableList(
            asList(
                    PROJECT_GROUP_ID, PROJECT_ARTIFACT_ID, PROJECT_VERSION,
                    BASEDIR, PROJECT_BASEDIR,
                    MAVEN_VERSION, JAVA_VERSION
            ));

    private static final List<String> WITH_PARENT_KEYS = unmodifiableList(
            asList(
                    PROJECT_GROUP_ID, PROJECT_ARTIFACT_ID, PROJECT_VERSION,
                    PARENT_GROUP_ID, PARENT_ARTIFACT_ID, PARENT_VERSION,
                    PROJECT_PARENT_GROUP_ID, PROJECT_PARENT_ARTIFACT_ID,
                    PROJECT_PARENT_VERSION,
                    MAVEN_VERSION, JAVA_VERSION,
                    BASEDIR, PROJECT_BASEDIR, PARENT_BASEDIR,
                    PROJECT_PARENT_BASEDIR
            ));

    private final Pom self;
    private final Pom parent;
    private final List<String> keys;

    /**
     * Probably this can be found within a mojo at runtime - not any better way
     * to do it than provide an opportunity to set it.
     *
     * @param mavenVersion
     */
    public static void setMavenVersion(String mavenVersion)
    {
        FAKE_MAVEN_VERSION = mavenVersion;
    }

    public CoordinatesPropertyResolver(Pom self, Pom parent)
    {
        this.self = notNull("self", self);
        this.parent = parent; // can be null
        if (PROJECT_GROUP_ID.equals(self.groupId().text()))
        {
            throw new IllegalStateException(self + "");
        }
        if (parent != null && PROJECT_GROUP_ID.equals(parent.groupId().text()))
        {
            throw new IllegalStateException(parent + "");
        }
        if (parent != null)
        {
            keys = WITH_PARENT_KEYS;
        }
        else
        {
            keys = NO_PARENT_KEYS;
        }
    }

    public CoordinatesPropertyResolver(Pom pom, PomResolver res)
    {
        this(pom, pom.parent().flatMapThrowing(x -> res.get(x)).orElse(null));
    }

    @Override
    public String toString()
    {
        return "Coordinates(" + self + (parent == null
                                        ? ", " + parent
                                        : "") + ")";
    }

    @Override
    protected String valueFor(String k)
    {
        if (k.startsWith("${"))
        {
        }
        if (parent != null)
        {
            switch (k)
            {
                case PROJECT_PARENT_GROUP_ID:
                case PARENT_GROUP_ID:
                    return parent.groupId().text();
                case PROJECT_PARENT_ARTIFACT_ID:
                case PARENT_ARTIFACT_ID:
                    return parent.artifactId().text();
                case PROJECT_PARENT_VERSION:
                case PARENT_VERSION:
                    if (parent.resolvedVersion().isPresent())
                    {
                        return parent.resolvedVersion().get();
                    }
                    return null;
                case PARENT_BASEDIR:
                    return parent.projectFolder().toString();
            }
        }
        switch (k)
        {
            case MAVEN_VERSION:
                return FAKE_MAVEN_VERSION;
            case JAVA_VERSION:
                return System.getProperty("java.version");
            case PROJECT_GROUP_ID:
                return self.groupId().text();
            case PROJECT_VERSION:
                if (self.resolvedVersion().isPresent())
                {
                    return self.resolvedVersion().get();
                }
                break;
            case PROJECT_ARTIFACT_ID:
                return self.artifactId().text();
        }
        return null;
    }

    @Override
    public Iterator<String> iterator()
    {
        return keys.iterator();
    }

}
