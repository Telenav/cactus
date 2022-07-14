package com.telenav.cactus.maven;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Objects;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * A printable message used by PrintMessageMojo. Needs to be public to satisfy
 * broken modular classloading. Non-api.
 *
 * @author Tim Boudreau
 */
public class PrintableMessage
{
    // This class runs in a shutdown hook and must NOT trigger any classloading.
    private final CharSequence message;
    /**
     * In embedded mode, in an IDE, our message could be kept alive until IDE
     * shutdown. We do NOT want to leak the maven session for eternity.
     */
    private final Reference<MavenSession> sess;
    private final Boolean onFailure;

    public PrintableMessage(CharSequence msg, MavenSession sess,
            Boolean printOnFailure)
    {
        this.sess = new WeakReference<>(sess);
        this.message = notNull("msg", msg);
        this.onFailure = printOnFailure;
    }

    public boolean shouldPrint()
    {
        // This code was much nicer with an enum of always, on-failure and
        // on-success, but we don't have a classloader that can load the
        // generated inner classes of enums during a shutdown hook.
        if (onFailure == null)
        {
            return true;
        }
        boolean hasExceptions = false;
        MavenSession s = sess.get();
        if (s != null)
        {
            MavenExecutionResult result = s.getResult();
            if (result != null && result.hasExceptions())
            {
                hasExceptions = true;
            }
        }
        if (!onFailure)
        {
            return !hasExceptions;
        }
        else
        {
            return hasExceptions;
        }
    }

    public String toString()
    {
        return message.toString();
    }

    @Override
    public int hashCode()
    {
        int hash = 3;
        hash = 97 * hash + Objects.hashCode(this.message);
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final PrintableMessage other = (PrintableMessage) obj;
        return Objects.equals(this.message, other.message);
    }

}
