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
module cactus.test {
    requires cactus.maven.plugin;
    requires cactus.cli;
    requires cactus.git;
    requires cactus.maven.log;
    requires cactus.maven.model;
    requires cactus.maven.scope;
    requires cactus.maven.versioning;
    requires cactus.maven.xml;
    requires cactus.metadata;
    requires cactus.process;
    requires cactus.util;
    requires com.mastfrog.strings;
    requires com.mastfrog.streams;
    requires com.mastfrog.preconditions;
    requires com.mastfrog.function;
    requires com.zaxxer.nuprocess;
}
