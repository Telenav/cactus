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

    public Topologizer(GitCheckout someCheckout, BuildLog log) throws IOException
    {
        root = someCheckout.submoduleRoot().orElse(someCheckout);
        poms = Poms.in(someCheckout.checkoutRoot());
        this.log = log.child("topology");
    }

    public static void main(String[] args) throws IOException
    {
        GitCheckout checkout = GitCheckout.checkout(Paths.get(
                "/Users/tim/work/telenav/jonstuff")).get();
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
            C coords)
    {

        ThrowingOptional<Pom> pomOpts = poms.get(coords.groupId(), coords
                .artifactId());
        if (!pomOpts.isPresent())
        {
            return emptyList();
        }
        Pom pom = pomOpts.get();
        Set<MavenCoordinates> modules = new HashSet<>();
        for (MavenModule mod : pom.modules())
        {
            Pom childPom = mod.toPom().get();
            modules.add(childPom.coordinates());
        }
        List<MavenCoordinates> sorted = childGraph.topologicalSort(modules);
        List<String> result = new ArrayList<>();
        for (MavenCoordinates mc : sorted)
        {
            Pom p = poms.get(mc).get();
            result.add(p.path().getFileName().toString());
        }
        return result;
    }

    public ObjectGraph<MavenCoordinates> childGraph() throws IOException
    {
        if (childGraph != null)
        {
            return childGraph;
        }
        Set<Pom> all = new HashSet<>();
        poms.forEach(pom -> all.add(pom));
        return childGraph = log.benchmark("Project Module Graph Creation", () ->
        {
            return new DependencyGraphs(all).parentage();
        });
    }

    public ObjectGraph<MavenCoordinates> graph() throws IOException
    {
        if (graph != null)
        {
            return graph;
        }
        Set<GroupId> groupIds = new HashSet<>();
        poms.forEach(pom -> groupIds.add(pom.groupId()));
        return graph = log.benchmark("Dependency Graph Creation", () ->
        {
            return DependencyGraphBuilder.dependencyGraphBuilder()
                    .withPostFilter(
                            coords -> groupIds.contains(coords.groupId()))
                    .scanningFolder(root.checkoutRoot())
                    .graphingAllJavaAndPomProjects().build();
        });
    }
}
