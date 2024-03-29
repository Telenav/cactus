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

package com.telenav.cactus.analysis;

import com.telenav.cactus.maven.model.Pom;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
public interface ProjectScanConsumer
{
    void onProjectScanned(Pom pom, Map<Path, Integer> scores) throws IOException;

    default void onDone() throws IOException
    {

    }
}
