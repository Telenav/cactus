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

import static com.telenav.cactus.wordy.WordLists.nearestPowerOfTwoLessThan;
import static java.lang.Long.numberOfTrailingZeros;
import java.util.function.Consumer;

/**
 * A list of words which can be looked up by index, which can express how many
 * bits can possibly be distinctly represented by its contents.
 *
 * @author Tim Boudreau
 */
public interface WordList {

    int size();

    String word(int index);

    default int bits() {
        return numberOfTrailingZeros(nearestPowerOfTwoLessThan(size()));
    }

    default int indexOf(String word) {
        int sz = size();
        for (int i = 0; i < sz; i++) {
            if (word.equals(word(i))) {
                return i;
            }
        }
        return -1;
    }

    default BitsConsumer toBitsConsumer(Consumer<String> c) {
        int bits = bits();
        return value -> {
            long masked = mask() & value;
            int val = (int) masked;
            c.accept(word(val));
            return bits;
        };
    }

    default long mask() {
        long result = 0;
        for (int i = 0; i <= bits(); i++) {
            result |= 1 << i;
        }
        return result;
    }

}
