package com.telenav.cactus.maven.xml;

import com.mastfrog.function.optional.ThrowingOptional;
import java.util.Objects;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

/**
 * Deletes the result of an xpath query if it is matched.
 *
 * @author Tim Boudreau
 */
public final class XMLElementRemoval extends AbstractXMLUpdater
{
    private final String query;

    public XMLElementRemoval(XMLFile file, String query)
    {
        super(file);
        this.query = query;
    }

    @Override
    public Document replace() throws Exception
    {
        return in.inContext(doc ->
        {
            return in.nodeQuery(query).flatMapThrowing(node ->
            {
                Node parent = node.getParentNode();
                if (parent != null)
                {
                    Node sib = node.getNextSibling();
                    // If there is a subsequent whitespace node, remove it.
                    if (sib instanceof Text && sib.getTextContent().trim().isEmpty())
                    {
                        parent.removeChild(sib);
                    }
                    parent.removeChild(node);
                    return ThrowingOptional.of(doc);
                }
                return ThrowingOptional.empty();
            }).orElse(null);
        });
    }

    @Override
    public int hashCode()
    {
        int hash = 3;
        hash = 29 * hash + Objects.hashCode(this.in);
        hash = 29 * hash + Objects.hashCode(this.query);
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
            if (obj == null || obj.getClass() != XMLElementRemoval.class)
            {
                return false;
            }
        final XMLElementRemoval other = (XMLElementRemoval) obj;
        if (!Objects.equals(this.query, other.query))
            return false;
        return Objects.equals(this.in, other.in);
    }
    
    @Override
    public String toString() {
        return query + " REMOVAL in " + in;
    }
}
