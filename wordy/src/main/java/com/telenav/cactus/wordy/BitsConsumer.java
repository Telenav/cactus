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

/**
 * Consumes some bits of the passed long and returns the number of bits that
 * were consumed.
 *
 * @author Tim Boudreau
 */
public interface BitsConsumer
{
    /**
     * Consume some of the bits of the passed long, returning the number of bits
     * that should not be offered to a subsequent consumer.
     *
     * @param value A value which may be partially or fully consumed
     *
     * @return A number of bits less than or equal to 64
     */
    int consume(long value);
}
