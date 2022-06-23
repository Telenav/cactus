package com.telenav.cactus.graph;

import com.mastfrog.graph.ObjectGraph;
import com.telenav.cactus.maven.model.ArtifactId;
import com.telenav.cactus.maven.model.ArtifactIdentifiers;
import com.telenav.cactus.maven.model.Dependency;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.MavenCoordinates;
import com.telenav.cactus.maven.model.MavenIdentified;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.dependencies.DependencyScope;
import com.telenav.cactus.maven.model.resolver.Poms;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

/**
 * Allows for building a dependency graph.
 *
 * @author Tim Boudreau
 */
public class DependencyGraphBuilder
{
    final Set<DependencyScope> scopes = EnumSet.noneOf(DependencyScope.class);
    BiPredicate<Pom, Dependency> preFilter;
    Predicate<MavenCoordinates> postFilter;
    boolean includeOptionalDependencies = false;

    private DependencyGraphBuilder()
    {

    }

    public static DependencyGraphBuilder dependencyGraphBuilder()
    {
        return new DependencyGraphBuilder();
    }

    public DependencyGraphBuilder withPreFilter(
            BiPredicate<Pom, Dependency> preFilter)
    {
        this.preFilter = preFilter;
        return this;
    }

    public DependencyGraphBuilder withPostFilter(
            BiPredicate<Pom, Dependency> preFilter)
    {
        this.preFilter = preFilter;
        return this;
    }

    public DependencyGraphBuilder includeOptionalDependencies()
    {
        includeOptionalDependencies = true;
        return this;
    }

    FinishableDependencyGraphBuilder finishableBuilder()
    {
        return new FinishableDependencyGraphBuilder(this);
    }

    public FinishableDependencyGraphBuilder scanningFolder(Path folder)
    {
        return finishableBuilder().scanningFolder(folder);
    }

    public static final class FinishableDependencyGraphBuilder extends DependencyGraphBuilder
    {
        private final Set<Path> foldersToScan = new LinkedHashSet<>(8);
        private final Set<ArtifactIdentifiers> from = new LinkedHashSet<>(8);
        private GraphSpec graphing = new AllJavaProjects(true);

        FinishableDependencyGraphBuilder(DependencyGraphBuilder orig)
        {
            this.scopes.addAll(orig.scopes);
            this.preFilter = orig.preFilter;
            this.postFilter = orig.postFilter;
            this.includeOptionalDependencies = orig.includeOptionalDependencies;
        }

        @Override
        public FinishableDependencyGraphBuilder includeOptionalDependencies()
        {
            return (FinishableDependencyGraphBuilder) super
                    .includeOptionalDependencies();
        }

        @Override
        public FinishableDependencyGraphBuilder withPostFilter(
                BiPredicate<Pom, Dependency> preFilter)
        {
            return (FinishableDependencyGraphBuilder) super.withPostFilter(
                    preFilter);
        }

        @Override
        public FinishableDependencyGraphBuilder withPreFilter(
                BiPredicate<Pom, Dependency> preFilter)
        {
            return (FinishableDependencyGraphBuilder) super.withPreFilter(
                    preFilter);
        }

        @Override
        FinishableDependencyGraphBuilder finishableBuilder()
        {
            return this;
        }

        public FinishableDependencyGraphBuilder scanningFolder(Path folder)
        {
            foldersToScan.add(notNull("folder", folder));
            return this;
        }

        public FinishableDependencyGraphBuilder graphing(String groupId,
                String artifactId)
        {
            graphing = graphing.and(new GraphOf(new ArtifactIdentifiers(GroupId
                    .of(groupId), ArtifactId.of(artifactId))));
            return this;
        }

        public FinishableDependencyGraphBuilder graphing(MavenIdentified mi)
        {
            graphing = graphing.and(new GraphOf(notNull("mi", mi)
                    .toArtifactIdentifiers()));
            return this;
        }

        public FinishableDependencyGraphBuilder graphingAllJavaProjects() throws IOException
        {
            graphing = graphing.and(new AllJavaProjects(false));
            return this;
        }

        public FinishableDependencyGraphBuilder graphingAllJavaAndPomProjects()
                throws IOException
        {
            graphing = graphing.and(new AllProjects());
            return this;
        }

        public D3DataGenerator d3Graph() throws IOException
        {
            return new D3DataGenerator(build());
        }

        Set<DependencyScope> scopes()
        {
            if (scopes.isEmpty())
            {
                return DependencyScope.Compile.asSet();
            }
            return scopes;
        }

        public ObjectGraph<MavenCoordinates> build() throws IOException
        {
            Poms poms = Poms.in(foldersToScan);
            Set<Pom> toGraph = graphing.apply(poms);
            Set<DependencyScope> scopes = scopes();
            DependencyGraphs graphs = new DependencyGraphs(poms.poms());
            return graphs.dependencyGraph(scopes, includeOptionalDependencies,
                    preFilter, postFilter, toGraph);
        }
    }

    private interface GraphSpec extends Function<Poms, Set<Pom>>
    {

        default GraphSpec and(GraphSpec other)
        {
            return poms ->
            {
                Set<Pom> set = new LinkedHashSet<>();
                set.addAll(GraphSpec.this.apply(poms));
                set.addAll(other.apply(poms));
                return set;
            };
        }
    }

    private static class GraphOf implements GraphSpec
    {
        private final ArtifactIdentifiers id;

        public GraphOf(ArtifactIdentifiers id)
        {
            this.id = id;
        }

        @Override
        public Set<Pom> apply(Poms poms)
        {
            return poms.get(id.groupId(), id.artifactId()).map(pom ->
            {
                return singleton(pom);
            }).orElse(emptySet());
        }
    }

    private static class AllProjects implements GraphSpec
    {
        @Override
        public Set<Pom> apply(Poms t)
        {
            return new LinkedHashSet<>(t.poms());
        }
    }

    private static class AllJavaProjects implements GraphSpec
    {
        private final boolean isDefault;

        AllJavaProjects(boolean isDefault)
        {
            this.isDefault = isDefault;
        }

        @Override
        public Set<Pom> apply(Poms t)
        {
            return new LinkedHashSet<>(t.javaProjects());
        }

        @Override
        public GraphSpec and(GraphSpec other)
        {
            if (isDefault)
            {
                return other;
            }
            return GraphSpec.super.and(other);
        }
    }

    public static void main(String[] args) throws Exception
    {
        ObjectGraph<MavenCoordinates> graph = dependencyGraphBuilder()
                .scanningFolder(Paths.get(
                        "/Users/timb/work/personal/mastfrog-parent"))
                .scanningFolder(Paths.get(
                        "/Users/timb/work/personal/meta-update-center"))
                .includeOptionalDependencies()
//                .withPreFilter((pom, dep) ->
//                {
//                    return dep.groupId().text().contains("com.mastfrog");
//                })
                .graphing(GroupId.of("com.mastfrog").artifact("meta-update-server"))
//                .graphing("com.mastfrog", "util-preconditions")
                
                .d3Graph().generate(Paths.get("/tmp/wug/foo.json"));
        System.out.println(graph);
    }
}