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
package com.telenav.cactus.maven.model.resolver.versions;

/**
 * Just a string equality predicate with a reasonable toString() implementation.
 * Used for -SNAPSHOT dependencies and similar which must be matched exactly.
 *
 * @author Tim Boudreau
 */
final class ExactMatcher implements VersionPredicate<String>
{
    private final String spec;

    ExactMatcher(String spec)
    {
        this.spec = spec;
    }

    @Override
    public boolean test(String t)
    {
        return spec.equals(t);
    }

    @Override
    public String toString()
    {
        return "==" + spec;
    }
}
