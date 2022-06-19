package com.telenav.cactus.maven.model;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractPropertyResolver implements PropertyResolver
{

    protected abstract String valueFor(String k);
    
    static class MapResolver extends AbstractPropertyResolver {
        private final Map<String, String> map;

        public MapResolver(Map<String, String> map)
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
    }

    @Override
    public String resolve(String what)
    {
        String val = valueFor(what);
        System.out.println("Resolve '" + what + "' to " + val);
        if (val == null) {
            val = what;
        }
        if (val != null && !PropertyResolver.isResolved(what))
        {
            StringBuilder result = new StringBuilder();
            visitSegments(val, (part, needsResolution) ->
            {
                if (!needsResolution)
                {
                    result.append(part);
                }
                else
                {
                    String res = resolve(part);
                    if (res != null)
                    {
                        result.append(res);
                    }
                    else
                    {
                        result.append("${").append(part).append("}");
                    }
                }
            });
            return result.toString();
        }
        return val;
    }

    interface ResolvableVisitor
    {
        void visit(String component, boolean needResolve);
    }

    static void visitSegments(String what, ResolvableVisitor v)
    {
        StringBuilder output = new StringBuilder();
        StringBuilder curr = new StringBuilder();
        boolean inProperty = false;
        for (int i = 0; i < what.length(); i++)
        {
            char c = what.charAt(i);
            if (c == '$' && c < what.length() - 1 && what.charAt(i + 1) == '{')
            {
                if (inProperty)
                {
                    v.visit("${" + curr, false);
                    curr.setLength(0);
                }
                else
                    if (output.length() > 0)
                    {
                        v.visit(output.toString(), false);
                        output.setLength(0);
                    }
                i++;
                inProperty = true;
                continue;
            }
            else
                if (inProperty && c == '}')
                {
                    inProperty = false;
                    v.visit(curr.toString(), true);
                    curr.setLength(0);
                    continue;
                }
                else
                {
                    if (inProperty)
                    {
                        curr.append(c);
                    }
                    else
                    {
                        output.append(c);
                    }
                }
        }
        if (curr.length() > 0)
        {
            v.visit("${" + curr, false);
        }
        if (output.length() > 0)
        {
            v.visit(output.toString(), false);
        }
    }

    /*
    public static void main(String[] args)
    {
        String thing = "some-${first}-with-${second}-and-some-${messed${up}stuff";
        visitSegments(thing, (part, needRes) ->
        {
            if (needRes)
            {
                System.out.println("RESOLVE " + part);
            }
            else
            {
                System.out.println("   HAVE " + part);
            }
        });
        System.out.println(thing);
        Map<String, String> m = new HashMap<>();
        m.put("first", "one");
        m.put("second", "two");
        m.put("up", "-down-");
        
        MapResolver mr = new MapResolver(m);
        System.out.println(mr.resolve(thing));
    }
*/
}
