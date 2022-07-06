package com.telenav.cactus.maven.mojobase;

import com.telenav.cactus.scope.ProjectFamily;
import java.util.Set;

/**
 * Interface implemented by family-aware mojos.
 *
 * @author Tim Boudreau
 */
public interface FamilyAware
{
    Set<ProjectFamily> families();

}
