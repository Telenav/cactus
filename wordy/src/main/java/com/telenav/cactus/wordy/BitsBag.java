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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Maintains a cursor into a BitSet and can have bits appended to it.
 *
 * @author Tim Boudreau
 */
public final class BitsBag
{
    private final BitSet set;
    private int cursor = 0;

    public BitsBag()
    {
        set = new BitSet(256);
    }

    public BitsBag(BitSet set, int cursor)
    {
        this.set = set;
        this.cursor = cursor;
    }

    int cardinality()
    {
        return set.cardinality();
    }

    void shuffle(Random rnd)
    {
        List<Integer> values = new ArrayList<>(cursor);
        for (int i = 0; i <= cursor; i++)
        {
            values.add(i);
        }
        Collections.shuffle(values, rnd);
        for (int i = 0; i < values.size(); i++)
        {
            int altIx = values.get(i);
            swap(i, altIx);
        }
    }

    private void swap(int a, int b)
    {
        if (a == b)
        {
            return;
        }
        boolean aval = set.get(a);
        boolean bval = set.get(b);
        if (aval != bval)
        {
            set.set(b, aval);
            set.set(a, bval);
        }
    }

    public BitsBag reverse()
    {
        BitSet nue = new BitSet();
        for (int bit = set.nextSetBit(0); bit >= 0; bit = set
                .nextSetBit(bit + 1))
        {
            nue.set(cursor - bit);
        }
        return new BitsBag(nue, cursor);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(cursor + 1);
        for (int i = 0; i < cursor; i++)
        {
            if (set.get(i))
            {
                sb.append('1');
            }
            else
            {
                sb.append('0');
            }
        }
        if (sb.length() == 0)
        {
            sb.append("-empty-");
        }
        sb.append(" @ ").append(cursor);
        return sb.toString();
    }

    public void consume(BitsConsumer c)
    {
        for (int i = 0; i < cursor;)
        {
            long v = valueAt(i);
            int consumed = c.consume(v);
            i += consumed;
        }
    }

    private long valueAt(int position)
    {
        // Pending - this could be plenty more efficient, shifting the
        // existing word and or'ing in a few bits.  And it is not worth it.
        long result = 0;
        for (int i = 0; i < 64; i++)
        {
            boolean isSet = set.get(position + i);
            if (isSet)
            {
                long val = 1 << i;
                result |= val;
            }
        }
        return result;
    }

    public int bits()
    {
        return cursor;
    }

    /**
     * Add the number of bits specified from the passed long value starting at
     * bit zero, and increment the cursor for adding the next bits.
     *
     * @param bits A number of bits
     * @param value
     */
    public void add(int bits, long value)
    {
        if (bits > 64)
        {
            throw new IllegalArgumentException(
                    "Impossible number of bits: " + bits);
        }
        for (int i = 0; i < bits; i++)
        {
            boolean setIt = (value & 1) != 0;
            if (setIt)
            {
                set.set(cursor);
            }
            cursor++;
            value >>= 1;
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        else
            if (o == null || o.getClass() != BitsBag.class)
            {
                return false;
            }
        BitsBag bb = (BitsBag) o;
        return toString().equals(bb.toString());
    }

    @Override
    public int hashCode()
    {
        int result = 0;
        for (int bit = set.nextSetBit(0); bit >= 0; bit = set
                .nextSetBit(bit + 1))
        {
            result += 71 * (bit + 1);
        }
        result *= ((cursor + 1) * 261);
        return result;
    }
}
