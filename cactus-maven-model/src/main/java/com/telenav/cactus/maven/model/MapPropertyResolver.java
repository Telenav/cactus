package com.telenav.cactus.maven.model;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
final class MapPropertyResolver extends AbstractPropertyResolver
{

    private final Map<String, String> map;

    public MapPropertyResolver(Map<String, String> map)
    {
        this.map = map;
    }

    @Override
    protected String valueFor(String k)
    {
        System.out.println("tryget " + k + " with " + map.get(k));
        return map.get(k);
    }

    @Override
    public Iterator<String> iterator()
    {
        return map.keySet().iterator();
    }
    
    Set<String> keys() {
        return map.keySet();
    }
}
