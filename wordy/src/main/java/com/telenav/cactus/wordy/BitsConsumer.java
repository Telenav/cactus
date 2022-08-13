package com.telenav.cactus.wordy;

/**
 * Consumes some bits of the passed long and returns the number of bits that
 * were consumed.
 *
 * @author Tim Boudreau
 */
public interface BitsConsumer {

    int consume(long value);
}
