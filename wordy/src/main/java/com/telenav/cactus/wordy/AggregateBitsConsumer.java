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

    public AggregateBitsConsumer(Consumer<String> wordConsumer, WordList[] items) {
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
