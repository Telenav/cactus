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

import java.util.function.Consumer;

/**
 * Round robins through an array of WordLists, calling each one to consume some
 * bits.
 *
 * @author Tim Boudreau
 */
final class AggregateBitsConsumer implements BitsConsumer {

    private int consumed;
    private int cursor;
    private final BitsConsumer[] consumers;

    AggregateBitsConsumer(Consumer<String> wordConsumer, WordList[] items) {
        consumers = new BitsConsumer[items.length];
        for (int i = 0; i < consumers.length; i++) {
            consumers[i] = items[i].toBitsConsumer(wordConsumer);
        }
    }

    public BitsConsumer nextConsumer() {
        return consumers[cursor++ % consumers.length];
    }

    public int consumed() {
        return consumed;
    }

    @Override
    public int consume(long value) {
        int result = nextConsumer().consume(value);
        consumed += result;
        return result;
    }

}
