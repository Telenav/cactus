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
package com.telenav.cactus.test.project.generator;

import com.telenav.cactus.maven.model.MavenArtifactCoordinates;
import com.telenav.cactus.maven.model.MavenIdentified;
import com.telenav.cactus.maven.model.MavenVersioned;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.write;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Arrays.fill;

/**
 * Generates usable pom files.
 *
 * @author Tim Boudreau
 */
class PomGenerator
{
    private static final String POM_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project xmlns:xsi = \"http://www.w3.org/2001/XMLSchema-instance\" xmlns = \"http://maven.apache.org/POM/4.0.0\"\n" + "         xsi:schemaLocation = \"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n";
    private static final String POM_TAIL = "\n</project>\n";
    final Map<String, String> properties = new HashMap<>();
    MavenArtifactCoordinates parent;
    final Set<MavenIdentified> dependencies = new HashSet<>();
    final Set<MavenArtifactCoordinates> dependencyMgmt = new HashSet<>();
    final String groupId;
    final String artifactId;
    final String packaging;
    final String version;
    final Set<String> modules = new TreeSet<>();
    final Set<MavenArtifactCoordinates> imports = new HashSet<>();
    Path parentRelativePath;

    PomGenerator(String groupId, String artifactId, String packaging,
            String version)
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.packaging = packaging;
        this.version = version;
    }

    PomGenerator(ProjectsGenerator.FakeProject fp)
    {
        this.groupId = fp.info.groupId();
        this.artifactId = fp.info.artifactId();
        this.dependencies.addAll(fp.dependencies);
        this.dependencyMgmt.addAll(fp.dependencyMgmt);
        this.properties.putAll(fp.properties);
        this.modules.addAll(fp.modules);
        this.parent = fp.parent;
        this.version = fp.version;
        this.packaging = fp.packaging;
        if (parent != null)
        {
            this.parentRelativePath = fp.info.relativePathIfWithinSameCheckout(
                    parent.groupId()
                            .text(), parent.artifactId().text());
            if (this.parentRelativePath != null && !"pom.xml".equals(
                    this.parentRelativePath.getFileName()))
            {
                this.parentRelativePath = this.parentRelativePath.resolve(
                        "pom.xml");
            }
        }
    }

    PomGenerator(ProjectsGenerator.Superpom fp)
    {
        this.groupId = fp.info.groupId();
        this.artifactId = fp.info.artifactId();
        this.dependencies.addAll(fp.dependencies);
        this.dependencyMgmt.addAll(fp.dependencyMgmt);
        this.properties.putAll(fp.properties);
        this.modules.addAll(fp.modules);
        this.parent = fp.parent;
        this.version = fp.version;
        this.packaging = "pom";
        this.imports.addAll(fp.imports);
        if (parent != null)
        {
            this.parentRelativePath = fp.info.relativePathIfWithinSameCheckout(
                    parent.groupId()
                            .text(), parent.artifactId().text());
        }
    }

    PomGenerator withParentRelativePath(Path pth)
    {
        this.parentRelativePath = pth;
        return this;
    }

    PomGenerator withModule(String module)
    {
        this.modules.add(module);
        return this;
    }

    PomGenerator setParent(MavenArtifactCoordinates coords)
    {
        this.parent = coords;
        return this;
    }

    PomGenerator addDependency(MavenIdentified id)
    {
        dependencies.add(id);
        return this;
    }

    PomGenerator addDependencyManagement(MavenArtifactCoordinates coords)
    {
        this.dependencyMgmt.add(coords);
        return this;
    }

    PomGenerator addProperty(String k, String v)
    {
        properties.put(k, v);
        return this;
    }

    Path generate(Path into) throws IOException
    {
        if (!exists(into.getParent()))
        {
            createDirectories(into.getParent());
        }
        write(into, toString().getBytes(UTF_8), WRITE, TRUNCATE_EXISTING, CREATE);
        return into;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(POM_HEAD);
        tag(1, "modelVersion", sb, "4.0.0");
        sb.append('\n');
        if (parent != null)
        {
            inTag(1, "parent", sb,
                    () ->
            {
                tag(2, "groupId", sb, parent.groupId());
                tag(2, "artifactId", sb, parent.artifactId());
                tag(2, "version", sb, parent.version());
                if (parentRelativePath != null)
                {
                    if (!parentRelativePath.toString().equals("..") && !parentRelativePath
                            .toString()
                            .equals("../") && !parentRelativePath.toString()
                            .equals("../pom.xml"))
                    {
                        tag(2, "relativePath", sb, parentRelativePath.toString());
                    }
                }
                else
                {
                    tag(2, "relativePath", sb);
                }
            });
            sb.append("\n");
            if (!parent.groupId().is(groupId))
            {
                tag(1, "groupId", sb, groupId);
            }
            if (!parent.version().is(version))
            {
                tag(1, "version", sb, version);
            }
            tag(1, "artifactId", sb, artifactId);
        }
        else
        {
            tag(1, "groupId", sb, groupId);
            tag(1, "artifactId", sb, artifactId);
            tag(1, "version", sb, version);
        }
        sb.append('\n');
        tag(1, "packaging", sb, packaging);
        sb.append('\n');
        tag(1, "name", sb, artifactId);
        tag(1, "description", sb, "This is " + groupId + ":" + artifactId);
        sb.append('\n');
        if (!properties.isEmpty())
        {
            inTag(1, "properties", sb,
                    () ->
            {
                properties.forEach((k, v) ->
                {
                    tag(2, k, sb, v);
                });
            });
            sb.append('\n');
        }
        if (!dependencies.isEmpty())
        {
            inTag(1, "dependencies", sb,
                    () ->
            {
                dependencies.forEach(d -> {
                    inTag(2, "dependency", sb,
                            () ->
                            {
                                if (d.groupId().is(groupId))
                                {
                                    tag(3, "groupId", sb, "${project.groupId}");
                                }
                                else
                                {
                                    tag(3, "groupId", sb, d.groupId());
                                }
                                tag(3, "artifactId", sb, d.artifactId());
                                if (d instanceof MavenVersioned)
                                {
                                    if (!d.groupId().is(groupId))
                                    {
                                        tag(3, "version", sb,
                                                ((MavenVersioned) d).version());
                                    }
                                }
                            });
                });
            });
        }
        if (!modules.isEmpty())
        {
            inTag(1, "modules", sb,
                    () ->
            {
                modules.forEach(m -> tag(2, "module", sb, m));
            });
        }
        boolean hasBuildSections = !dependencyMgmt.isEmpty();
        if (hasBuildSections)
        {
            if (!dependencyMgmt.isEmpty())
            {
                inTag(1, "dependencyManagement", sb,
                        () ->
                {
                    inTag(2, "dependencies", sb,
                            () ->
                    {
                        dependencyMgmt.forEach(d -> {
                            inTag(3, "dependency", sb,
                                    () ->
                                    {
                                        if (d.groupId().is(groupId))
                                        {
                                            tag(4, "groupId", sb, "${project.groupId}");
                                        }
                                        else
                                        {
                                            tag(4, "groupId", sb, d.groupId());
                                        }
                                        tag(4, "artifactId", sb, d.artifactId());
                                        tag(4, "version", sb, d.version());
                                    });
                        });
                        imports.forEach(d -> {
                            inTag(3, "dependency", sb,
                                    () ->
                                    {
                                        if (d.groupId().is(groupId))
                                        {
                                            tag(4, "groupId", sb, "${project.groupId}");
                                        }
                                        else
                                        {
                                            tag(4, "groupId", sb, d.groupId());
                                        }
                                        tag(4, "artifactId", sb, d.artifactId());
                                        tag(4, "version", sb, d.version());
                                        tag(4, "type", sb, "pom");
                                        tag(4, "scope", sb, "import");
                                    });
                        });
                    });
                });
            }
        }
        return sb.append(POM_TAIL).toString();
    }

    private static void tag(int depth, String tag, StringBuilder into,
            Object content)
    {
        char[] ind = new char[1 + (depth * 4)];
        fill(ind, ' ');
        ind[0] = '\n';
        into.append(ind);
        into.append("<");
        into.append(tag);
        into.append(">");
        into.append(content);
        into.append("</");
        into.append(tag);
        into.append(">");
    }

    private static void tag(int depth, String tag, StringBuilder into)
    {
        char[] ind = new char[1 + (depth * 4)];
        fill(ind, ' ');
        ind[0] = '\n';
        into.append(ind);
        into.append("<");
        into.append(tag);
        into.append("/>");
    }

    private static void inTag(int depth, String tag, StringBuilder into,
            Runnable r)
    {
        char[] ind = new char[1 + (depth * 4)];
        fill(ind, ' ');
        ind[0] = '\n';
        into.append(ind);
        into.append("<");
        into.append(tag);
        into.append(">");
        r.run();
        into.append(ind);
        into.append("</");
        into.append(tag);
        into.append(">");
    }

}
/*
public static void main(String[] args)
{
PomGenerator gen = new PomGenerator("com.wurgles", "glorp-parent", "pom",
"1.0.0");
//        gen.setParent(new MavenCoordinates("com.wurgles.sups", "wurgles-superpom", "1.0.0"));
gen.addProperty("wook.version", "2.1.5");
gen.addDependency(new MavenCoordinates("com.wook", "wookie-food",
"${wook.version}"));
gen.addDependency(new MavenCoordinates("com.wook", "wookie-silverware",
"${wook.version}"));
gen.withModule("wook-adapter");
gen.withModule("wook-spaceships");
gen.addDependencyManagement(new MavenCoordinates("com.wook",
"wookie-food",
"${wook.version}"));
gen.addDependencyManagement(new MavenCoordinates("com.wook",
"wookie-silverware",
"${wook.version}"));
System.out.println("GEN\n" + gen);
}
 */
