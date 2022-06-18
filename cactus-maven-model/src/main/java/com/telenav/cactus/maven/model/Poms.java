package com.telenav.cactus.maven.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
public class Poms
{
    private final List<Pom> sorted;
    private final Map<String, Map<String, Pom>> poms = new HashMap<>();

    public Poms(Collection<? extends Pom> all)
    {
        sorted = new ArrayList<>(all);
        Collections.sort(sorted);
        for (Pom pom : sorted)
        {
            Map<String, Pom> kids
                    = poms.computeIfAbsent(pom.coords.groupId,
                            gid -> new HashMap<>());
            kids.put(pom.coords.artifactId, pom);
        }
    }

    public Optional<Pom> get(String groupId, String artifactId)
    {
        Map<String, Pom> map = poms.get(groupId);
        if (map == null)
        {
            return Optional.empty();
        }
        return Optional.ofNullable(map.get(artifactId));
    }

}
