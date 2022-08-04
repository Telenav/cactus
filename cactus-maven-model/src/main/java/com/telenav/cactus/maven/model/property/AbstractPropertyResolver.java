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
package com.telenav.cactus.maven.model.property;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/**
 * Base class for property resolvers that perform a transform (say, looking up a
 * property value in a map), with common logic for traversing all property
 * elements in a string.
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

        ComboPropertyResolver(AbstractPropertyResolver a,
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
            }
            else
                if (inProperty && c == '}')
                {
                    inProperty = false;
                    v.visit(curr.toString(), true);
                    curr.setLength(0);
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
}
