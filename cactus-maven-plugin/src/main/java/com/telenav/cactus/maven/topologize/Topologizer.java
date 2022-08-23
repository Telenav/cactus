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
package com.telenav.cactus.maven.topologize;

import com.mastfrog.function.optional.ThrowingOptional;
import com.mastfrog.graph.IntGraph;
import com.mastfrog.graph.IntGraphBuilder;
import com.mastfrog.graph.ObjectGraph;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.graph.DependencyGraphBuilder;
import com.telenav.cactus.graph.DependencyGraphs;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.MavenArtifactCoordinates;
import com.telenav.cactus.maven.model.MavenCoordinates;
import com.telenav.cactus.maven.model.MavenModule;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.resolver.Poms;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;

/**
 *
 * @author Tim Boudreau
 */
public class Topologizer
{

    final Poms poms;
    private final GitCheckout root;
    private final BuildLog log;
    private DependencyGraphs graphs;
    private ObjectGraph<MavenCoordinates> graph;
    private ObjectGraph<MavenCoordinates> mergedGraph;
    private ObjectGraph<MavenCoordinates> ownershipGraph;

    public Topologizer(GitCheckout someCheckout, BuildLog log) throws IOException
    {
        root = someCheckout.submoduleRoot().orElse(someCheckout);
        poms = Poms.in(someCheckout.checkoutRoot());
        this.log = log.child("topology");
    }
    
    public Poms poms() {
        return poms;
    }

    public static void main(String[] args) throws IOException
    {
        GitCheckout checkout = GitCheckout.checkout(Paths.get(
                "/Users/timb/work/telenav/jonstuff")).get();
        BuildLog bl = BuildLog.get();
        Topologizer topo = new Topologizer(checkout, bl);
        for (Pom p : topo.poms)
        {
            if (p.isAggregator())
            {
                List<String> sorted = topo.topologicallySortedModules(p);
                System.out.println("\n-------\nSORTED MODULES OF " + p.toArtifactIdentifiers());
                for (String s : sorted)
                {
                    System.out.println("    <module>" + s + "</module>");
                }
            }
        }
    }

    public <C extends MavenArtifactCoordinates> List<String> topologicallySortedModules(
            C coords) throws IOException
    {

        ThrowingOptional<Pom> pomOpts = poms.get(coords.groupId(), coords
                .artifactId());
        if (!pomOpts.isPresent())
        {
            return emptyList();
        }
        Pom pom = pomOpts.get();
        Set<MavenCoordinates> modules = new HashSet<>();

        ObjectGraph<MavenCoordinates> cg = mergedGraph();

        Set<MavenCoordinates> inGraph = new HashSet<>();

        for (int i = 0; i < cg.size(); i++)
        {
            inGraph.add(cg.toNode(i));
        }

        for (MavenModule mod : pom.modules())
        {
            Pom childPom = mod.toPom().get();
            MavenCoordinates mc = childPom.coordinates();
            if (!inGraph.contains(mc))
            {
                continue;
            }
            modules.add(mc);
        }
        List<MavenCoordinates> sorted = cg.topologicalSort(modules);
        List<String> result = new ArrayList<>();
        for (MavenCoordinates mc : sorted)
        {
            Pom p = poms.get(mc).get();
            result.add(p.projectFolder().getFileName().toString());
        }
        Collections.reverse(result);
        return result;
    }
    
    private ObjectGraph<MavenCoordinates> mergedGraph() throws IOException
    {
        if (mergedGraph != null)
        {
            return mergedGraph;
        }
        return log.benchmark("Merged dependency graph creation", () -> {
            return mergedGraph = _mergedGraph();
        });
    }

    private ObjectGraph<MavenCoordinates> _mergedGraph() throws IOException
    {
        // What we are doing here is taking the dependency graph of java
        // projects, and decorating it with dependencies on parent pom
        // projects and dependencies between <module> entries and their
        // modules, so that, if, say, kivakit-extensions depends on something
        // in kivakit, then kivakit-extensions aggregator pom depends on
        // kivakit, and the it must be built first - so we get a graph
        // that indicates not just Java dependencies but all inter-project
        // dependencies that determine what needs to be built first.
        //
        // In Maven, you're really dealing with THREE graphs:
        //  - Java dependencies
        //  - What builds what aggregator dependencies (<modules> entries)
        //  - Parent pom dependencies - what parents off of what
        //
        // and in this case, we need a graph that captures all of the above
        
        Set<MavenCoordinates> allNodes = new HashSet<>();
        ObjectGraph<MavenCoordinates> modulesGraph = ownershipGraph();
        ObjectGraph<MavenCoordinates> dependencyGraph = graph();

        log.debug(() -> "\n\nDEPENDENCY GRAPH:\n" + dependencyGraph);
        Set<MavenCoordinates> graphNodes = new HashSet<>();
        for (int i = 0; i < dependencyGraph.size(); i++)
        {
            MavenCoordinates coords = dependencyGraph.toNode(i);
            graphNodes.add(coords);
            allNodes.add(coords);
        }

        log.debug(() -> "\n\nOWNERSHIP GRAPH:\n" + modulesGraph);

        for (int i = 0; i < modulesGraph.size(); i++)
        {
            MavenCoordinates coords = modulesGraph.toNode(i);
            allNodes.add(coords);
        }

        List<MavenCoordinates> coords = new ArrayList<>(allNodes);

        IntGraphBuilder igb = IntGraph.builder(coords.size());

        for (int i = 0; i < coords.size(); i++)
        {
            int index = i;
            MavenCoordinates c = coords.get(i);
            ThrowingOptional<Pom> po = poms.get(c);
            if (!po.isPresent())
            {
                continue;
            }
            Pom p = po.get();

            p.parent().ifPresent(par ->
            {
                poms.get(par).ifPresent(pp ->
                {
                    int pix = coords.indexOf(pp.coordinates());
                    igb.addEdge(index, pix);
                });
            });

            p.modules().forEach(module ->
            {
                module.toPom().ifPresent(modulePom ->
                {
                    int moduleIx = coords.indexOf(modulePom.coordinates());
                    igb.addEdge(index, moduleIx);
                });
            });

            if (graphNodes.contains(c))
            {
                for (MavenCoordinates child : dependencyGraph.children(c))
                {
                    int cix = coords.indexOf(child);
                    igb.addEdge(i, cix);

                    modulesGraph.parents(child).forEach(parent ->
                    {
                        if (modulesGraph.topLevelOrOrphanNodes().contains(parent)) {
                            return;
                        }
                        poms.get(parent).ifPresent(ancestorPom ->
                        {
                            int ancestorIx = coords.indexOf(ancestorPom
                                    .coordinates());
                            
                            log.debug(() -> "Synthesize dependency " 
                                    + p.artifactId() + " on " + parent.artifactId());
                            igb.addEdge(index, ancestorIx);
                        });
                    });
                }
            }
        }
        IntGraph mergedIntGraph = igb.build();
        mergedGraph = mergedIntGraph.toObjectGraph(coords);
        log.debug(() -> "\n\nMERGED GRAPH\n" + mergedGraph);
        return mergedGraph;
    }

    private DependencyGraphs graphs()
    {
        return graphs == null
               ? graphs = new DependencyGraphs(poms)
               : graphs;
    }

    public ObjectGraph<MavenCoordinates> ownershipGraph() throws IOException
    {
        if (ownershipGraph != null)
        {
            return ownershipGraph;
        }
        return ownershipGraph = log.benchmark(
                "Project Module Graph Creation", () ->
        {
            return graphs().ownership();
        });
    }

    public ObjectGraph<MavenCoordinates> graph() throws IOException
    {
        if (graph != null)
        {
            return graph;
        }
        Set<GroupId> groupIds = new HashSet<>();
        Set<MavenCoordinates> allCoords = new HashSet<>();
        poms.forEach(pom ->
        {
            groupIds.add(pom.groupId());
            allCoords.add(pom.coordinates());
        });
        return graph = log.benchmark("Dependency Graph Creation", () ->
        {
            return DependencyGraphBuilder.dependencyGraphBuilder()
                    .withPostFilter(
                            coords
                            ->
                    {
                        return groupIds.contains(coords.groupId())
                                && allCoords.contains(coords);
                    })
                    .scanningFolder(root.checkoutRoot())
                    .graphingAllJavaAndPomProjects().build();
        });
    }
}
