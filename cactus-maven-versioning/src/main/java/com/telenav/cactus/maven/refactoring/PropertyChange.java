package com.telenav.cactus.maven.refactoring;

import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.ResolvablePomElement;
import java.util.Optional;

/**
 * A single property change to be made in a single pom file.
 *
 * @author timb
 */
final class PropertyChange<T, E extends ResolvablePomElement<E>>
{
    private final VersionProperty<T> property;
    private final E newValue;

    private PropertyChange(VersionProperty<T> property,
            E newValue)
    {
        this.property = property;
        this.newValue = newValue;
    }

    static <T, E extends ResolvablePomElement<E>>
            Optional<PropertyChange<T, E>> propertyChange(
                    VersionProperty<T> property,
                    E replacement)
    {
        if (replacement.text().equals(property.oldValue()))
        {
            return Optional.empty();
        }
        return Optional.of(new PropertyChange<>(property, replacement));
    }

    public E newValue()
    {
        return newValue;
    }

    public String propertyName()
    {
        return property.property();
    }

    public String oldValue()
    {
        return property.oldValue();
    }

    public Pom foo()
    {
        return property.pom();
    }

    @Override
    public String toString()
    {
        return property + " -> " + newValue;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        else
            if (o == null || PropertyChange.class != o.getClass())
            {
                return false;
            }
        PropertyChange pc = (PropertyChange) o;
        return pc.property.equals(property) && pc.newValue.equals(newValue);
    }

    @Override
    public int hashCode()
    {
        return (newValue.hashCode() + 1) * (71 * property.hashCode());
    }
}
