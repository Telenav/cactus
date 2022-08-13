package com.telenav.cactus.wordy;

/**
 *
 * @author Tim Boudreau
 */
final class FixedBoundValue implements BoundValue {

    private final long n;
    private final long of;

    FixedBoundValue(long n, long of) {
        this.n = n;
        this.of = of;
    }

    @Override
    public long n() {
        return n;
    }

    @Override
    public long of() {
        return of;
    }

    @Override
    public String toString() {
        return n + "/" + of;
    }
}
