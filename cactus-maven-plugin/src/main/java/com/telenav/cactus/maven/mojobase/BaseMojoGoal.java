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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Maven's Mojo annotation is not runtime-retained, but we need access to the
 * name of the goal. So, for now at least, using a runtime annotation which must
 * be kept in sync with the Mojo annotation to accomplish that - several run
 * policies need to know the goal.
 *
 * @author Tim Boudreau
 */
@Retention(RUNTIME)
@Target(ElementType.TYPE)
public @interface BaseMojoGoal
{
    String value();
}
