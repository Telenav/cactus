package com.telenav.cactus.maven.model.resolver.versions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Does a best effort fuzzy dewey decimal comparison.  Old code.
 */
public final class VersionComparator implements Comparator<String> {
    public static final VersionComparator INSTANCE = new VersionComparator();

    private VersionComparator()
    {
    }

    @Override
    public int compare(String a, String b)
    {
        return performComparison(a, b);
    }

    private static int compare(List<Long> as, List<Long> bs)
    {
        for (int i = 0; i < Integer.min(as.size(), bs.size()); i++)
        {
            long a = as.get(i);
            long b = bs.get(i);
            int res = Long.compare(a, b);
            if (res != 0)
            {
                return res;
            }
        }
        return 0;
    }
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+|x)+");

    private static List<Long> extractNumerics(String[] parts)
    {
        List<Long> result = new ArrayList<>();
        for (String s : parts)
        {
            Matcher m = NUMBER_PATTERN.matcher(s);
            if (m.find())
            {
                for (int i = 1; i < m.groupCount(); i++)
                {
                    String nums = m.group(i);
                    if ("x".equals(nums))
                    {
                        result.add(Long.MAX_VALUE);
                    }
                    else
                    {
                        result.add(Long.parseLong(nums));
                    }
                }
            }
        }
        return result;
    }

    private static int performComparison(String a, String b)
    {
        String[] aSplit = a.split("\\.");
        String[] bSplit = b.split("\\.");
        if (aSplit.length == 1 && bSplit.length == 1)
        {
            return aSplit[0].compareTo(bSplit[0]);
        }
        List<Long> aNumerics = extractNumerics(aSplit);
        List<Long> bNumerics = extractNumerics(bSplit);
        int result = 0;
        if (!aNumerics.isEmpty() && !bNumerics.isEmpty())
        {
            result = compare(aNumerics, bNumerics);
            if (result == 0)
            {
                result = Integer.compare(aNumerics.size(), bNumerics.size());
            }
            if (result == 0)
            {
                result = Integer.compare(a.length(), b.length());
            }
        }
        if (result == 0)
        {
            result = a.compareTo(b);
        }
        return result;
    }

}
