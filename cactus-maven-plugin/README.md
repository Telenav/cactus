[//]: # (start-user-text)

# Cactus Maven Plugin

This project contains a Maven plugin that contains a set of Maven mojos specifically 
designed for working in environments where multiple trees of projects are joined 
together with a root bill-of-materials POM using Git submodules. You can think of 
this development style as an extension to git-flow for large, multi-module sets 
of related projects.

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

Say you want to execute a `git pull` across all submodules in the same family as the
project you're in (and optionally the submodule root):

```
mvn -Dcactus.scope=FAMILY -Dcactus.update-root=true com.telenav.cactus:cactus-maven-plugin:pull
```

Or you want to generate a commit across all modified projects within the same project family:

```
mvn -Dcactus.scope=FAMILY '-Dcactus.commit-message=Initial v3.x modularization' \
   -Dcactus.update-root=true com.telenav.cactus:cactus-maven-plugin:commit
```

And push _all_ of your local changes:

```
mvn -Dcactus.scope=ALL -Dcactus.update-root=true com.telenav.cactus:cactus-maven-plugin:push
```

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
