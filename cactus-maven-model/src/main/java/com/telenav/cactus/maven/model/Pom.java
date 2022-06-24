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
import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.util.preconditions.Exceptions;
import com.telenav.cactus.maven.model.dependencies.DependencyScope;
import com.telenav.cactus.maven.model.internal.PomFile;
import com.telenav.cactus.maven.model.property.MapPropertyResolver;
import com.telenav.cactus.maven.model.property.PropertyResolver;
import com.telenav.cactus.maven.model.resolver.PomResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import org.xml.sax.SAXException;

/**
 * @author Tim Boudreau
 */
public class Pom implements Comparable<Pom>, MavenIdentified, MavenVersioned
{
    public static ThrowingOptional<Pom> in(Path pomFileOrDir)
    {
        if (Files.isDirectory(pomFileOrDir))
        {
            return from(pomFileOrDir.resolve("pom.xml"));
        }
        else
        {
            return from(pomFileOrDir);
        }
    }

    public static Optional<Pom> fromOpt(Path pomFile)
    {
        return from(pomFile).toOptional();
    }

    public static ThrowingOptional<Pom> from(Path pomFile)
    {
        if (!Files.exists(pomFile) || Files.isDirectory(pomFile) || !Files
                .isReadable(pomFile))
        {
            return ThrowingOptional.empty();
        }
        PomFile pom = new PomFile(pomFile);
        try
        {
            MavenCoordinates coord = pom.coordinates();
            String pkg = pom.packaging();
            Set<String> modules;
            if ("pom".equals(pkg))
            {
                modules = pom.modules();
            }
            else
            {
                modules = Collections.emptySet();
            }
            Pom res = new Pom(pomFile, coord, pkg, modules);
            // PomFile does some threadlocal caching of the document, so
            // it is useful to keep it around
            PomFile.note(res, pom);
            return ThrowingOptional.of(res);
        }
        catch (ParserConfigurationException | SAXException | IOException
                | XPathExpressionException ex)
        {
            Logger.getLogger(Pom.class.getName()).log(Level.SEVERE, null, ex);
        }
        return ThrowingOptional.empty();
    }

    public final Set<String> modules;

    public final String packaging;

    public final Path pom;

    public final MavenCoordinates coords;

    public Pom(Path pom, MavenCoordinates coords, String packaging,
            Set<String> modules)
    {
        this.pom = pom;
        this.coords = coords;
        this.packaging = packaging;
        this.modules = modules == null || modules.isEmpty()
                       ? Collections.emptySet()
                       : Collections.unmodifiableSet(modules);
    }

    PomFile toPomFile()
    {
        return PomFile.of(this);
    }

    public PropertyResolver localPropertyResolver()
    {
        return new MapPropertyResolver(properties());
    }

    @Override
    public PomVersion rawVersion()
    {
        return coords.rawVersion();
    }

    public Map<String, String> properties()
    {
        try
        {
            Map<String, String> result = new LinkedHashMap<>();
            toPomFile().visitProperties((key, val) ->
            {
                result.put(key, val);
            });
            return result;
        }
        catch (Exception | Error e)
        {
            return Exceptions.chuck(e);
        }
    }

    public ThrowingOptional<ParentMavenCoordinates> parent()
    {
        try
        {
            return toPomFile().parentCoordinates();
        }
        catch (Exception ex)
        {
            return com.mastfrog.util.preconditions.Exceptions.chuck(ex);
        }
    }

    public ThrowingOptional<Pom> resolveParent(PomResolver resolver)
    {
        return parent().flatMapThrowing(par -> resolver.get(par.groupId(), par
                .artifactId(), par.rawVersion()));
    }

    public Dependency toDependency(String type, DependencyScope scope,
            boolean optional)
    {
        return new Dependency(coords, type, scope, optional, Collections
                .emptySet());
    }

    public List<Pom> hierarchyDescending(PomResolver res)
    {
        List<Pom> result = parents(res);
        result.add(0, this);
        return result;
    }

    public List<Pom> parents(PomResolver res)
    {
        List<Pom> result = new ArrayList<>();
        visitParents(res, result::add);
        return result;
    }

    public int visitParents(PomResolver resolver, ThrowingConsumer<Pom> coords)
    {
        int result = 0;
        ThrowingOptional<ParentMavenCoordinates> opt = parent();
        while (opt.isPresent())
        {
            ParentMavenCoordinates pmc = opt.get();

            ThrowingOptional<Pom> resolved = resolver.get(pmc.groupId(),
                    pmc.artifactId(), pmc.rawVersion());
            if (resolved.isPresent())
            {
                try
                {
                    coords.accept(resolved.get());
                    opt = resolved.get().parent();
                }
                catch (Exception ex)
                {
                    return Exceptions.chuck(ex);
                }
            }
            else
            {
                break;
            }
            result++;
        }
        return result;
    }

    @Override
    public GroupId groupId()
    {
        return coords.groupId;
    }

    @Override
    public ArtifactId artifactId()
    {
        return coords.artifactId;
    }

    @Override
    public ThrowingOptional<String> version()
    {
        return coords.version();
    }

    @Override
    public int compareTo(Pom o)
    {
        return coords.compareTo(o.coords);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }
        final Pom other = (Pom) obj;
        if (!Objects.equals(this.pom, other.pom))
        {
            return false;
        }
        return Objects.equals(this.coords, other.coords);
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.pom);
        hash = 67 * hash + Objects.hashCode(this.coords);
        return hash;
    }

    public Path projectFolder()
    {
        return pom.getParent();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(coords);
        if ("pom".equals(packaging) && !modules.isEmpty())
        {
            sb.append('(');
            for (Iterator<String> it = modules.iterator(); it.hasNext();)
            {
                sb.append(it.next());
                if (it.hasNext())
                {
                    sb.append(',');
                }
            }
            sb.append(')');
        }
        return sb.toString();
    }

    private Boolean hasExplicitVersion;

    public boolean hasExplicitVersion()
    {
        if (hasExplicitVersion != null)
        {
            return hasExplicitVersion;
        }
        try
        {
            return hasExplicitVersion = toPomFile().nodeQuery("/project/version")
                    .isPresent();
        }
        catch (XPathExpressionException | ParserConfigurationException
                | SAXException | IOException ex)
        {
            return Exceptions.chuck(ex);
        }
    }
}
