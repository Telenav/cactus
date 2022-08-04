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
package com.telenav.cactus.maven.trigger;

import com.telenav.cactus.maven.mojobase.BaseMojo;
import org.apache.maven.project.MavenProject;

public class NegatedRunPolicy implements RunPolicy {

    // Public because of classloader issues with maven instantiating
    // modularized mojos
    
    private final RunPolicy delegate;

    public NegatedRunPolicy(RunPolicy delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public boolean shouldRun(BaseMojo mojo, MavenProject prj) {
        return !delegate.shouldRun(mojo, prj);
    }

    @Override
    public RunPolicy negate() {
        return delegate;
    }

    @Override
    public String toString() {
        return "!" + delegate;
    }
}
