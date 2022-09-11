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

import org.w3c.dom.Node;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * The &lt;packaging&gt; element of a Maven <code>pom.xml</code> file.
 *
 * @author Tim Boudreau
 */
public final class Packaging implements Comparable<Packaging>
{
    private final String packaging;
    public static final Packaging JAR = new Packaging("jar");
    public static final Packaging POM = new Packaging("pom");

    private Packaging(String packaging)
    {
        this.packaging = notNull("packaging", packaging).trim();
    }

    public static Packaging packaging(Node node)
    {
        if (node == null || node.getTextContent() == null || node
                .getTextContent().isBlank())
        {
            return JAR;
        }
        return packaging(node.getTextContent());
    }

    public static Packaging packaging(String what)
    {
        if (what == null || what.isBlank())
        {
            return JAR;
        }
        switch (what)
        {
            case "jar":
                return JAR;
            case "pom":
                return POM;
            default:
                return new Packaging(what);
        }
    }
    
    public boolean is(String type) {
        return packaging.equals(type);
    }

    public boolean isPom()
    {
        return POM.equals(this);
    }
    
    public String kind() {
        return packaging;
    }

    @Override
    public String toString()
    {
        return kind();
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        else
            if (o == null || o.getClass() != Packaging.class)
            {
                return false;
            }
        Packaging other = (Packaging) o;
        return other.packaging.equals(packaging);
    }

    @Override
    public int hashCode()
    {
        switch (packaging)
        {
            case "jar":
                return 1;
            case "pom":
                return Integer.MAX_VALUE / 2;
            case "maven-plugin":
                return Integer.MIN_VALUE / 8;
            default:
                return 51 * packaging.hashCode();
        }
    }

    @Override
    public int compareTo(Packaging o)
    {
        return packaging.compareTo(o.packaging);
    }
}
