package com.telenav.cactus.maven.util;

import com.mastfrog.function.throwing.ThrowingSupplier;
import com.telenav.cactus.maven.model.internal.PomFile;
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
    protected final PomFile in;

    public AbstractXMLUpdater(PomFile in)
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
        return in.path;
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
        return in.path.compareTo(o.in.path);
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
