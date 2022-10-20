package com.telenav.cactus.maven;

import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.ArtifactIdentifiers;
import com.telenav.cactus.maven.model.ParentMavenCoordinates;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.internal.PomFile;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.SharedProjectTreeMojo;
import com.telenav.cactus.maven.xml.AbstractXMLUpdater;
import com.telenav.cactus.maven.xml.XMLTextContentReplacement;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.maven.trigger.RunPolicies.FIRST;
import static com.telenav.cactus.maven.xml.AbstractXMLUpdater.applyAll;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * A simpler version of HomogenizeVersionsMojo which simply finds any declared
 * parent in the tree, and if it does not match the actual version of that
 * project in the tree, corrects it.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = VALIDATE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = SINGLETON,
        name = "fix-parent-versions", threadSafe = true)
@BaseMojoGoal("fix-parent-versions")
public class FixParentsMojo extends SharedProjectTreeMojo
{

    public FixParentsMojo()
    {
        super(FIRST);
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        withProjectTree(tree ->
        {
            Map<Pom, ParentMavenCoordinates> parentCoordsForProject = new HashMap<>();
            Map<ArtifactIdentifiers, Pom> pomForArtifactIds = new HashMap<>();

            tree.allProjects().forEach((Pom pom) ->
            {
                pomForArtifactIds.put(pom.toArtifactIdentifiers(), pom);
                pom.parent().ifPresent(pmc ->
                {
                    parentCoordsForProject.put(pom, pmc);
                });
            });

            Map<Pom, PomVersion> parentVersionChanges = new HashMap<>();
            parentCoordsForProject.forEach((pom, parentCoords) ->
            {
                ArtifactIdentifiers aids = parentCoords.toArtifactIdentifiers();
                Pom parentProject = pomForArtifactIds.get(aids);
                if (parentProject != null)
                {
                    PomVersion versionOnDisk = parentProject.version();
                    PomVersion versionReferencedAsParent = parentCoords
                            .version();
                    if (!versionOnDisk.equals(versionReferencedAsParent))
                    {
                        System.out.println(
                                pom.toArtifactIdentifiers() + " parent version is " + versionReferencedAsParent + " should be " + versionOnDisk + " of " + aids);
                        parentVersionChanges.put(pom, versionOnDisk);
                    }
                }
            });

            if (parentVersionChanges.isEmpty())
            {
                log.info("No versions inconsistent.");
            }
            Set<AbstractXMLUpdater> replacers = new LinkedHashSet<>();
            parentVersionChanges.forEach((pom, newParentVersion) ->
            {
                PomFile file = PomFile.of(pom);
                XMLTextContentReplacement replacement = new XMLTextContentReplacement(
                        file,
                        "/project/parent/version", newParentVersion.text());
                replacers.add(replacement);
            });

            applyAll(replacers, isPretend(),
                    this::emitMessage);
        });
    }
}
