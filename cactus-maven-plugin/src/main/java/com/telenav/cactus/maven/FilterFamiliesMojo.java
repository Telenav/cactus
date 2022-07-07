package com.telenav.cactus.maven;

import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.FamilyAwareMojo;
import com.telenav.cactus.scope.ProjectFamily;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.scope.ProjectFamily.fromGroupId;
import static java.util.stream.Collectors.toCollection;

/**
 * Certain targets, when run from a root pom, will result in building or
 * generating code for every project in the tree, regardless of whether they are
 * in the set of families relevant to the build. This mojo simply looks for the
 * project family list set in cactus.family or cactus.families, and takes a list
 * of "skip" properties that it will set to true for those properties <b>not</b>
 * a member of that family - so we avoid, say, generating lexakai documentation
 * during a release for a project we are not releasing.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.INITIALIZE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = InstantiationStrategy.PER_LOOKUP,
        name = "filter-families", threadSafe = true)
@BaseMojoGoal("filter-families")
public class FilterFamiliesMojo extends FamilyAwareMojo
{

    @Parameter(property = "cactus.properties")
    private String properties;

    @Parameter(property = "cactus.filter.skip.superpoms", defaultValue = "true")
    private boolean skipSuperpoms;

    @Parameter(property = "cactus.families.required", defaultValue = "false")
    private boolean familiesRequired;

    protected void validateParameters(BuildLog log, MavenProject project) throws Exception
    {
        if (familiesRequired && !hasExplicitFamilies())
        {
            fail("cactus.families.required is set. -Dcactus.families or "
                    + "-Dcactus.family must be set");
        }
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        if (properties == null || properties.isBlank() || !hasExplicitFamilies())
        {
            return;
        }
        Set<ProjectFamily> families = families();
        if (families.isEmpty())
        {
            return;
        }
        List<MavenProject> projectsToSetPropertiesFor = new HashSet<>(session()
                .getAllProjects())
                .stream().filter(x ->
                {
                    if (skipSuperpoms)
                    {
                        if ("pom".equals(x.getPackaging()) && x.getModules()
                                .isEmpty())
                        {
                            return false;
                        }
                    }
                    ProjectFamily fam
                            = fromGroupId(x.getGroupId());
                    return !families.contains(fam);
                }).collect(toCollection(ArrayList::new));
        for (String prop : properties.split(","))
        {
            prop = prop.trim();
            if (!prop.isEmpty())
            {
                for (MavenProject prj : projectsToSetPropertiesFor)
                {
                    if (isVerbose())
                    {
                        log.info("Inject " + prop + "=true into " + prj
                                .getArtifactId() + " for " + families());
                    }
                    prj.getProperties().setProperty(prop, "true");
                }
            }
        }
    }
}
