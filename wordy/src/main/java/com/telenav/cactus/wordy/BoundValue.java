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

import java.util.function.LongSupplier;

/**
 * A value which is a positive integer within a range from zero to a maximum
 * which is greater than or equal to the value - i.e. 7 of 9.
 *
 * @author Tim Boudreau
 */
public interface BoundValue
{

    /**
     * The value.
     *
     * @return A value
     */
    long n();

    /**
     * The maximum possible value.
     *
     * @return A maximum
     */
    long of();

    /**
     * The number of bits required to express the maximum possible value.
     *
     * @return A number of bits
     */
    default int bits()
    {
        if (of() <= 1L)
        {
            return 0;
        }
        else
            if (of() == 2L)
            {
                return 1;
            }
        return WordLists.bits(of());
    }

    /**
     * Append this value to a BitsBag.
     *
     * @param bag A bag
     */
    default void addTo(BitsBag bag)
    {
        bag.add(bits(), n());
    }

    /**
     * Get the value as a fraction.
     *
     * @return A double
     */
    default double asFraction()
    {
        double mx = of();
        double val = n();
        return val / mx;
    }

    static BoundValue boundValue(long n, long of)
    {
        if (n > of)
        {
            throw new IllegalArgumentException(n + "/" + of);
        }
        if (n < 0 || of < 0)
        {
            throw new IllegalArgumentException(
                    "Negative value in " + n + "/" + of);
        }
        return new FixedBoundValue(n, of);
    }

    /**
     * Allows a LongSupplier to wrap around a maximum value, so the maximum is
     * the passed value, and the n value is the value returned by the supplier
     * modulo that maximum.
     *
     * @param at The wrap point
     * @param supp A value supplier
     * @return A bound value
     */
    static BoundValue wrap(long at, LongSupplier supp)
    {
        return new BoundValue()
        {
            @Override
            public long n()
            {
                return supp.getAsLong() % at;
            }

            @Override
            public long of()
            {
                return at;
            }
        };
    }
}
