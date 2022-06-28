package com.telenav.cactus.maven.model;

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.maven.model.property.PropertyResolver;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
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

    /**
     * Convenience method for casting to the type parameter, so that is always
     * done in one place.
     *
     * @return This cast to E
     */
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
    public final String text()
    {
        return value;
    }

    /**
     * Convert this element to, potentially a new element using text derived
     * from this one's and the passed function. Used when resolving dependencies
     * to turn a property like <code>${foo.version}</code> into the value of
     * that property.
     *
     * @param transform A function which can transform the value of this object
     * into one of the same type with a different textual value
     * @return Either this, or if the transform results in a change, a new
     * instance of this object's type.
     */
    @Override
    public final E resolve(Function<String, String> transform)
    {
        if (!isResolved())
        {
            String resolvedValue = transform.apply(value);
            if (resolvedValue != null && !resolvedValue.equals(value))
            {
                return newInstance(resolvedValue);
            }
        }
        return cast();
    }

    /**
     * Determine if the text content of this element contains the passed string.
     *
     * @param text Some text
     * @return
     */
    public final boolean textContains(String text)
    {
        return value.contains(notNull("text", text));
    }

    /**
     * Determine if the text content of this element starts with the passed string.
     *
     * @param text Some text
     * @return
     */
    public final boolean textStartsWith(String text)
    {
        return value.startsWith(notNull("text", text));
    }
    /**
     * Determine if the text content of this element ends with the passed string.
     *
     * @param text Some text
     * @return
     */
    public final boolean textEndsWith(String text)
    {
        return value.endsWith(notNull("text", text));
    }

    /**
     * Determine if the text content of this element matches the passed predicate.
     *
     * @param test A test
     * @return the result of applying the test
     */
    public final boolean textMatches(Predicate<String> test)
    {
        return notNull("test", test).test(value);
    }

    /**
     * Create a new instance of this object's type using the passed text.
     * 
     * @param what The text
     * @return An instance of this class
     */
    protected abstract E newInstance(String what);

    /**
     * Determine if this object represents a value which does not need
     * dereferencing and is not a placeholder value.
     * 
     * @return WHether or not the value needs further resolution - if it is
     * usable as-is
     */
    @Override
    public final boolean isResolved()
    {
        return PropertyResolver.isResolved(value)
                && !isPlaceholder()
                && internalIsResolved();
    }

    /**
     * Determine if the value contains the sequence of <code>${</code>
     * followed at some point by <code>}</code> indicating the value is
     * or contains an unresolved property.
     * 
     * @return whether or not the value needs resolving
     */
    public final boolean isUnresolvedProperty()
    {
        return !PropertyResolver.isResolved(text());
    }

    /**
     * Determine whether or not the value in this object is a <i>placeholder</i>
     * which should be somehow replaced by a value found somewhere other than
     * the place this object was parsed from - for example, a <code>&lt;dependency&gt;</code>
     * entry in a pom.xml may not contain a version at all, because the version is
     * spelled out in a <code>&lt;dependencyManagement&gt;</code> section of a
     * different file.
     * 
     * @return True if the value is the placeholder string
     */
    public final boolean isPlaceholder()
    {
        return PLACEHOLDER.equals(value);
    }

    public ThrowingOptional<String> ifResolved()
    {
        if (isResolved())
        {
            return ThrowingOptional.of(value);
        }
        return ThrowingOptional.empty();
    }

    /**
     * Determine if the text content equals the passed text.
     * 
     * @param what Some text
     * @return True if they are an exact match
     */
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
        return value.compareTo(o.text());
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
