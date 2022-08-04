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

import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Tests if any git checkouts within the specified scope are dirty (have local
 * modifications) and logs their state.
 *
 * @author jonathanl (shibo)
 */
@SuppressWarnings(
        {
            "unused", "DuplicatedCode"
        })
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "is-dirty", threadSafe = true)
@BaseMojoGoal("is-dirty")
public class IsDirtyMojo extends ScopedCheckoutsMojo
{
    public IsDirtyMojo()
    {
        super(true);
    }

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout,
            ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {
        var dirty = false;
        for (var checkout : checkouts)
        {
            if (checkout.isDirty())
            {
                if (!dirty)
                {
                    System.out.println("Dirty projects:");
                }
                dirty = true;
                System.out.println("* " + checkout);
            }
        }
        if (!dirty)
        {
            log.info("\nClean\n\n");
        }
    }
}
