package com.telenav.cactus.maven.versions;

import com.mastfrog.function.throwing.ThrowingSupplier;
import com.telenav.cactus.maven.model.internal.PomFile;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Objects;
import org.w3c.dom.Document;

/**
 *
 * @author timb
 */
public final class TextContentReplacer implements
        Comparable<TextContentReplacer>
{
    private final PomFile in;
    private final String xslQuery;
    private final String newValue;

    public TextContentReplacer(PomFile in, String xslQuery, String newValue)
    {
        this.in = in;
        this.xslQuery = xslQuery;
        this.newValue = newValue;
    }

    public Path path()
    {
        return in.path;
    }

    @Override
    public String toString()
    {
        return xslQuery + " -> " + newValue + " in " + in.path;
    }

    static <T> T openAll(Iterable<TextContentReplacer> iter,
            ThrowingSupplier<T> supp) throws Exception
    {
        Iterator<TextContentReplacer> it = iter.iterator();
        if (!it.hasNext())
        {
            return supp.get();
        }
        else
        {
            return it.next().run(it, supp);
        }
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
    public <T> T run(Iterator<TextContentReplacer> iter,
            ThrowingSupplier<T> supp)
            throws Exception
    {
        if (!iter.hasNext())
        {
            return in.inContext(supp);
        }
        else
        {
            return in.inContext(() -> iter.next().run(iter, supp)
            );
        }
    }

    public Document replace() throws Exception
    {
        return in.inContext(doc ->
        {
            return in.nodeQuery(xslQuery).map(node ->
            {
                boolean result = !Objects.equals(newValue, node.getTextContent()
                        .trim());
                if (result)
                {
                    node.setTextContent(newValue);
                }
                return doc;
            }).orElse(null);
        });
    }

    @Override
    public int compareTo(TextContentReplacer o)
    {
        return in.path.compareTo(o.in.path);
    }
}
