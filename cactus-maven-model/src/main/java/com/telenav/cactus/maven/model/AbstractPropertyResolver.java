package com.telenav.cactus.maven.model;

import com.telenav.cactus.maven.model.internal.PomFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractPropertyResolver implements PropertyResolver
{

    protected abstract String valueFor(String k);

    @Override
    public PropertyResolver or(PropertyResolver other)
    {
        if (other instanceof AbstractPropertyResolver)
        {
            return new ComboPropertyResolver(this,
                    (AbstractPropertyResolver) other);
        }
        return PropertyResolver.super.or(other);
    }

    static final class ComboPropertyResolver extends AbstractPropertyResolver
    {
        private final AbstractPropertyResolver a;
        private final AbstractPropertyResolver b;

        public ComboPropertyResolver(AbstractPropertyResolver a,
                AbstractPropertyResolver b)
        {
            this.a = a;
            this.b = b;
        }

        @Override
        public String toString()
        {
            return "or(" + a + ", " + b + ")";
        }

        @Override
        protected String valueFor(String k)
        {
            String result = a.valueFor(k);
            if (result == null)
            {
                result = b.valueFor(k);
            }
            else
                if (!PropertyResolver.isResolved(result))
                {
                    result = a.valueFor(result);
                }
            return result;
        }

        @Override
        public Iterator<String> iterator()
        {
            Set<String> result = new TreeSet<>();
            a.forEach(result::add);
            b.forEach(result::add);
            return result.iterator();
        }

    }

    @Override
    public String resolve(String what)
    {
//        String val = valueFor(what);
//        if (val == null)
//        {
//            val = what;
//        }
        String val = what;
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
                    String res = valueFor(part);
                    if (res != null)
                    {
                        if (res.length() > 3 && res.equals(part))
                        {
                            throw new IllegalStateException(
                                    "In " + what + " resolved '" + part + "' to itself by " + this);
                        }
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
        if (what.equals(val))
        {
            return null;
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
            if (c == '$' && i < what.length() - 1 && what.charAt(i + 1) == '{')
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
    
    public static void main(String[] args) throws Exception
    {
        Path p = Paths.get("/Users/timb/work/telenav/jonstuff/cactus/pom.xml");
        PomFile pf = new PomFile(p);
        
        pf.visitProperties((k, v) -> {
            System.out.println(k + " = " + v);
        });
        
        Poms poms = Poms.in(Paths.get("/Users/timb/work/telenav/jonstuff/"));
        Pom pom = poms.get("com.telenav.cactus", "cactus-maven-model").get();
        PomResolver pomRes = poms.or(LocalRepoResolver.INSTANCE);
        ParentsPropertyResolver pp = new ParentsPropertyResolver(pom, pomRes);
        CoordinatesPropertyResolver coords = new CoordinatesPropertyResolver(pom);
        
        Pom parent = poms.get("com.telenav.cactus", "cactus").get();
        
        MapPropertyResolver mpr = pp.resolverFor(parent);
        System.out.println("MPR " + mpr.resolve("${mastfrog.version}"));
        
        PropertyResolver propRes = pp.or(coords);
        
        System.out.println("MF " + propRes.resolve("${mastfrog.version}"));
    }
}
