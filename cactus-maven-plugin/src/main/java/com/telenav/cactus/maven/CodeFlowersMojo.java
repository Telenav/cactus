package com.telenav.cactus.maven;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.sourceanalysis.CodeflowersJsonGenerator;
import com.telenav.cactus.maven.sourceanalysis.MavenProjectsScanner;
import com.telenav.cactus.maven.sourceanalysis.WordCount;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Generates CodeFlowers JSON and .wc files to the assets directory for each
 * project family in scope.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = InstantiationStrategy.SINGLETON,
        name = "codeflowers", threadSafe = true)
public class CodeFlowersMojo extends ScopedCheckoutsMojo {

    /**
     * If true, generate JSON files indented for human-readability; if false,
     * omit all inter-element whitespace.
     */
    @Parameter(property = "indent", defaultValue = "false")
    private boolean indent = false;

    @Override
    protected void execute(BuildLog log, MavenProject project, GitCheckout myCheckout, ProjectTree tree, List<GitCheckout> checkouts) throws Exception {
        Map<ProjectFamily, Set<Pom>> all = allPoms(tree, checkouts);
        for (Map.Entry<ProjectFamily, Set<Pom>> e : all.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            String version = checkConsistentVersion(e.getKey(), e.getValue());
            if (version == null) { // empty = should not happen
                log.warn("Got no versions at all in " + e.getKey());
                continue;
            }
            ProjectFamily fam = e.getKey();
            fam.assetsPath(myCheckout).ifPresentOrElse(assetsRoot -> {
                Path codeflowersPath = assetsRoot.resolve("docs").resolve(version).resolve("codeflowers")
                        .resolve("site").resolve("data");
                log.info("Will generate codeflowers for '" + fam + "' into " + codeflowersPath);
                MavenProjectsScanner scanner = new MavenProjectsScanner(log::error, new WordCount(), e.getValue());
                CodeflowersJsonGenerator gen = new CodeflowersJsonGenerator(fam.toString(), codeflowersPath, indent, isPretend());
                scanner.scan(gen);
            }, () -> {
                log.warn("Could not find an assets root for family " + fam);
            });
        }
    }

    private String checkConsistentVersion(ProjectFamily fam, Set<Pom> poms) throws Exception {
        Set<String> versions = new HashSet<>();
        poms.forEach(pom -> versions.add(pom.coords.version));
        if (versions.size() > 1) {
            throw new MojoExecutionException("Not all projects in family '" + fam + "' have the same version: " + versions);
        }
        return versions.isEmpty() ? null : versions.iterator().next();
    }

    private Map<ProjectFamily, Set<Pom>> allPoms(ProjectTree tree, Collection<? extends GitCheckout> checkouts) {
        Map<ProjectFamily, Set<Pom>> result = new HashMap<>();
        for (GitCheckout co : checkouts) {
            for (Pom pom : tree.projectsWithin(co)) {
                if (!"pom".equals(pom.packaging)) {
                    Set<Pom> poms = result.computeIfAbsent(ProjectFamily.fromGroupId(pom.coords.groupId), f -> new HashSet<>());
                    poms.add(pom);
                }
            }
        }
        return result;
    }
}
