package com.telenav.cactus.maven.model;

import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Tim Boudreau
 */
public class Pom implements Comparable<Pom>
{
    public static Optional<Pom> from(Path pomFile)
    {
        if (!Files.exists(pomFile))
        {
            return Optional.empty();
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
            return Optional.of(new Pom(pomFile, coord, pkg, modules));
        }
        catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException ex)
        {
            Logger.getLogger(Pom.class.getName()).log(Level.SEVERE, null, ex);
        }
        return Optional.empty();
    }

    public final Set<String> modules;

    public final String packaging;

    public final Path pom;

    public final MavenCoordinates coords;

    public Pom(Path pom, MavenCoordinates coords, String packaging, Set<String> modules)
    {
        this.pom = pom;
        this.coords = coords;
        this.packaging = packaging;
        this.modules = Collections.unmodifiableSet(modules);
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
            for (Iterator<String> it = modules.iterator(); it.hasNext(); )
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
}
