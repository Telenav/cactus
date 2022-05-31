package com.telenav.cactus.maven;

import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ConsistencyChecker;
import com.telenav.cactus.maven.tree.Inconsistency;
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "check-consistency", threadSafe = true)
public class CheckConsistencyMojo extends BaseMojo
{

    @Parameter(property = "ignoreInBranchConsistencyCheck", defaultValue = "-assets")
    private String ignoreInBranchConsistencyCheck = "";

    @Parameter(property = "ignoreInVersionConsistencyCheck", defaultValue = "")
    private String ignoreInVersionConsistencyCheck = "";

    @Parameter(property = "allGroupIds", defaultValue = "false")
    private boolean allGroupIds = false;

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        ConsistencyChecker checker = new ConsistencyChecker(ignoreInBranchConsistencyCheck,
                ignoreInVersionConsistencyCheck, allGroupIds ? null : project.getGroupId(), true);

        Set<Inconsistency<?>> inconsistencies = checker.checkConsistency(project, log);
        if (!inconsistencies.isEmpty())
        {
            StringBuilder sb = new StringBuilder();
            for (Inconsistency<?> issue : inconsistencies)
            {
                if (sb.length() > 0)
                {
                    sb.append('\n');
                }
                sb.append(issue);
            }
            throw new MojoExecutionException(this, sb.toString(), sb.toString());
        }
    }

}
