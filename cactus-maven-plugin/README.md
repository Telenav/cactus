[//]: # (start-user-text)

# Cactus Maven Plugin

This project contains a Maven plugin that contains a set of Maven mojos specifically 
designed for working in environments where multiple trees of projects are joined 
together with a root bill-of-materials POM using Git submodules. You can think of 
this development style as an extension to git-flow for large, multi-module sets 
of related projects.

## Assumptions and Use Cases

Where this plugin is really useful is in projects which are large trees of multi-module
projects, particularly when those use git submodules, and you occasionally make changes
that span multiple modules.  For example, say you have changes in several projects in
different submodules you want to commit:

```sh
mvn -Dcactus.push=true -Dcactus.scope=all '-Dcactus.commit-message=Fix the thing' \
    com.telenav.cactus:cactus-maven-plugin:commit
```

will figure out which repositories have changes, add them and commit them in each
repository, with the same commit message (the commit message will contain info about
what was changed).

Add `-Dcactus.include-root=true` and it will also get a new commit in the submodule
root, so that it points to the new commits.

Add `-Dcactus.push=true` and it will push for you as well.

Similarly, `com.telenav.cactus:cactus-maven-plugin:push` will figure out what submodules
need pushing and push them (`git submodule foreach git push` would abort on the first
repository that didn't have anything to push).

Creating branches across multiple modules is similarly simplified:

```
mvn -Dcactus.scope=all-project-families -Dcactus.create-branches=true -Dcactus.update-root=true \
    -Dcactus.push=true -Dcactus.target-branch=feature/something \
    com.telenav.cactus:cactus-maven-plugin:checkout
```

will create a branch named `feature/something` in any submodules that don't have one,
based on the a default branch (`develop` by default, can be set) - or switch to that
branch if you already have it - or, if one with that name exists remotely but not
locally, check it out locally.  So, you express _I want to be on a branch called X_
and the Right Thing happens.


### Scopes and Families

Most mojos in the Cactus plugin can have what they apply to controlled via the `cactus.scope` 
property.  Unlike typical Maven mojos, these operate at the level of git checkouts - and
(since many projects under an aggregator may be in the same repository) run _once_ at
the end of a build cycle.

A key concept is the "project family" - these are projects that have similar group ids -
the same Maven group-id suffix, omitting any characters after the first `-` character - so
com.foo.fooframework and com.foo.fooframework-extensions both are members of the family
"fooframework".

When the `family` scope is selected, mojos that match a family will operate on _all git
submodules containing at least one maven project with a group-id in that family.

You can manually pass `-Dcactus.family=[branch-name]` to override the default family detection
mechanism (this is useful when making changes when running Maven against a root pom which
has some other group-id).

The possible values of `cactus.scope` are:


  * `just-this` - Operate only on the git submodule the project maven was invoked against
    belongs to.


  * `family` - Operate on all git submodules within the tree of the project maven was
    invoked against that contain a maven project with the same project
    family.

  * `family-or-child-family` - Operate on all git submodules within the tree of the project
    maven was invoked against that contain a maven project with the same project
    family, or where the project family is the parent family of that project
    (e.g. the groupId is com.foo.bar, the family is "bar" and the parent
    family is "foo").

  * `same-group-id` - Operate on all git submodules within the tree of the project maven was
    invoked against that contains the same group id as the project maven was
    invoked against.

  * `all-project-families` - Operate on all of the checkouts in the project tree
    tree which contain a `pom.xml` file in their root

  * `all` - Operate on all checkouts in the project tree


### Documentation and Github Sites Integeration

Cactus uses the concept of an _assets repository_ - a place where documentation, UML
diagrams and similar can be generated to, and contains Maven Mojos to build javadoc,
[lexakai documentation](https://github.com/Telenav/lexakai) and 
[codeflowers](http://www.redotheweb.com/CodeFlower/) into one.

### Managing Versions

The versions maven plugin exists, and it is good, but it is more about managing your
_external_ dependencies.  The cactus maven plugin's versioning support is about managing
_internal_ dependencies - interdependencies _within_ your project tree.  So, say
you have a project family named `fooframework` on version `1.2.2`.  You run:

```sh
mvn -Dcactus.version.flavor.change=to-snapshot \
    com.telenav.cactus:cactus-maven-plugin:1.4.15:bump-version
```

and now all projects within it have a new version `1.2.3-SNAPSHOT`.  It will also, across
your entire project tree, find any properties named `fooframework.version` or
`fooframework.previous.version` or `fooframework.prev.version` - _and_ any properties
with the suffix `.version`, `.prev.version` or `.previous.version` named after the
artifact id of a project it is going to update, and updates those properties.

If there are superpoms providing configuration - including those that use their own
versioning scheme, and properties are modified in them, then their version is bumped
as well (based on their existing version).  Since those are changed, any projects that
use those superpoms as their parent will also get their version bumped - so, if it gets
changed, its version gets bumped, and version bumps cascade through anything that uses
them - and you always get a result that builds.

Since you may want to update either only one project family and not touch anything else's
superpoms, you can pass `-Dcactus.version.single.family=true` to suppress all changes
to poms that do not belong to that project family (meaning anything in the tree that is
not in the family will still depend on the old version) - or if you _do_ want to update
those superpoms for local development, but not to bump their versions and those of things
that depend on them, then you can pass `-Dcactus.superpom.bump.policy=ignore` and properties
will still be updated, but versions will not (this _does_ mean anyone else who pulls your
changes need to know to rebuild their superpoms to use your changes).


### What It's Good For

These mojos simplify performing git operations identically across a tree of git submodules - 
pull, checkout, branch, push or update all checkouts in a family (or all of them, period)
at once.

They also protect against common problems and inconsistencies, and go to some effort to
ensure that all operations can succeed before any changes are made.  For example:

  * The commit mojo will not let you commit changes in detached-head mode (which would
    create a commit on no branch at all, which you lose track of as soon as you change
    to a branch)
  * Switching branches will not succeed on some branches if others have local modifications
  * Feature branches are always created from the default development branch, so you cannot
    accidentally create one against some other feature branch

#### Pulling across multiple submodules

Say you want to execute a `git pull` across all submodules in the same family as the
project you're in (and optionally the submodule root):

```
mvn -Dcactus.scope=family -Dcactus.update-root=true com.telenav.cactus:cactus-maven-plugin:pull
```

#### Commiting across multiple submodules

Or you want to generate a commit across all modified projects within the same project family:

```
mvn -Dcactus.scope=family '-Dcactus.commit-message=Initial v3.x modularization' \
   -Dcactus.update-root=true com.telenav.cactus:cactus-maven-plugin:commit
```

#### Pushing across multiple submodules

And push _all_ of your local changes:

```
mvn -Dcactus.scope=all -Dcactus.update-root=true com.telenav.cactus:cactus-maven-plugin:push
```

#### Branching across multiple submodules

Or you have a fresh clone, and a bunch of submodules in "detached head" state, and you
just want to get all of them onto the default development branch to do some coding (the default
branch name is _develop_ - you can pass `-Dbase-branch=whatever` if you use something else):
```
mvn -Dcactus.scope=ALL -Dupdate-root=true -Dpermit-local-changes=true \
    com.telenav.cactus:cactus-maven-plugin:1.4.7:checkout
```

Or, say you want to work on a new feature branch named `woovlesnorks`
 - `cd` to the project directory of any project in the family you want to work on and run:

```
  mvn -Dcactus.scope=FAMILY -Dcreate-branches=true -Dupdate-root=true \
    -Dtarget-branch=feature/woovlesnorks -Dpermit-local-changes=true \
    -Dcactus.update-root=true -Dpush=true \
    com.telenav.cactus:cactus-maven-plugin:1.4.7:checkout
```

If `cactus.update-root` is true, it will also correct your `.gitmodules` to point to
the correct branches.

#### Merging a two branches and testing

ForkBuildMojo and MergeToBranchMojo can be combined (running the former on the `validate` phase
and the latter in the `package` or `install` phase) in a Maven `profile` - pass in a
branch you want to try merging and building, and it will create a temporary branch,
from a stable branch, merge the target branch into it, build, and if the build succeeds,
optionally push the changes back to the stable branch - creating a branch safe to pull from
for all developers which incorporates incremental changes from feature branches (this is
the "team repositories" concept used for years in NetBeans' development).

[//]: # (end-user-text)

# cactus maven-plugin 1.4.3 &nbsp;&nbsp; <img src="https://telenav.github.io/telenav-assets/images/icons/gears-32.png" srcset="https://telenav.github.io/telenav-assets/images/icons/gears-32-2x.png 2x"/>

This module provides maven support for Telenav Open Source projects.

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-512.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-512-2x.png 2x"/>

### Index



[**Dependencies**](#dependencies) | [**Class Diagrams**](#class-diagrams) | [**Package Diagrams**](#package-diagrams) | [**Javadoc**](#javadoc)

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-512.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-512-2x.png 2x"/>

### Dependencies <a name="dependencies"></a> &nbsp;&nbsp; <img src="https://telenav.github.io/telenav-assets/images/icons/dependencies-32.png" srcset="https://telenav.github.io/telenav-assets/images/icons/dependencies-32-2x.png 2x"/>

[*Dependency Diagram*](https://telenav.github.io/cactus-assets/1.4.3/lexakai/cactus/maven-plugin/documentation/diagrams/dependencies.svg)

#### Maven Dependency

    <dependency>
        <groupId>com.telenav.cactus</groupId>
        <artifactId>cactus-maven-plugin</artifactId>
        <version>1.4.7</version>
    </dependency>

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

[//]: # (start-user-text)



[//]: # (end-user-text)

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

### Class Diagrams <a name="class-diagrams"></a> &nbsp; &nbsp; <img src="https://telenav.github.io/telenav-assets/images/icons/diagram-40.png" srcset="https://telenav.github.io/telenav-assets/images/icons/diagram-40-2x.png 2x"/>

None

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

### Package Diagrams <a name="package-diagrams"></a> &nbsp;&nbsp; <img src="https://telenav.github.io/telenav-assets/images/icons/box-24.png" srcset="https://telenav.github.io/telenav-assets/images/icons/box-24-2x.png 2x"/>

[*com.telenav.cactus.maven*](https://telenav.github.io/cactus-assets/1.4.3/lexakai/cactus/maven-plugin/documentation/diagrams/com.telenav.cactus.maven.svg)  
[*com.telenav.cactus.maven.git*](https://telenav.github.io/cactus-assets/1.4.3/lexakai/cactus/maven-plugin/documentation/diagrams/com.telenav.cactus.maven.git.svg)  
[*com.telenav.cactus.maven.log*](https://telenav.github.io/cactus-assets/1.4.3/lexakai/cactus/maven-plugin/documentation/diagrams/com.telenav.cactus.maven.log.svg)  
[*com.telenav.cactus.maven.tree*](https://telenav.github.io/cactus-assets/1.4.3/lexakai/cactus/maven-plugin/documentation/diagrams/com.telenav.cactus.maven.tree.svg)  
[*com.telenav.cactus.maven.util*](https://telenav.github.io/cactus-assets/1.4.3/lexakai/cactus/maven-plugin/documentation/diagrams/com.telenav.cactus.maven.util.svg)

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

### Javadoc <a name="javadoc"></a> &nbsp;&nbsp; <img src="https://telenav.github.io/telenav-assets/images/icons/books-24.png" srcset="https://telenav.github.io/telenav-assets/images/icons/books-24-2x.png 2x"/>

Javadoc coverage for this project is 43.9%.  
  
&nbsp; &nbsp; <img src="https://telenav.github.io/telenav-assets/images/meters/meter-40-96.png" srcset="https://telenav.github.io/telenav-assets/images/meters/meter-40-96-2x.png 2x"/>


The following significant classes are undocumented:  

- com.telenav.cactus.maven.git  
- com.telenav.cactus.maven  
- com.telenav.cactus.maven  
- com.telenav.cactus.maven  
- com.telenav.cactus.maven.git  
- com.telenav.cactus.maven.git  
- com.telenav.cactus.maven.git  
- com.telenav.cactus.maven.git  
- com.telenav.cactus.maven.util  
- com.telenav.cactus.maven.tree  
- com.telenav.cactus.maven  
- com.telenav.cactus.maven  
- com.telenav.cactus.maven

| Class | Documentation Sections |
|---|---|
| [*Awaitable*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin////////////////////////////////////////.html) |  |  
| [*AwaitableCompletionStage*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin///////////////////////////////////////////////////////.html) |  |  
| [*AwaitableCompletionStageImpl*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin///////////////////////////////////////////////////////////.html) |  |  
| [*BaseMojo*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin//////////////////////////////////.html) |  |  
| [*BooleanProcessResultConverter*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin////////////////////////////////////////////////////////////.html) |  |  
| [*Branches*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin//////////////////////////////////////.html) |  |  
| [*Branches.Branch*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin/////////////////////////////////////////////.html) |  |  
| [*BringAssetsBranchesToHeadMojo*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin///////////////////////////////////////////////////////.html) |  |  
| [*BuildLog*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin//////////////////////////////////////.html) |  |  
| [*BuildMetadataMojo*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin///////////////////////////////////////////.html) |  |  
| [*CheckConsistencyMojo*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin//////////////////////////////////////////////.html) |  |  
| [*CleanCachesMojo*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin/////////////////////////////////////////.html) |  |  
| [*CleanCachesMojo.CacheFindingStrategy*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin//////////////////////////////////////////////////////////////.html) |  |  
| [*CliCommand*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin/////////////////////////////////////////.html) |  |  
| [*CliCommand.SimpleCommand*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin///////////////////////////////////////////////////////.html) |  |  
| [*CommitMojo*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin////////////////////////////////////.html) |  |  
| [*ConsistencyChecker*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin/////////////////////////////////////////////////.html) |  |  
| [*DevelopmentPrepMojo*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin/////////////////////////////////////////////.html) |  |  
| [*GitCheckout*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin/////////////////////////////////////////.html) |  |  
| [*GitCommand*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin////////////////////////////////////////.html) |  |  
| [*GitRemotes*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin////////////////////////////////////////.html) |  |  
| [*Heads*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin///////////////////////////////////.html) |  |  
| [*Heads.Head*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin////////////////////////////////////////.html) |  |  
| [*Inconsistency*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin////////////////////////////////////////////.html) |  |  
| [*Inconsistency.Kind*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin/////////////////////////////////////////////////.html) |  |  
| [*LexakaiMojo*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin/////////////////////////////////////.html) |  |  
| [*NeedPushResult*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin////////////////////////////////////////////.html) |  |  
| [*PathUtils*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin////////////////////////////////////////.html) |  |  
| [*PathUtils.FileKind*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin/////////////////////////////////////////////////.html) |  |  
| [*ProcessFailedException*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin/////////////////////////////////////////////////////.html) |  |  
| [*ProcessResultConverter*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin/////////////////////////////////////////////////////.html) |  |  
| [*ProjectTree*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin//////////////////////////////////////////.html) |  |  
| [*ProjectTree.Cache*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin////////////////////////////////////////////////.html) |  |  
| [*PullMojo*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin//////////////////////////////////.html) |  |  
| [*PushMojo*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin//////////////////////////////////.html) |  |  
| [*RepoSet*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin/////////////////////////////////////.html) |  |  
| [*Scope*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin///////////////////////////////.html) |  |  
| [*StringProcessResultConverter*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin///////////////////////////////////////////////////////////.html) |  |  
| [*StringProcessResultConverterImpl*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin///////////////////////////////////////////////////////////////.html) |  |  
| [*StringProcessResultConverterImpl.OutputReader*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin////////////////////////////////////////////////////////////////////////////.html) |  |  
| [*SubmoduleStatus*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin/////////////////////////////////////////////.html) |  |  
| [*SubmodulesRepoSet*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin///////////////////////////////////////////////.html) |  |  
| [*TestMojo*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin//////////////////////////////////.html) |  |  
| [*ThrowingOptional*](https://telenav.github.io/cactus-assets/1.4.3/javadoc/cactus/cactus.maven.plugin///////////////////////////////////////////////.html) |  |  

[//]: # (start-user-text)



[//]: # (end-user-text)

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-512.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-512-2x.png 2x"/>

<sub>Copyright &#169; 2011-2021 [Telenav](https://telenav.com), Inc. Distributed under [Apache License, Version 2.0](LICENSE)</sub>  
<sub>This documentation was generated by [Lexakai](https://lexakai.org). UML diagrams courtesy of [PlantUML](https://plantuml.com).</sub>
