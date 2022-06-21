package com.telenav.cactus.maven.model;

import org.w3c.dom.Node;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 *
 * @author Tim Boudreau
 */
public final class GroupId extends ResolvablePomElement<GroupId>
{
    GroupId(String groupId)
    {
        super(groupId);
    }

    GroupId(Node node)
    {
        super(node);
    }

    public static GroupId of(String what)
    {
        return new GroupId(notNull("what", what));
    }

    public static GroupId of(Node what)
    {
        return new GroupId(what);
    }

    @Override
    GroupId newInstance(String what)
    {
        return of(what);
    }
}
