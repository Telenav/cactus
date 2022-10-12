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
import org.w3c.dom.Document;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.telenav.cactus.maven.xml.XMLReplacer.writeXML;
import static java.util.Collections.sort;

/**
 * A thing which can make a single change in an XML file.
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

    protected AbstractXMLUpdater(XMLFile in)
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
        return in.path().toString().compareTo(o.in.path().toString());
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

    /**
     * Apply a collection of xml updaters to the files they are concerned with.
     *
     * @param replacers
     * @param pretend
     * @param msgs
     * @return
     * @throws Exception
     */
    public static Set<Path> applyAll(
            Collection<? extends AbstractXMLUpdater> changes,
            boolean pretend, Consumer<String> msgs) throws Exception
    {
        List<AbstractXMLUpdater> replacers = new ArrayList<>(changes);
        // Sort them so we work on one file at a time
        sort(replacers);
        // Preload Document instances for all of the poms, so each document
        // change operates against any earlier changes
        return AbstractXMLUpdater.openAll(replacers, () ->
        {
            Set<Path> result = new HashSet<>();
            Map<Path, Document> docForPath = new LinkedHashMap<>();
            // Group by file
            for (AbstractXMLUpdater rep : replacers)
            {
                Document changed = rep.replace();
                if (changed != null)
                {
                    Document old = docForPath.get(rep.path());
                    // Do a sanity check in case openAll() has been broken - 
                    // XMLFile should hold the same document instance the entire
                    // time we're in here, so each edit is applied against the
                    // previous one and we don't save and reload over and over
                    if (old != changed && old != null)
                    {
                        throw new AssertionError(
                                "Context did not hold - " + old + " vs "
                                + changed + " for " + rep.path());
                    }
                    msgs.accept(" Apply: " + rep);
                    docForPath.put(rep.path(), changed);
                }
            }
            String mode = (pretend
                           ? "(pretend) "
                           : "");
            // Apply 
            for (Map.Entry<Path, Document> e : docForPath.entrySet())
            {
                if (!pretend)
                {
                    writeXML(e.getValue(), e.getKey());
                }
                result.add(e.getKey());
                msgs.accept(mode + "Rewrote " + e.getKey());
            }
            return result;
        });
    }
}
