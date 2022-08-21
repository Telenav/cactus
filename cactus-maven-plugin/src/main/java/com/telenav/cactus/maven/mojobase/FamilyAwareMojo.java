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
package com.telenav.cactus.maven.mojobase;

import com.telenav.cactus.maven.trigger.RunPolicy;
import com.telenav.cactus.scope.ProjectFamily;
import java.util.Set;
import org.apache.maven.plugins.annotations.Parameter;

import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.FAMILIES;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.FAMILY;
import static java.util.Collections.singleton;

/**
 * BaseMojo which adds properties family and families.
 *
 * @author Tim Boudreau
 */
public abstract class FamilyAwareMojo extends SharedProjectTreeMojo implements
        FamilyAware
{

    /**
     * Override the project family, using this value instead of one derived from
     * the target project's group id. Only relevant for scopes concerned with
     * families.
     * <p>
     * If both cactus.families and cactus.family are present, cactus.family is
     * ignored.
     * </p>
     */
    @Parameter(property = FAMILY)
    public String family;

    /**
     * Override the project family, using a comma-delimted list of values
     * instead of one derived from the project's group id. Only relevant for
     * scopes concerned with families.
     * <p>
     * If both cactus.families and cactus.family are present, cactus.family is
     * ignored.
     * </p>
     */
    @Parameter(property = FAMILIES)
    public String families;

    protected FamilyAwareMojo(RunPolicy policy)
    {
        super(policy);
    }

    protected FamilyAwareMojo()
    {
    }

    protected FamilyAwareMojo(boolean oncePerSession)
    {
        super(oncePerSession);
    }

    protected final boolean hasExplicitFamilies()
    {
        return (families != null && !families.isBlank())
                || (family != null && !family.isBlank());
    }

    @Override
    public Set<ProjectFamily> families()
    {
        if (families != null && !families.isBlank())
        {
            return ProjectFamily.fromCommaDelimited(families,
                    () -> (family == null || family.isBlank())
                          ? null
                          : ProjectFamily.named(family));
        }
        else
            if (family != null && !family.isBlank())
            {
                return singleton(ProjectFamily.named(family.trim()));
            }
        return singleton(ProjectFamily.fromGroupId(project().getGroupId()));
    }
}
