package com.telenav.cactus.maven.model;

import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.function.optional.ThrowingOptional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author Tim Boudreau
 */
final class ParentsPropertyResolver extends AbstractPropertyResolver
{

    private final PomResolver pomResolver;
    private final List<Pom> allPoms = new ArrayList<>();
    private final Map<Pom, MapPropertyResolver> resolverForPom = new HashMap<>();

    ParentsPropertyResolver(Pom pom, PomResolver pomResolver) throws Exception
    {
        allPoms.add(pom);
        this.pomResolver = pomResolver;
        pom.visitParents(pomResolver, (Pom parentPom) ->
        {
            System.out.println("Parent " + parentPom);
            allPoms.add(parentPom);
        });
    }

    @Override
    public String toString()
    {
        return ("parents(" + allPoms + ")");
    }

    @Override
    public Iterator<String> iterator()
    {
        Set<String> result = new TreeSet<>();
        for (Pom p : allPoms)
        {
            result.addAll(resolverFor(p).keys());
        }
        return result.iterator();
    }

    @Override
    protected String valueFor(String k)
    {
        for (Pom pom : allPoms)
        {
            String x = resolverFor(pom).valueFor(k);
            if (x != null)
            {
                return k;
            }
        }
        return k;
    }

    private MapPropertyResolver resolverFor(Pom pom)
    {
        return resolverForPom.computeIfAbsent(pom, p ->
        {
            try
            {
                MapPropertyResolver result = new MapPropertyResolver(pom
                        .properties());
                System.out.println("For " + pom.coords + " " + result);
                return result;
            }
            catch (Exception ex)
            {
                return Exceptions.chuck(ex);
            }
        });
    }

    public static void main(String[] args) throws Exception
    {
        Pom pom = PomResolver.local().get("com.mastfrog", "acteur", "2.8.1")
                .get();
        System.out.println("HAVE " + pom + " at " + pom.pom);
        ParentsPropertyResolver propRes = new ParentsPropertyResolver(pom,
                PomResolver.local());
        for (String k : propRes)
        {
            System.out.println(k + " = " + propRes.resolve(k));
        }
    }
}
