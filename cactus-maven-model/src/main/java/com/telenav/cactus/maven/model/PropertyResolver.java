package com.telenav.cactus.maven.model;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author Tim Boudreau
 */
public interface PropertyResolver extends Iterable<String>
{

    static boolean isResolved(String what)
    {
        if (what == null)
        {
            return true;
        }
        int hix = what.indexOf("${");
        int tix = hix < 0
                  ? -1
                  : what.lastIndexOf("}", hix);
        return hix > 0 && tix > hix;
    }
    
    static PropertyResolver coords(MavenCoordinates self, MavenCoordinates parent) {
        return new CoordinatesPropertyResolver(self, parent);
    }
    
    default PropertyResolver or(PropertyResolver other) {
        return new PropertyResolver() {
            @Override
            public String resolve(String what)
            {
                String result = other.resolve(what);
                if (result != null) {
                    what = result;
                }
                if (!isResolved(what)) {
                    String ores = other.resolve(what);
                    if (ores != null) {
                        return ores;
                    }
                }
                return result == null ? what : result;
            }

            @Override
            public Iterator<String> iterator()
            {
                Set<String> all = new TreeSet<>();
                for (String k : PropertyResolver.this) {
                    all.add(k);
                }
                for (String k : other) {
                    all.add(k);
                }
                return all.iterator();
            }
            
            @Override
            public String toString() {
                return "or(" + PropertyResolver.this + ", " + other + ")";
            }
            
        };
    }

    String resolve(String what);
    
    default PropertyResolver memoizing() {
        return new PropertyResolver() {
            private final Map<String, Optional<String>> cache = new HashMap<>();
            @Override
            public String resolve(String what)
            {
                return cache.computeIfAbsent(what, w -> Optional.ofNullable(PropertyResolver.this.resolve(
                        what))).orElse(null);
            }

            @Override
            public Iterator<String> iterator()
            {
                return PropertyResolver.this.iterator();
            }
            
            public String toString() {
                return "memo(" + PropertyResolver.this + ")";
            }

            @Override
            public PropertyResolver memoizing()
            {
                return this;
            }
        };
    }
}
