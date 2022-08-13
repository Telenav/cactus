////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// © 2011-2022 Telenav, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
package com.telenav.cactus.wordy;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static java.lang.Math.abs;
import static java.lang.Math.floor;
import static java.lang.Math.log;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 * @author Tim Boudreau
 */
public enum WordLists implements WordList
{
    // We pass the number of items in each list file, so choosing the
    // best recipe doesn't requires loading vast lists that aren't going
    // to be used.  A test enforces that the static number here matches the
    // actual list size.
    ADJECTIVES(4_847),
    NOUNS(1_523),
    PREPOSITIONS(52),
    POSESSIVES(8),
    LARGE_NOUNS(28_865),
    ADVERBS(319),
    VERBS(3_248),
    PRONOUNS(4),
    BUILD_NAME_ADJECTIVES(64),
    BUILD_NAME_NOUNS(64),
    LARGE_ADVERBS(2_694),
    LARGE_VERBS(6_056),
    LARGE_ADJECTIVES(11_204),;

    private static long SEED;
    private List<String> words;
    private long p2 = -1;
    private long mask;
    final int staticSize;

    WordLists(int size)
    {
        this.staticSize = size;
    }

    WordLists()
    {
        this(-1);
    }

    @Override
    public int bits()
    {
        return Long.numberOfTrailingZeros(lastPowerOfTwo());
    }

    public long lastPowerOfTwo()
    {
        return p2 == -1
               ? p2 = nearestPowerOfTwoLessThan(size())
               : p2;
    }

    @Override
    public long mask()
    {
        if (mask != 0)
        {
            return mask;
        }
        long result = 0;
        for (int i = 0; i < bits(); i++)
        {
            result |= 1 << i;
        }
        return mask = result;
    }

    static int bits(long possibleValues)
    {
        int result = Long.numberOfTrailingZeros(nearestPowerOfTwoLessThan(
                possibleValues));
        if (result == 0)
        {
            throw new Error("0 bits in " + possibleValues + "? npt is "
                    + nearestPowerOfTwoLessThan(possibleValues));
        }
        return result;
    }

    static long nearestPowerOfTwoLessThan(long count)
    {
        double pow = floor(log(count) / log(2));
        return (long) Math.pow(2, pow);
    }

    @Override
    public int size()
    {
        return staticSize < 0
               ? words().size()
               : staticSize;
    }

    @Override
    public int indexOf(String word)
    {
        return words().indexOf(word);
    }

    @Override
    public String word(int index)
    {
        List<String> w = words();
        return w.get(abs(index) % w.size());
    }

    static Random shuffler()
    {
        return new Random(seed());
    }

    public List<String> words()
    {
        if (words != null)
        {
            return words;
        }
        try
        {
            // We want a new Random each time, so changing th enum
            // order of number of constants cannot change thw way lists
            // are shuffled
            words = load(shuffler());
        }
        catch (Exception | Error ex)
        {
            throw new Error(ex);
        }
        return words;
    }

    boolean hidden()
    {
        switch (this)
        {
            case BUILD_NAME_ADJECTIVES:
            case BUILD_NAME_NOUNS:
                return true;
        }
        return false;
    }

    @Override
    public String toString()
    {
        return name().toLowerCase().replaceAll("_", "");
    }

    private String resourceName()
    {
        return name().toLowerCase().replaceAll("_", "") + ".txt";
    }

    private List<String> load(Random shuffler) throws IOException
    {
        String nm = resourceName();
        String list;// = Streams.readResourceAsUTF8(WordLists.class, nm);
        try ( InputStream in = WordLists.class.getResourceAsStream(nm))
        {
            if (in == null)
            {
                throw new Error("No resource read for " + nm
                        + " on classpath next to " + getClass());
            }
            list = new String(in.readAllBytes(), UTF_8);
        }
        List<String> result = new ArrayList<>();
        for (String line : list.split("\n"))
        {
            line = line.trim();
            if (line.isEmpty() || line.charAt(0) == '#')
            {
                continue;
            }
            result.add(line);
        }
        if (!hidden())
        {
            Collections.shuffle(result, shuffler);
        }
        return result;
    }

    static WordLists find(String what)
    {
        for (WordLists wl : values())
        {
            if (wl.name().toLowerCase().equals(what.toLowerCase()))
            {
                return wl;
            }
            else
                if (wl.toString().equals(what.toLowerCase()))
                {
                    return wl;
                }
        }
        return null;
    }

    static long seed()
    {
        if (SEED != 0)
        {
            return SEED;
        }
        long result = 9_563_412_748_503_859L;
        String val = System.getProperty("wordy-shuffle-seed");
        if (val != null)
        {
            try
            {
                result = Long.parseLong(val.trim());
            }
            catch (NumberFormatException nfe)
            {
                System.err.println(
                        "Could not parse property wordy-shuffle-seed '" + val);
            }
        }
        return SEED = result;
    }
}
