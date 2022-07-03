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

import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;

/**
 * @author Tim Boudreau
 */
public class Pom implements Comparable<Pom>, MavenArtifactCoordinates, DiskResident
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

    private final Set<String> modules;

    private final String packaging;

    private final Path pom;

    private final MavenCoordinates coords;

    public Pom(Path pom, MavenCoordinates coords, String packaging,
            Set<String> modules)
    {
        this.pom = pom;
        this.coords = coords;
        this.packaging = packaging;
        this.modules = modules == null || modules.isEmpty()
                       ? emptySet()
                       : unmodifiableSet(modules);
    }

    public Path path()
    {
        return pom;
    }

    public Set<String> modules()
    {
        return modules;
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
    public PomVersion version()
    {
        return coordinates().version();
    }

    public String packaging()
    {
        return packaging;
    }

    public boolean isPomProject()
    {
        return "pom".equals(packaging());
    }

    private Map<String, String> props;

    public Map<String, String> properties()
    {
        if (props != null)
        {
            return props;
        }
        try
        {
            Map<String, String> result = new LinkedHashMap<>();
            toPomFile().visitProperties((key, val) ->
            {
                result.put(key, val);
            });
            return props = unmodifiableMap(result);
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
                .artifactId(), par.version()));
    }

    public Dependency toDependency(String type, DependencyScope scope,
            boolean optional)
    {
        return new Dependency(coordinates(), type, scope, optional, Collections
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
                    pmc.artifactId(), pmc.version());
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
        return coordinates().groupId();
    }

    @Override
    public ArtifactId artifactId()
    {
        return coordinates().artifactId;
    }

    @Override
    public ThrowingOptional<String> resolvedVersion()
    {
        return coordinates().resolvedVersion();
    }

    @Override
    public int compareTo(Pom o)
    {
        return coordinates().compareTo(o.coordinates());
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null || Pom.class != obj.getClass())
        {
            return false;
        }
        return pom.equals(((Pom) obj).pom);
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.pom);
        hash = 67 * hash + Objects.hashCode(this.coordinates());
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
        sb.append(coordinates());
        if (isPomProject() && !modules.isEmpty())
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

    private String[] nameDescription;

    private String[] nameAndDescription()
    {
        if (nameDescription != null)
        {
            return nameDescription;
        }
        PomFile pf = PomFile.of(this);
        try
        {
            String[] result = new String[]
            {
                artifactId().text(), "-none-"
            };
            return nameDescription = pf.inContext(doc ->
            {
                pf.nodeQuery("/project/name").ifPresent(nd ->
                {
                    String txt = nd.getTextContent();
                    if (txt != null && !txt.isBlank())
                    {
                        result[0] = txt.trim();
                    }
                });
                pf.nodeQuery("/project/description").ifPresent(nd ->
                {
                    String txt = nd.getTextContent();
                    if (txt != null && !txt.isBlank())
                    {
                        result[1] = txt.trim();
                    }
                });
                return result;
            });
        }
        catch (Exception ex)
        {
            return Exceptions.chuck(ex);
        }
    }

    public String name()
    {
        return nameAndDescription()[0] == null
               ? artifactId().text()
               : nameAndDescription()[0];
    }

    public String description()
    {
        return nameAndDescription()[1] == null
               ? "-none-"
               : nameAndDescription()[1];
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
            return hasExplicitVersion = toPomFile()
                    .nodeQuery("/project/version")
                    .isPresent();
        }
        catch (XPathExpressionException | ParserConfigurationException
                | SAXException | IOException ex)
        {
            return Exceptions.chuck(ex);
        }
    }

    /**
     * @return the coords
     */
    public MavenCoordinates coordinates()
    {
        return coords;
    }

}
