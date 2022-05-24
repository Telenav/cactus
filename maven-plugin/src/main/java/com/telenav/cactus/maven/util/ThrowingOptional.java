package com.telenav.cactus.maven.util;

import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingFunction;
import com.mastfrog.function.throwing.ThrowingPredicate;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingSupplier;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.Exceptions;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Because optional is unusable with code that routinely does I/O or waits on
 * things, without becoming littered with try/catch blocks if you try to use
 * functional operations.
 *
 * @author Tim Boudreau
 */
public final class ThrowingOptional<T> implements Supplier<T>
{

    private static final ThrowingOptional<Object> EMPTY = new ThrowingOptional<>(
            Optional.empty());
    private final Optional<T> delegate;

    ThrowingOptional(Optional<T> delegate)
    {
        this.delegate = delegate;
    }
    
    public static <T> ThrowingOptional<T> from(Optional<T> delegate) {
        return new ThrowingOptional<>(notNull("delegate", delegate));
    }

    @SuppressWarnings("unchecked")
    public static <T> ThrowingOptional<T> empty()
    {
        return (ThrowingOptional<T>) EMPTY;
    }

    public static <T> ThrowingOptional<T> of(T obj)
    {
        return new ThrowingOptional<>(Optional.ofNullable(obj));
    }

    public Optional<T> toOptional()
    {
        return delegate;
    }

    @Override
    public T get()
    {
        return delegate.get();
    }

    public boolean isPresent()
    {
        return delegate.isPresent();
    }

    public boolean isEmpty()
    {
        return delegate.isEmpty();
    }

    public void ifPresent(ThrowingConsumer<? super T> action)
    {
        delegate.ifPresent(action.toNonThrowing());
    }

    public void ifPresentOrElse(ThrowingConsumer<? super T> action, ThrowingRunnable emptyAction)
    {
        delegate.ifPresentOrElse(action.toNonThrowing(), emptyAction.toRunnable());
    }

    public ThrowingOptional<T> filter(ThrowingPredicate<? super T> predicate)
    {
        return new ThrowingOptional<>(delegate.filter(obj ->
        {
            try
            {
                return predicate.test(obj);
            } catch (Exception | Error e)
            {
                return Exceptions.chuck(e);
            }
        }));
    }

    public <U> ThrowingOptional<U> map(ThrowingFunction<? super T, ? extends U> mapper)
    {
        return new ThrowingOptional<>(delegate.map(mapper.toNonThrowing()));
    }

    public <U> ThrowingOptional<U> flatMap(ThrowingFunction<? super T, ? extends Optional<? extends U>> mapper)
    {
        return new ThrowingOptional<>(delegate.flatMap(mapper.toNonThrowing()));
    }
    
    public <U> ThrowingOptional<U> flatMapThrowing(ThrowingFunction<? super T, ? extends ThrowingOptional<? extends U>> mapper)
    {
        return new ThrowingOptional<>(delegate.flatMap(obj -> {
            return mapper.toNonThrowing().apply(obj).toOptional();
        }));
    }
    

    public ThrowingOptional<T> or(ThrowingSupplier<? extends Optional<? extends T>> supplier)
    {
        return new ThrowingOptional<>(delegate.or(() ->
        {
            try
            {
                return supplier.get();
            } catch (Exception | Error e)
            {
                return Exceptions.chuck(e);
            }
        }));
    }

    public Stream<T> stream()
    {
        return delegate.stream();
    }

    public T orElse(T other)
    {
        return delegate.orElse(other);
    }

    public T orElseGet(ThrowingSupplier<? extends T> supplier)
    {
        return delegate.orElseGet(() ->
        {
            try
            {
                return supplier.get();
            } catch (Exception | Error e)
            {
                return Exceptions.chuck(e);
            }
        });
    }

    @Override
    public String toString()
    {
        if (delegate.isPresent())
        {
            return delegate.get().toString();
        }
        return "ThrowingOptional.empty()";
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        } else if (o == null || o.getClass() != ThrowingOptional.class)
        {
            return false;
        }
        ThrowingOptional other = (ThrowingOptional) o;
        return other.delegate.equals(delegate);
    }

    @Override
    public int hashCode()
    {
        return delegate.hashCode();
    }
}
