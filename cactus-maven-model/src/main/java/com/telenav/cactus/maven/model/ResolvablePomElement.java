package com.telenav.cactus.maven.model;

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.maven.model.property.PropertyResolver;
import java.util.function.Function;
import java.util.function.IntSupplier;
import org.w3c.dom.Node;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * Base class for simple, immutable, textual pom elements which may be
 * represented by properties or otherwise fully specified somewhere other than
 * where they are initially encountered.
 *
 * @author Tim Boudreau
 */
public abstract class ResolvablePomElement<E extends ResolvablePomElement<E>>
        implements Comparable<E>, Resolvable<E>
{

    public static final String PLACEHOLDER = "-**-";
    private final String value;

    ResolvablePomElement(String value)
    {
        this.value = notNull("value", value);
    }

    ResolvablePomElement(Node node)
    {
        this(textOrPlaceholder(node));
    }

    private static String textOrPlaceholder(Node node)
    {
        return node == null || node.getTextContent().isBlank()
               ? PLACEHOLDER
               : node.getTextContent().trim();
    }

    @SuppressWarnings("unchecked")
    protected final E cast()
    {
        return (E) this;
    }

    /**
     * Get the string value of this object.
     *
     * @return A string
     */
    public final String value()
    {
        return value;
    }

    @Override
    public final E resolve(Function<String, String> resolver)
    {
        if (!isResolved())
        {
            String resolvedValue = resolver.apply(value);
            if (resolvedValue != null && !resolvedValue.equals(value))
            {
                return newInstance(resolvedValue);
            }
        }
        return cast();
    }

    abstract E newInstance(String what);

    @Override
    public final boolean isResolved()
    {
        return PropertyResolver.isResolved(value)
                && !isPlaceholder()
                && internalIsResolved();
    }

    public final boolean isUnresolvedProperty()
    {
        return !PropertyResolver.isResolved(value());
    }

    public final boolean isPlaceholder()
    {
        return PLACEHOLDER.equals(value);
    }

    public E preferred(E other)
    {
        if (isPlaceholder() && !other.isPlaceholder())
        {
            return other;
        }
        else
        {
            return cast();
        }
    }

    public ThrowingOptional<String> ifResolved()
    {
        if (isResolved())
        {
            return ThrowingOptional.of(value);
        }
        return ThrowingOptional.empty();
    }

    public final boolean is(String what)
    {
        return value.equals(what);
    }

    /**
     * For subclasses that have additional ways in which a string might be
     * unresolved - for example, a placeholder value that should be replaced.
     *
     * @return True if this instance is unresolved in some way other than
     * containing property references
     */
    boolean internalIsResolved()
    {
        return true;
    }

    @Override
    public final String toString()
    {
        return value;
    }

    @Override
    public final boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        else
            if (o == null || o.getClass() != getClass())
            {
                return false;
            }
        ResolvablePomElement other = (ResolvablePomElement) o;
        return other.value.equals(value);
    }

    @Override
    public final int hashCode()
    {
        return 129 * (getClass().hashCode() + 1) * value.hashCode();
    }

    @Override
    public int compareTo(E o)
    {
        return value.compareTo(o.value());
    }

    public final int compare(E other, IntSupplier fallback)
    {
        int result = compareTo(other);
        if (result == 0)
        {
            result = fallback.getAsInt();
        }
        return result;
    }
}
