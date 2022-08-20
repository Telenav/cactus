package com.telenav.cactus.maven;

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
import com.telenav.cactus.maven.task.TaskSet;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.maven.trigger.RunPolicies;
import com.telenav.cactus.maven.xml.AbstractXMLUpdater;
import com.telenav.cactus.maven.xml.XMLReplacer;
import com.telenav.cactus.maven.xml.XMLTextContentReplacement;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * A project tree with multiple families may develop a variety of divergent
 * version properties for the same thing. This mojo will simply find all such
 * properties (for property names that indicate a version of a project or family
 * in the tree), and sets them to the greatest value found.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "homogenize-versions", threadSafe = true)
@BaseMojoGoal("homogenize-versions")
public class HomogenizeVersionsMojo extends SharedProjectTreeMojo
{
    public HomogenizeVersionsMojo()
    {
        super(RunPolicies.FIRST);
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
        Set<XMLTextContentReplacement> replacers = new LinkedHashSet<>();
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
        else
        {
            AbstractXMLUpdater.applyAll(replacers, isPretend(),
                    System.out::println);
        }
    }
}
