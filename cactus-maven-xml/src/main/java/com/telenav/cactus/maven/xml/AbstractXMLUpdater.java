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
package com.telenav.cactus.maven.xml;

import com.mastfrog.function.throwing.ThrowingSupplier;
import java.nio.file.Path;
import java.util.Iterator;
import org.w3c.dom.Document;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AbstractXMLUpdater implements
        Comparable<AbstractXMLUpdater>
{
    /**
     * Ensures that the same Document instance is used by all
     * XMLTextContentReplacement instances over the same file within the
     * collection, so they cannot clobber each other, and also so we do not need
     * to write out and reread the document repeatedly to pick up changes from
     * other XMLTextContentReplacements that have already run.
     *
     * @param <T> The return type
     * @param iter An iterable
     * @param supp The work that should run once all of the Document instances
     * that may be modified have been loaded
     * @return The result of the supplier
     * @throws Exception if something goes wrong
     */
    public static <T> T openAll(Iterable<AbstractXMLUpdater> iter,
            ThrowingSupplier<T> supp) throws Exception
    {
        Iterator<AbstractXMLUpdater> it = iter.iterator();
        if (!it.hasNext())
        {
            return supp.get();
        }
        else
        {
            return it.next().run(it, supp);
        }
    }
    protected final XMLFile in;

    public AbstractXMLUpdater(XMLFile in)
    {
        this.in = notNull("in", in);
    }

    /**
     * The file to be modified.
     *
     * @return A path
     */
    public Path path()
    {
        return in.path();
    }

    /**
     * Perform the replacement on the document.
     *
     * @return The document, if the change could be performed and the document
     * was really modified, or null if not.
     * @throws Exception If something goes wrong
     */
    public abstract Document replace() throws Exception;

    /**
     * Implements comparable just so these can be logged in a consistent order.
     *
     * @param o Another instance
     * @return a comparison result
     */
    @Override
    public final int compareTo(AbstractXMLUpdater o)
    {
        return in.path().compareTo(o.in.path());
    }

    /**
     * Recursively ensure a cached document is held for the entire time we were
     * running - there may be multiple changes applied.
     *
     * @param <T>
     * @param iter
     * @param supp
     * @return
     * @throws Exception
     */
    final <T> T run(Iterator<AbstractXMLUpdater> iter,
            ThrowingSupplier<T> supp)
            throws Exception
    {
        if (!iter.hasNext())
        {
            return in.inContext(supp);
        }
        else
        {
            return in.inContext(() -> iter.next().run(iter, supp));
        }
    }
}
