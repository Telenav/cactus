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

import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.FamilyAwareMojo;
import com.telenav.cactus.scope.ProjectFamily;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.scope.ProjectFamily.fromGroupId;
import static java.lang.Boolean.parseBoolean;
import static java.util.stream.Collectors.toCollection;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.PER_LOOKUP;
import static org.apache.maven.plugins.annotations.LifecyclePhase.INITIALIZE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

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
        defaultPhase = INITIALIZE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = PER_LOOKUP,
        name = "filter-families", threadSafe = true)
@BaseMojoGoal("filter-families")
public class FilterFamiliesMojo extends FamilyAwareMojo
{

    @Parameter(property = "cactus.properties", required = true)
    private String properties;

    @Parameter(property = "cactus.filter.skip.superpoms", defaultValue = "true")
    private boolean skipSuperpoms;

    @Parameter(property = "cactus.families.required", defaultValue = "false")
    private boolean familiesRequired;

    @Override
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
        propertiesToApply().forEach(prop ->
        {
            for (MavenProject prj : projectsToSetPropertiesFor)
            {
                prj.getProperties().setProperty(prop, "true");
                boolean changed = logicalCombineProperties(prop, true,
                        prj.getProperties(), false);
                if (changed && isVerbose())
                {
                    log.info("Inject " + prop + "=true into " + prj
                            .getArtifactId() + " for " + families());
                }
            }
        });
    }

    private Set<String> propertiesToApply()
    {
        Set<String> result = new TreeSet<>();
        for (String prop : properties.split("[, ]"))
        {
            prop = prop.trim();
            if (!prop.isEmpty())
            {
                result.add(prop);
            }
        }
        return result;
    }

    /**
     * Combine a boolean value to set with the value stored as a string in a
     * project's properties, if any, and combine logically.
     *
     * @param prop The property
     * @param defaultValue The value to set it to if unset
     * @param props project properties
     * @param or whether to or or and
     * @return true if the properties were changed as a result of this operation
     */
    @SuppressWarnings("SameParameterValue")
    private static boolean logicalCombineProperties(String prop, boolean defaultValue, Properties props, boolean or)
    {
        String val = props.getProperty(prop);
        if (val == null)
        {
            props.put(prop, Boolean.toString(defaultValue));
            return true;
        }
        boolean eval = parseBoolean(val);
        boolean newValue = or
                           ? (eval || defaultValue)
                           : (eval && defaultValue);
        props.put(prop, Boolean.toString(newValue));
        return newValue != eval;
    }
}
