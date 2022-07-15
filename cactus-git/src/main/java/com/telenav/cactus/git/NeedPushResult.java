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

package com.telenav.cactus.git;

/**
 * @author Tim Boudreau
 */
public enum NeedPushResult
{
    NOT_ON_A_BRANCH,
    REMOTE_BRANCH_DOES_NOT_EXIST,
    YES,
    NO;

    static NeedPushResult of(boolean result)
    {
        return result
               ? YES
               : NO;
    }
    
    @Override
    public String toString() {
        return name().toLowerCase().replace('_', '-');
    }

    public boolean canBePushed()
    {
        return this == YES || this == REMOTE_BRANCH_DOES_NOT_EXIST;
    }

    public boolean needCreateBranch()
    {
        return this == REMOTE_BRANCH_DOES_NOT_EXIST;
    }
}
