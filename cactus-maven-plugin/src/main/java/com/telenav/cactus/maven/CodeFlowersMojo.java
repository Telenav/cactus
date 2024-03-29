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

import com.telenav.cactus.analysis.MavenProjectsScanner;
import com.telenav.cactus.analysis.WordCount;
import com.telenav.cactus.analysis.codeflowers.CodeflowersJsonGenerator;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.scope.ProjectFamily;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.maven.trigger.RunPolicies.FAMILY_ROOTS;
import static com.telenav.cactus.scope.ProjectFamily.fromCommaDelimited;
import static com.telenav.cactus.scope.ProjectFamily.fromGroupId;
import static java.util.Collections.emptySet;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PREPARE_PACKAGE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * Generates CodeFlowers JSON and .wc files to the assets directory for each
 * project family in scope.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = PREPARE_PACKAGE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = InstantiationStrategy.SINGLETON,
        name = "codeflowers", threadSafe = true)
@BaseMojoGoal("codeflowers")
public class CodeFlowersMojo extends ScopedCheckoutsMojo
{
    /**
     * If true, generate JSON files indented for human-readability; if false,
     * omit all inter-element whitespace.
     */
    @Parameter(property = "cactus.indent", defaultValue = "false")
    private boolean indent;

    @Parameter(property = "cactus.tolerate.version.inconsistencies.families"
    )
    private String tolerateVersionInconsistenciesIn;

    @Parameter(property = "cactus.codeflowers.skip")
    private boolean skipped;

    public CodeFlowersMojo()
    {
        super(FAMILY_ROOTS);
    }

    private Set<ProjectFamily> tolerateVersionInconsistenciesIn()
    {
        return tolerateVersionInconsistenciesIn == null
               ? emptySet()
               : fromCommaDelimited(
                        tolerateVersionInconsistenciesIn, () -> null);
    }

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout, ProjectTree tree,
            List<GitCheckout> checkouts) throws Exception
    {
        if (skipped)
        {
            log.info("Codeflowers is skipped");
            return;
        }
        ifVerbose(() -> {
            log.info("Codeflowers on " + project.getArtifactId() + " with " + scope() + " and " + families());
        });
        Map<ProjectFamily, Set<Pom>> all = allPoms(tree, checkouts);
        for (Map.Entry<ProjectFamily, Set<Pom>> e : all.entrySet())
        {
            if (e.getValue().isEmpty())
            {
                continue;
            }
            String version = checkConsistentVersion(e.getKey(), e.getValue(),
                    tree);
            if (version == null)
            { // empty = should not happen
                log.warn("Got no versions at all in " + e.getKey());
                continue;
            }
            ProjectFamily fam = e.getKey();
            fam.assetsPath(myCheckout.submoduleRoot()
                    .map(GitCheckout::checkoutRoot)).ifPresentOrElse(
                    assetsRoot ->
            {
                Path codeflowersPath = assetsRoot.resolve("docs").resolve(
                        version).resolve("codeflowers")
                        .resolve("site").resolve("data");
                log.info(
                        "Will generate codeflowers for '" + fam + "' into " + codeflowersPath);
                
                MavenProjectsScanner scanner = new MavenProjectsScanner(log
                        .child("scanProjects"), new WordCount(), e.getValue(), isVerbose());
                CodeflowersJsonGenerator gen = new CodeflowersJsonGenerator(fam
                        .toString(), codeflowersPath, indent, isPretend());
                scanner.scan(gen);
            }, () ->
            {
                log.warn("Could not find an assets root for family " + fam);
            });
        }
    }

    private Map<ProjectFamily, Set<Pom>> allPoms(ProjectTree tree,
            Collection<? extends GitCheckout> checkouts)
    {
        Map<ProjectFamily, Set<Pom>> result = new HashMap<>();
        for (GitCheckout co : checkouts)
        {
            for (Pom pom : tree.projectsWithin(co))
            {
                if (!pom.isPomProject())
                {
                    Set<Pom> poms = result.computeIfAbsent(fromGroupId(pom.coordinates().groupId().text()),
                            f -> new HashSet<>());
                    poms.add(pom);
                }
            }
        }
        return result;
    }

    private String checkConsistentVersion(ProjectFamily fam, Set<Pom> poms,
            ProjectTree tree)
            throws Exception
    {
        Set<String> versions = new HashSet<>();
        poms.forEach(pom -> versions.add(pom.coordinates().version.text()));
        if (versions.size() > 1)
        {
            if (tolerateVersionInconsistenciesIn().contains(fam))
            {
                fam.probableFamilyVersion(poms).ifPresent(ver ->
                {
                    versions.clear();
                    versions.add(ver.text());
                });
            }
            else
            {
                throw new MojoExecutionException(
                        "Not all projects in family '" + fam + "' have the same version: " + versions);
            }
        }
        return versions.isEmpty()
               ? null
               : versions.iterator().next();
    }
}
