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
package com.telenav.cactus.process.internal;

import com.telenav.cactus.process.ProcessState;

/**
 * Internal interface for getting notified on process exit. Note: This package
 * is not published to the world at large via the module system, and subject to
 * change.
 *
 * @author Tim Boudreau
 */
public interface ProcessListener
{
    /**
     * Called when the process exits.
     *
     * @param state The exit state of the process
     */
    void processExited(ProcessState state);
}
