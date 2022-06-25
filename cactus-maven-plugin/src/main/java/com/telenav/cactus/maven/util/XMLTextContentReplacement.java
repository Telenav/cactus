package com.telenav.cactus.maven.util;

import com.mastfrog.function.throwing.ThrowingSupplier;
import com.telenav.cactus.maven.model.internal.PomFile;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Objects;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * Thing that can replace the text content of a single DOM element specified by
 * an XSL query.
 *
 * @author Tim Boudreau
 */
public final class XMLTextContentReplacement extends AbstractXMLUpdater
{
    private final String xslQuery;
    private final String newValue;

    /**
     * Create a new replacement. If the new value is null, the found node will
     * be replaced with one which is self closing.
     *
     * @param in The file
     * @param xslQuery The query
     * @param newValue The new value or null
     */
    public XMLTextContentReplacement(PomFile in, String xslQuery,
            String newValue)
    {
        super(in);
        this.xslQuery = notNull("xslQuery", xslQuery);
        this.newValue = newValue;
    }


    @Override
    public String toString()
    {
        return xslQuery + " -> " + newValue + " in " + in.path;
    }

    /**
     * Perform the replacement on the document.
     *
     * @return The document, if the change could be performed and the document
     * was really modified, or null if not.
     * @throws Exception If something goes wrong
     */
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
                    if (newValue == null)
                    {
                        Node nue = doc.createElement(node.getNodeName());
                        node.getParentNode().replaceChild(nue, node);
                    }
                    else
                    {
                        node.setTextContent(newValue);
                    }
                }
                return doc;
            }).orElse(null);
        });
    }


    @Override
    public int hashCode()
    {
        int hash = 3;
        hash = 29 * hash + Objects.hashCode(this.in);
        hash = 29 * hash + Objects.hashCode(this.xslQuery);
        hash = 29 * hash + Objects.hashCode(this.newValue);
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        else
            if (obj == null || obj.getClass() != XMLTextContentReplacement.class)
            {
                return false;
            }
        final XMLTextContentReplacement other = (XMLTextContentReplacement) obj;
        if (!Objects.equals(this.xslQuery, other.xslQuery))
            return false;
        if (!Objects.equals(this.newValue, other.newValue))
            return false;
        return Objects.equals(this.in, other.in);
    }
}
