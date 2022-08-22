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

import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.common.CommonPreferences;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.trigger.RunPolicy;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.CREATE_AUTOMERGE_TAG;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.STABLE_BRANCH;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AbstractStableBranchMojo extends ScopedCheckoutsMojo
{
    /**
     * The name of the stable branch that would be merged to, in order to weed
     * out checkouts which are already on that branch.
     */
    @Parameter(property = STABLE_BRANCH, required = false)
    private String stableBranch;

    @Parameter(property = CREATE_AUTOMERGE_TAG, defaultValue = "false")
    protected boolean createAutomergeTag;

    protected AbstractStableBranchMojo()
    {
    }

    protected AbstractStableBranchMojo(boolean runFirst)
    {
        super(runFirst);
    }

    protected AbstractStableBranchMojo(RunPolicy policy)
    {
        super(policy);
    }

    protected String stableBranch(GitCheckout checkout)
    {
        return property(checkout.checkoutRoot(), stableBranch,
                CommonPreferences.STABLE_BRANCH);
    }

    @Override
    protected void onValidateParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        validateBranchName(stableBranch, true);
    }

}
