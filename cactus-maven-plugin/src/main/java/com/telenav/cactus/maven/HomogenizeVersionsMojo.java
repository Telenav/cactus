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
package com.telenav.cactus.maven;

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.ArtifactIdentifiers;
import com.telenav.cactus.maven.model.ParentMavenCoordinates;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.internal.PomFile;
import com.telenav.cactus.maven.model.resolver.Poms;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.SharedProjectTreeMojo;
import com.telenav.cactus.maven.refactoring.PropertyHomogenizer;
import com.telenav.cactus.maven.topologize.Topologizer;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.maven.xml.AbstractXMLUpdater;
import com.telenav.cactus.maven.xml.XMLFile;
import com.telenav.cactus.maven.xml.XMLTextContentReplacement;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static com.telenav.cactus.maven.trigger.RunPolicies.FIRST;
import static com.telenav.cactus.maven.xml.AbstractXMLUpdater.applyAll;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * A project tree with multiple families may develop a variety of divergent
 * version properties for the same thing. This mojo will simply find all such
 * properties (for property names that indicate a version of a project or family
 * in the tree), and sets them to the greatest value found.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = VALIDATE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = SINGLETON,
        name = "homogenize-versions", threadSafe = true)
@BaseMojoGoal("homogenize-versions")
public class HomogenizeVersionsMojo extends SharedProjectTreeMojo
{
    @Parameter(property = "cactus.topologically-sort-modules",
            defaultValue = "false")
    private boolean topoSortModules;

    public HomogenizeVersionsMojo()
    {
        super(FIRST);
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        withProjectTree(tree ->
        {
            PropertyHomogenizer ph = new PropertyHomogenizer(new Poms(tree
                    .allProjects()));
            if (isPretend())
            {
                ph.pretend();
            }
            Set<Path> updated = ph.go(log::info);
            if (updated.isEmpty())
            {
                log.info("No inconsistent properties found.");
            }
            else
            {
                log.info("Updated " + updated.size() + " pom files.");
            }
            fixParentVersions(tree, log);
        });
    }

    private void fixParentVersions(ProjectTree tree, BuildLog log) throws Exception
    {
        Map<ArtifactIdentifiers, Pom> pomForIds = new HashMap<>();
        Map<Pom, ArtifactIdentifiers> parentForPom = new HashMap<>();
        Map<Pom, ParentMavenCoordinates> fullParentForPom = new HashMap<>();
        tree.allProjects().forEach(pom ->
        {
            pomForIds.put(pom.toArtifactIdentifiers(), pom);
            pom.parent().ifPresent(par ->
            {
                ArtifactIdentifiers parentIds = par.toArtifactIdentifiers();
                parentForPom.put(pom, parentIds);
                fullParentForPom.put(pom, par);
            });
        });

        Map<Pom, PomVersion> newVersionForPom = new HashMap<>();
        Set<AbstractXMLUpdater> replacers = new LinkedHashSet<>();
        parentForPom.forEach((pom, parentId) ->
        {
            Pom parentPom = pomForIds.get(parentId);
            ParentMavenCoordinates parent = fullParentForPom.get(pom);
            if (!parentPom.version().equals(parent.version()))
            {
                log.info("Inconsistent parent version in " + pom.artifactId()
                        + " should be " + parentPom.version() + " but is "
                        + parent.version());
                newVersionForPom.put(pom, parent.version());
                PomFile file = PomFile.of(pom);
                XMLTextContentReplacement replacement = new XMLTextContentReplacement(
                        file,
                        "/project/parent/version", parentPom.version().text());
                replacers.add(replacement);
            }
        });
        if (replacers.isEmpty())
        {
            log.info("No parent version updates needed.");
        }

        if (topoSortModules)
        {
            Topologizer topo = new Topologizer(tree.root(), log);
            for (Pom pom : topo.poms())
            {
                if (pom.isAggregator())
                {
                    List<String> sortedModuleNames = topo
                            .topologicallySortedModules(pom);
                    log.info("Will replace module list in " + pom.artifactId());
                    replacers.add(new ModuleListReplacer(PomFile.of(pom),
                            sortedModuleNames));
                }
            }
        }

        applyAll(replacers, isPretend(),
                this::emitMessage);
    }

    private static final class ModuleListReplacer extends AbstractXMLUpdater
    {
        private final List<String> newModuleList;

        ModuleListReplacer(XMLFile in, List<String> newModuleList)
        {
            super(in);
            this.newModuleList = newModuleList;
        }

        @Override
        public Document replace() throws Exception
        {
            return in.inContext(doc ->
            {
                ThrowingOptional<NodeList> list = in.nodesQuery(
                        "/project/modules/module");
                if (!list.isPresent())
                {
                    return null;
                }
                NodeList nl = list.get();
                for (int i = 0; i < nl.getLength(); i++)
                {
                    String newText = newModuleList.get(i);
                    Node n = nl.item(i);
                    n.setTextContent(newText);
                }
                return doc;
            });
        }

    }
}
