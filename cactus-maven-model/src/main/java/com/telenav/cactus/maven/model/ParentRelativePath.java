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
package com.telenav.cactus.maven.model;

import com.mastfrog.function.optional.ThrowingOptional;
import java.nio.file.Path;
import org.w3c.dom.Node;

/**
 * Relative path to a parent pom - may be unspecified, which gets the value
 * DEFAULT, or may actively have empty content, which gets the value NONE -
 * otherwise it <i>is</i> a relative path from one pom to another.
 *
 * @author Tim Boudreau
 */
public final class ParentRelativePath extends ResolvablePomElement<ParentRelativePath>
{
    public static final ParentRelativePath NONE = new ParentRelativePath("");
    public static final ParentRelativePath DEFAULT = new ParentRelativePath(
            "../pom.xml");

    ParentRelativePath(String value)
    {
        super(value);
    }

    ParentRelativePath(Node node)
    {
        super(node);
    }

    /**
     * Given a project directory, resolve the path described by this
     * ParentRelativePath.
     *
     * @param projectDir A directory
     * @return A path
     */
    public ThrowingOptional<Path> resolve(Path projectDir)
    {
        if (isNone())
        {
            return ThrowingOptional.empty();
        }
        return ThrowingOptional.of(projectDir.resolve(text()).normalize());
    }

    /**
     * Determine if this is the default instance (nothing at all is specified in
     * the pom, so the default of ../pom.xml is used).
     *
     * @return Whether or not this is the default value
     */
    public boolean isDefault()
    {
        return this == DEFAULT;
    }

    public static ParentRelativePath of(Node node)
    {
        if (node == null)
        {
            return DEFAULT;
        }
        if (node.getTextContent() == null || node.getTextContent().isBlank())
        {
            return NONE;
        }
        return new ParentRelativePath(node);
    }

    public boolean isNone()
    {
        return "".equals(text());
    }

    @Override
    protected ParentRelativePath newInstance(String what)
    {
        return new ParentRelativePath(what);
    }

}
