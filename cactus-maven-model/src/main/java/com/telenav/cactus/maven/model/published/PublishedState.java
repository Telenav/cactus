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
package com.telenav.cactus.maven.model.published;

/**
 * Publication state.
 *
 * @author Tim Boudreau
 */
public enum PublishedState
{
    /**
     * The artifact has been published, and the downloaded bits are identical.
     */
    PUBLISHED_IDENTICAL,
    /**
     * The artifact has been published, and the downloaded bits are different.
     */
    PUBLISHED_DIFFERENT,
    /**
     * The artifact has not been published.
     */
    NOT_PUBLISHED;

    /**
     * Returns true if this state indicates the library has been published and
     * does not match the local copy.
     *
     * @return whether or not the remote and local versions differ
     */
    public boolean differs() {
        return this == PUBLISHED_DIFFERENT;
    }

    public String toString()
    {
        return name().toLowerCase().replace('_', '-');
    }

}
