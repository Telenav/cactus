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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.apache.maven.plugins.annotations.Parameter;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Indicates that the default value for this field should be looked up in a
 * preferences file. This allows us to have properties on mojos which do not
 * have a hard-coded name (such as the default development branch being
 * "develop", but instead look them up in a file where the user can customize
 * them).
 * <p>
 * Use in place of <code>defaultValue="something"</code> for properties that
 * refer to naming conventions that may differ across projects, such as default
 * branch names. The properties files involved are any file named
 * <code>.cactus.properties</code> in the directory of the pom file being
 * executed, or any of its parents up to the git checkout or git submodule root.
 * </p>
 * <h3>Preferences lookup process</h3>
 * <ul>
 * <li>Any <code>.cactus.properties</code> file in the project (pom file)
 * directory</li>
 * <li>Any <code>.cactus.properties</code> file in parents of the pom directory,
 * searching upwards, stopping when a directory which is not part of a git
 * checkout is encountered, or at the filesystem root</li>
 * </ul>
 * <li>Any properties file pointed to by the environment variable
 * <code>CACTUS_SETTINGS</code></li>
 * <li>Any properties file pointed to by the system property
 * <code>cactus.settings</code></li>
 * <li>Any properties file in
 * <code>$APPLICATION_PROPERTIES_DIR/cactus/cactus.properties</code>, where
 * <code>APPLICATION_PROPERTIES_DIR</code> is the OS-specific directory for user
 * application preferences:
 * <ul>
 * <li>On Linux, it will be
 * <code>$HOME/.config/cactus/cactus.properties</code></li>
 * <li>On Mac OS-X, it will be
 * <code>$HOME/Library/Application\ Support/cactus/cactus.properties</code></li>
 * </ul>
 * </li>
 * </ul>
 *
 *
 * Cactus preferences are simple properties files, which are be layered over
 * each other hierarchically, so the first one providing a non-null, non-empty
 * value wins.
 *
 * <h3>Property lookup process</h3>
 * <p>
 * The preferences key is either the key specified in this annotation, or if
 * left blank, the raw <i>field name</i> from the mojo class.
 * </p>
 * <p>
 * Several permutations of the property name are tried, to allow properties to
 * be generally specified or specified specifically for one mojo or mojo
 * supertype, and are tried in order from most- to least-sepecific:
 * </p>
 * <ul>
 * <li>$MOJO-CLASS-SIMPLE_NAME.$PREFERENCE_NAME - using the key from this
 * annotation or the field name if absent</li>
 * <li>$MOJO-CLASS-SIMPLE_NAME.$PROPERTY_NAME - using the property name from the
 * &#064;Parameter annotation with any <code>cactus.</code> prefix stripped</li>
 * <li>$MOJO-SUPERCLASS-SIMPLE_NAME**.$PREFERENCE_NAME (all supertypes up to
 * BaseMojo)</li>
 * <li>$MOJO-SUPERCLASS-SIMPLE_NAME**.$PROPERTY_NAME (all supertypes up to
 * BaseMojo)</li>
 * <li>$PREFERENCE_NAME - just the property name (will apply to any mojo using
 * the same key)</li>
 * <li>$PROPERTY_NAME - just the property name (will apply to any mojo using the
 * same property)</li>
 * </ul>
 *
 *
 * @author Tim Boudreau
 */
@Target(FIELD)
@Retention(RUNTIME)
public @interface CactusDefaultKey
{
    /**
     * The key name to use to look up a default value for a property in cactus
     * preferences.
     *
     * @return The key - if default, the name of the field on the target class
     * will be used
     */
    String value() default "";

    /**
     * A fallback default value for the property if unset - if the empty string
     * (the default), the property will be left alone.
     *
     * @return A fallback value
     */
    String fallback() default "";

    /**
     * Mark the property as required - since the required property in
     * {@link Parameter} must be false to allow this annotation to supply
     * defaults, this can act as a substitute to abort the build if not value
     * can be found for a field.
     *
     * @return
     */
    boolean required() default false;
}
