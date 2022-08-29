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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Tim Boudreau
 */
public class BitsBagTest
{
    @Test
    public void testAdd()
    {
        BitsBag bag = new BitsBag();

        int sum = 0;
        for (int i = 1; i < 50; i++)
        {
            long allOnes = ones(i);
            bag.add(i, allOnes);
            sum += i;
            //            System.out.println(i + ". " + sum + " card " + bag.cardinality() + " bits " + bag.bits());
            assertEquals(sum, bag.cardinality(), "Bag reports different number of bits than added");
            assertEquals(bag.bits(), bag.cardinality(), "With all ones, bits should be same as cursor");
            assertEquals(onesString(sum) + " @ " + sum, bag.toString());
        }

        List<Boolean> vals = new ArrayList<>();
        BitsConsumer bc = value ->
        {
            boolean isTrue = (value & 0b1L) != 0;
            //            assertTrue(isTrue, "Value at " + vals.size() + " is false");
            vals.add(isTrue);
            return 1;
        };
        bag.consume(bc);
        assertEquals(sum, vals.size());
        assertEquals(bag.toString(), boolsToBits(vals) + " @ " + sum);
    }

    @Test
    public void testBitsConsumer()
    {
        System.out.println(WordLists.POSESSIVES.words());
        BitsConsumer bc = WordLists.POSESSIVES.toBitsConsumer(str ->
        {
            assertEquals("its", str);
        });
        int consumed = bc.consume(ones(8));
        assertEquals(3, WordLists.POSESSIVES.bits());
        assertEquals(3, consumed);
    }

    private static long ones(int ct)
    {
        long result = 0;
        for (int i = 0; i < ct; i++)
        {
            result |= 1 << i;
        }
        return result;
    }

    private String boolsToBits(List<Boolean> bls)
    {
        StringBuilder sb = new StringBuilder();
        for (Boolean b : bls)
        {
            sb.append(b ? "1" : "0");
        }
        return sb.toString();
    }

    private String onesString(int bits)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bits; i++)
        {
            sb.append('1');
        }
        return sb.toString();
    }
}
