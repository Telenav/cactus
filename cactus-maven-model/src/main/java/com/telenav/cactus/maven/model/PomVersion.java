package com.telenav.cactus.maven.model;

import com.telenav.cactus.maven.model.resolver.versions.VersionComparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import org.w3c.dom.Node;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.telenav.cactus.maven.model.VersionChangeMagnitude.DOT;
import static com.telenav.cactus.maven.model.VersionChangeMagnitude.NONE;
import static com.telenav.cactus.maven.model.VersionFlavor.RELEASE;
import static com.telenav.cactus.maven.model.VersionFlavorChange.TO_SNAPSHOT;

/**
 * A maven version which may be a placeholder, a property reference or a fully
 * specified version.
 *
 * @author Tim Boudreau
 */
public final class PomVersion extends ResolvablePomElement<PomVersion>
{

    /**
     * The unknown version, denoted by the string "---", which is used for
     * dependencies that are initially read from a &lt;dependencies&gt; section
     * of a pom, where the value will be determined by a
     * &lt;dependencyManagement&gt; section somewhere else.
     */
    public static final PomVersion UNKNOWN = new PomVersion(PLACEHOLDER);

    PomVersion(String version)
    {
        super(version);
    }

    PomVersion(Node node)
    {
        super(node);
    }

    public static PomVersion of(Node n)
    {
        if (n == null)
        {
            return UNKNOWN;
        }
        return new PomVersion(n);
    }

    public static PomVersion of(String what)
    {
        return new PomVersion(what);
    }

    @Override
    PomVersion newInstance(String what)
    {
        return of(what);
    }

    @Override
    public int compareTo(PomVersion o)
    {
        if (o.equals(this))
        {
            return 0;
        }
        return VersionComparator.INSTANCE.compare(this.text(), o.text());
    }

    private static final Pattern SUFFIX_PATTERN = Pattern.compile(
            "^[\\d.]+\\.\\d+(\\S+)");

    /**
     * Update the version in accordance with semantic versioning / 3-digit dewey
     * decimal.
     *
     * @param magnitude The digit position to change - major, minor or dot or no
     * change
     * @param flavorChange How to change the flavor (snapshot, release,
     * random-suffix) of this version - can be forced to relese or snapshot,
     * forced to the opposite of what it is now, or left alone
     * @return An optional if the result is not the same as this version (some
     * code will want to fail if a version bump isn't one).
     */
    public Optional<PomVersion> updatedWith(VersionChangeMagnitude magnitude,
            VersionFlavorChange flavorChange)
    {
        Optional<String> sfx = flavorChange.newSuffix(this);
        List<Long> decimals = decimals();
        while (magnitude.ordinal() >= decimals.size())
        {
            // If there are not enough decimals, add to fit
            decimals.add(0L);
        }
        if (flavor() == RELEASE && flavorChange == TO_SNAPSHOT && magnitude == NONE)
        {
            // Never go from a release version to a snapshot version with the
            // same number - always increment
            magnitude = DOT;
        }
        String newHead = magnitude.increment(decimals);
        String result = sfx.map(suffix ->
        {
            if (!suffix.startsWith("-"))
            {
                suffix = '-' + suffix;
            }
            return newHead + suffix;
        }).orElse(newHead);
        if (result.equals(text()))
        {
            return Optional.empty();
        }
        return Optional.of(of(result));
    }

    private String decimalHead()
    {
        String text = text();
        if (text.isEmpty())
        {
            return "";
        }
        return headTail(text, (head, tail) ->
        {
            return head;
        });
    }

    /**
     * Get the leading dot-delimited decimal portion of this version.
     * 
     * @return A list of numbers
     */
    public List<Long> decimals()
    {
        return VersionComparator.extractNumerics(decimalHead().split("[\\.-]+"));
    }

    /**
     * Get the suffix of this version, if there is one.
     * 
     * @return The suffix
     */
    public Optional<String> suffix()
    {
        return suffixOf(text());
    }

    /**
     * Get a descriptor for the suffix or its absence that describes the
     * semantics of what this version is (release, snapshot, other).
     * 
     * @return A flavor
     */
    public VersionFlavor flavor()
    {
        return suffix().map(sfx ->
        {
            switch (sfx)
            {
                case "-SNAPSHOT":
                    return VersionFlavor.SNAPSHOT;
                default:
                    return VersionFlavor.OTHER;
            }
        }).orElse(VersionFlavor.RELEASE);
    }

    static Optional<String> suffixOf(String what)
    {
        if (notNull("what", what).isEmpty())
        {
            return Optional.empty();
        }
        return Optional.ofNullable(headTail(what, (head, tail) ->
        {
            return tail.isEmpty()
                   ? null
                   : tail;
        }));
    }

    /**
     * Split the version into leading decimals and any trailing characters.
     * 
     * @param what A version string
     * @param func A function that computes the return value from both of them
     * @return A string
     */
    static String headTail(String what, BiFunction<String, String, String> func)
    {
        StringBuilder sb = new StringBuilder();
        boolean lastWasDot = true;
        for (int i = 0; i < what.length(); i++)
        {
            char c = what.charAt(i);
            if (c >= '0' && c <= '9')
            {
                lastWasDot = false;
                sb.append(c);
            }
            else
                if (c == '.')
                {
                    if (lastWasDot)
                    {
                        break;
                    }
                    else
                    {
                        sb.append(c);
                    }
                    lastWasDot = true;
                }
                else
                {
                    // Nun numeric char, don't skip it like a delimiter
                    func.apply(sb.toString(), what.substring(i,
                            what.length()));
                    break;
                }
        }
        return func.apply(sb.toString(), what.substring(sb.length(), what
                .length()));
    }
}
