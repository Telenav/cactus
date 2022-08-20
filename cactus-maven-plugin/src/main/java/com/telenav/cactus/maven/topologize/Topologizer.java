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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
    private ObjectGraph<MavenCoordinates> graph;
    private ObjectGraph<MavenCoordinates> childGraph;
    private ObjectGraph<MavenCoordinates> mergedGraph;
    private ObjectGraph<MavenCoordinates> ownershipGraph;

    public Topologizer(GitCheckout someCheckout, BuildLog log) throws IOException
    {
        root = someCheckout.submoduleRoot().orElse(someCheckout);
        poms = Poms.in(someCheckout.checkoutRoot());
        this.log = log.child("topology");
    }

    public static void main(String[] args) throws IOException
    {
        GitCheckout checkout = GitCheckout.checkout(Paths.get(
                "/Users/timb/work/telenav/jonstuff")).get();
        BuildLog bl = BuildLog.get();
        Topologizer topo = new Topologizer(checkout, bl);
        for (Pom p : topo.poms)
        {
            if (p.isPomProject() && !p.modules().isEmpty())
            {
                List<String> sorted = topo.topologicallySortedModules(p);
                System.out.println("\n-------\nSORTED MODULES OF " + p);
                for (String s : sorted)
                {
                    System.out.println("  * " + s);
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
                System.err.println(
                        "Not in graph " + mc + " in graph of " + coords);
                continue;
            }

            modules.add(mc);
        }
        List<MavenCoordinates> before = new ArrayList<>(modules);
        List<MavenCoordinates> sorted = cg.topologicalSort(modules);
//        List<MavenCoordinates> sorted = topoSort(modules, cg);
        if (before.equals(sorted))
        {
            System.out.println("TOP SORT DID NOTHING: " + before);
        }
        List<String> result = new ArrayList<>();
        for (MavenCoordinates mc : sorted)
        {
            Pom p = poms.get(mc).get();
            result.add(p.projectFolder().getFileName().toString());
        }
        Collections.reverse(result);
        return result;
    }

    private static <T> List<T> topoSort(Collection<? extends T> list,
            ObjectGraph<T> gr)
    {
        List<T> result = new ArrayList<>(list);
        result.sort(new TopoComparator<>(gr));
        return result;
    }

    static final class TopoComparator<T> implements Comparator<T>
    {
        private final ObjectGraph<T> gr;

        public TopoComparator(ObjectGraph<T> gr)
        {
            this.gr = gr;
        }

        @Override
        public int compare(T a, T b)
        {
            Set<T> aClosure = gr.closureOf(a);
            Set<T> bClosure = gr.closureOf(b);
            boolean bInA = aClosure.contains(b);
            boolean aInB = bClosure.contains(a);
            if (bInA == aInB)
            {
                return 0;
            }
            else
                if (bInA)
                {
                    return 1;
                }
                else
                {
                    return -1;
                }
        }

    }

    private ObjectGraph<MavenCoordinates> mergedGraph() throws IOException
    {
        if (mergedGraph != null)
        {
            return mergedGraph;
        }
        Set<MavenCoordinates> allNodes = new HashSet<>();
        ObjectGraph<MavenCoordinates> ownershipGraph = ownershipGraph();
        ObjectGraph<MavenCoordinates> childGraph = childGraph();
        ObjectGraph<MavenCoordinates> graph = graph();

        System.out.println("CHILD GRAPH:");
        System.out.println(childGraph);
        System.out.println("");

        Set<MavenCoordinates> childGraphNodes = new HashSet<>();
        for (int i = 0; i < childGraph.size(); i++)
        {
            MavenCoordinates coords = childGraph.toNode(i);
            childGraphNodes.add(coords);
            allNodes.add(coords);
        }

        System.out.println("\n\nDEPENDENCY GRAPH:\n" + graph);
        Set<MavenCoordinates> graphNodes = new HashSet<>();
        for (int i = 0; i < graph.size(); i++)
        {
            MavenCoordinates coords = graph.toNode(i);
            graphNodes.add(coords);
            allNodes.add(coords);
        }

        System.out.println("\n\nOWNERSHIP GRAPH:\n" + ownershipGraph);
        System.out.println("");

        Set<MavenCoordinates> ownerNodes = new HashSet<>();
        for (int i = 0; i < ownershipGraph.size(); i++)
        {
            MavenCoordinates coords = ownershipGraph.toNode(i);
            ownerNodes.add(coords);
            allNodes.add(coords);
        }

        List<MavenCoordinates> coords = new ArrayList<>(allNodes);
        Collections.shuffle(coords);

        IntGraphBuilder igb = IntGraph.builder(coords.size());

        for (int i = 0; i < coords.size(); i++)
        {
            int index = i;
            MavenCoordinates c = coords.get(i);

            if (graphNodes.contains(c))
            {
                for (MavenCoordinates child : graph.children(c))
                {
                    int cix = coords.indexOf(child);
                    igb.addEdge(i, cix);
                }
            }
            if (childGraphNodes.contains(c))
            {
                for (MavenCoordinates child : childGraph.children(c))
                {
                    int cix = coords.indexOf(child);
                    igb.addEdge(i, cix);
                }
            }
            if (ownerNodes.contains(c))
            {
                for (MavenCoordinates child : ownershipGraph.children(c))
                {
                    int cix = coords.indexOf(child);
                    igb.addEdge(i, cix);
                }
            }
        }
        IntGraph mergedIntGraph = igb.build();
        mergedGraph = mergedIntGraph.toObjectGraph(coords);
        System.out.println("\n\nMERGED GRAPH\n" + mergedGraph + "\n\n");
        return mergedGraph;
    }

    private DependencyGraphs graphs;

    private DependencyGraphs graphs()
    {
        return graphs == null
               ? graphs = new DependencyGraphs(poms)
               : graphs;
    }

    public ObjectGraph<MavenCoordinates> childGraph() throws IOException
    {
        if (childGraph != null)
        {
            return childGraph;
        }
        return childGraph = log.benchmark(
                "Project Module Graph Creation", () ->
        {
            return graphs().parentage();
        });
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
