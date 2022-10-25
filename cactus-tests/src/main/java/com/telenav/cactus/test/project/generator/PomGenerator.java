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

import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.model.MavenArtifactCoordinates;
import com.telenav.cactus.maven.model.MavenCoordinates;
import com.telenav.cactus.maven.model.MavenIdentified;
import com.telenav.cactus.maven.model.MavenVersioned;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
public class PomGenerator
{
    private static final String XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    private static final String POM_HEAD = "<project xmlns:xsi = \"http://www.w3.org/2001/XMLSchema-instance\" xmlns = \"http://maven.apache.org/POM/4.0.0\"\n" + "         xsi:schemaLocation = \"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n";
    private static final String LICENSE_HEAD = "<!-- ///////////////////////////////////////////////////////////////////////////////////////////////////////////////// -->\n"
            + "<!--  -->\n"
            + "<!-- © 2011-2022 Telenav, Inc. -->\n"
            + "<!-- Licensed under Apache License, Version 2.0 -->\n"
            + "<!--  -->\n"
            + "<!-- ///////////////////////////////////////////////////////////////////////////////////////////////////////////////// -->\n";
    private static final String GENERIC_APL = "\n    <licenses>\n"
            + "        <license>\n"
            + "            <name>Apache License, Version 2.0</name>\n"
            + "            <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>\n"
            + "            <distribution>repo</distribution>\n"
            + "        </license>\n"
            + "    </licenses>\n";
    private static final String POM_TAIL = "\n</project>\n";
    final Map<String, String> properties = new TreeMap<>();
    MavenArtifactCoordinates parent;
    final Set<MavenIdentified> dependencies = new TreeSet<>(
            PomGenerator::compareArtifactCoordinates);
    final Set<MavenArtifactCoordinates> dependencyMgmt = new TreeSet<>(
            PomGenerator::compareArtifactCoordinates);
    final Set<MavenArtifactCoordinates> dependencyMgmtTestDeps = new TreeSet<>(
            PomGenerator::compareArtifactCoordinates);
    final Set<MavenArtifactCoordinates> dependencyMgmtProvidedDeps = new TreeSet<>(
            PomGenerator::compareArtifactCoordinates);
    final String groupId;
    final String artifactId;
    final String packaging;
    final String version;
    final Set<String> modules = new TreeSet<>();
    final Set<MavenArtifactCoordinates> imports = new TreeSet<>(
            PomGenerator::compareArtifactCoordinates);
    Path parentRelativePath;
    String description;
    String name;
    GitCheckout scmUrl;
    String url;
    String inceptionYear;

    public PomGenerator(String groupId, String artifactId, String packaging,
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

    public MavenCoordinates coordinates()
    {
        return new MavenCoordinates(groupId, artifactId, version);
    }

    public PomGenerator setUrl(String url)
    {
        this.url = url;
        return this;
    }

    public PomGenerator addModule(String module)
    {
        this.modules.add(module);
        return this;
    }

    public PomGenerator setName(String name)
    {
        this.name = name;
        return this;
    }

    public PomGenerator setScmUrl(GitCheckout co)
    {
        scmUrl = co;
        return this;
    }

    static int compareArtifactCoordinates(MavenArtifactCoordinates a,
            MavenArtifactCoordinates b)
    {
        int result = a.groupId().compareTo(b.groupId());
        if (result == 0)
        {
            result = a.artifactId().compareTo(b.artifactId());
        }
        if (result == 0)
        {
            result = a.version().compareTo(b.version());
        }
        return result;
    }

    static int compareArtifactCoordinates(MavenIdentified a,
            MavenIdentified b)
    {
        int result = a.groupId().compareTo(b.groupId());
        if (result == 0)
        {
            result = a.artifactId().compareTo(b.artifactId());
        }
        return result;
    }

    public PomGenerator withParentRelativePath(Path pth)
    {
        this.parentRelativePath = pth;
        return this;
    }

    public PomGenerator withModule(String module)
    {
        this.modules.add(module);
        return this;
    }

    public PomGenerator setParent(MavenArtifactCoordinates coords)
    {
        this.parent = coords;
        return this;
    }

    public PomGenerator addDependency(MavenIdentified id)
    {
        dependencies.add(id);
        return this;
    }

    public PomGenerator addDependencyManagement(MavenArtifactCoordinates coords)
    {
        this.dependencyMgmt.add(coords);
        return this;
    }

    public PomGenerator addDependencyManagementTestDependency(
            MavenArtifactCoordinates coords)
    {
        this.dependencyMgmtTestDeps.add(coords);
        return this;
    }

    public PomGenerator addDependencyManagementProvidedDependency(
            MavenArtifactCoordinates coords)
    {
        this.dependencyMgmtProvidedDeps.add(coords);
        return this;
    }

    public PomGenerator setDescription(String desc)
    {
        this.description = desc;
        return this;
    }

    public PomGenerator addDependencyManagementImport(
            MavenArtifactCoordinates coords)
    {
        imports.add(coords);
        return this;
    }

    public PomGenerator addProperty(String k, String v)
    {
        properties.put(k, v);
        return this;
    }

    public PomGenerator setInceptionYear(String year)
    {
        inceptionYear = year;
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
        StringBuilder sb = new StringBuilder(XML_HEAD);
        sb.append(LICENSE_HEAD);
        sb.append(POM_HEAD);
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
            tag(1, "artifactId", sb, artifactId);
            if (!parent.version().is(version))
            {
                tag(1, "version", sb, version);
            }
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
        tag(1, "name", sb, name == null
                           ? artifactId
                           : name);
        tag(1, "description", sb,
                description == null
                ? "This is " + groupId + ":" + artifactId
                : description
        );
        if (!modules.isEmpty())
        {
            sb.append('\n');
            inTag(1, "modules", sb,
                    () ->
            {
                modules.forEach(m -> tag(2, "module", sb, m));
            });
        }

        if (!properties.isEmpty())
        {
            sb.append('\n');
            inTag(1, "properties", sb,
                    () ->
            {
                properties.forEach((k, v) ->
                {
                    tag(2, k, sb, v);
                });
            });
        }

        if (!dependencies.isEmpty())
        {
            sb.append('\n');
            inTag(1, "dependencies", sb,
                    () ->
            {
                dependencies.forEach(d ->
                {
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
        boolean hasBuildSections = !dependencyMgmt.isEmpty() || !dependencyMgmtTestDeps
                .isEmpty() || !imports.isEmpty() || !dependencyMgmtProvidedDeps
                .isEmpty();
        if (hasBuildSections)
        {
            if (!dependencyMgmt.isEmpty())
            {
                sb.append('\n');
                inTag(1, "dependencyManagement", sb,
                        () ->
                {
                    inTag(2, "dependencies", sb,
                            () ->
                    {
                        imports.forEach(d ->
                        {
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

                        dependencyMgmtProvidedDeps.forEach(d ->
                        {
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
                                if (d.groupId().is(groupId) && d.version().is(
                                        version))
                                {
                                    tag(4, "version", sb, "${project.version}");
                                }
                                else
                                {
                                    tag(4, "version", sb, d.version());
                                }
                                tag(4, "scope", sb, "provided");
                            });
                        });

                        dependencyMgmt.forEach(d ->
                        {
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
                        dependencyMgmtTestDeps.forEach(d ->
                        {
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
                                tag(4, "scope", sb, "test");
                            });
                        });
                    });
                });
            }
        }

        if (!adhocNodes.isEmpty())
        {
            for (AdhocNode nd : adhocNodes)
            {
                sb.append('\n');
                nd.render(1, sb);;
            }
        }

        sb.append('\n')
                .append(GENERIC_APL);

        if (url != null)
        {
            sb.append('\n');
            tag(1, "url", sb, url);
        }

        if (inceptionYear != null)
        {
            sb.append('\n');
            tag(1, "inceptionYear", sb, inceptionYear);
        }

        if (scmUrl != null)
        {
            scmUrl.remote("origin").ifPresent(rem ->
            {
                sb.append('\n');
                String u = rem.fetchUrl.substring(0, rem.fetchUrl.length() - 4);
                inTag(1, "scm", sb, () ->
                {
                    tag(2, "connection", sb, "scm:git:" + rem.fetchUrl);
                    tag(2, "developerConnection", sb, "scm:git:" + rem.pushUrl);
                    if (rem.fetchUrl.endsWith(".git"))
                    {
                        tag(2, "url", sb, u);
                    }
                });
                if (rem.fetchUrl.contains("github"))
                {
                    sb.append('\n');
                    inTag(1, "issueManagement", sb, () ->
                    {
                        tag(2, "system", sb, "GitHub");
                        tag(2, "url", sb, u + "/issues");
                    });
                }
            });
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

    private final List<AdhocNode> adhocNodes = new ArrayList<>();

    /**
     * Add a node with some adhoc name and or text to this pom. If a callback is
     * passed, it can either set the text content of the node by passing a null
     * consumer, or pass a consumer to create subnodes.
     *
     * @param name A node name
     * @param c A consumer to customize the node or add children to it. If null,
     * a self-closing tag with the passed name is added to the pom.
     * @return this
     */
    public PomGenerator adhocNode(String name, Consumer<ChildNode> c)
    {
        AdhocNode nd = new AdhocNode(name);
        c.accept(nd);
        adhocNodes.add(nd);

        return this;
    }

    /**
     * Abstraction for a child node, which can either have child nodes or text.
     */
    public interface ChildNode
    {
        /**
         * Either set the text of the node, or provide a child tag name if the
         * passed consumer is not null, in which case the child tag can be
         * populated in the consumer.
         *
         * @param textOrTagName Either the child tag name, or the text content
         * for this node.
         * @param childConsumer A consumer or null
         */
        void children(String textOrTagName, Consumer<ChildNode> childConsumer);
    }

    static class AdhocNode implements ChildNode
    {
        String text;
        List<AdhocNode> kids = new ArrayList<>();
        final String tagName;

        public AdhocNode(String tagName)
        {
            this.tagName = tagName;
        }

        @Override
        public void children(String textOrTagName, Consumer<ChildNode> r)
        {
            if (r == null)
            {
                text = textOrTagName;
            }
            else
            {
                AdhocNode child = new AdhocNode(textOrTagName);
                r.accept(child);
                kids.add(child);
            }
        }

        void render(int depth, StringBuilder sb)
        {
            if (!kids.isEmpty())
            {
                inTag(depth, tagName, sb, () ->
                {
                    for (AdhocNode nd : kids)
                    {
                        nd.render(depth + 1, sb);
                    }
                });
            }
            else if (text != null)
            {
                tag(depth, tagName, sb, text);
            }
            else
            {
                tag(depth, tagName, sb);
            }
        }
    }

}
