package com.telenav.cactus.maven.model;

import org.w3c.dom.Node;

/**
 * Relative path to a parent pom - may be unspecified, which gets the
 * value DEFAULT, or may actively have empty content, which gets the value
 * NONE - otherwise it <i>is</i> a relative path from one pom to another.
 *
 * @author Tim Boudreau
 */
public final class ParentRelativePath extends ResolvablePomElement<ParentRelativePath>
{
    public static final ParentRelativePath NONE = new ParentRelativePath("");
    public static final ParentRelativePath DEFAULT = new ParentRelativePath(
            "../pom.xml");

    ParentRelativePath(String value)
    {
        super(value);
    }

    ParentRelativePath(Node node)
    {
        super(node);
    }

    public static ParentRelativePath of(Node node)
    {
        if (node == null)
        {
            return DEFAULT;
        }
        if (node.getTextContent() == null || node.getTextContent().isBlank())
        {
            return NONE;
        }
        return new ParentRelativePath(node);
    }

    public boolean isNone()
    {
        return "".equals(value());
    }

    @Override
    ParentRelativePath newInstance(String what)
    {
        return new ParentRelativePath(what);
    }

}
