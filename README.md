[//]: # (start-user-text)

<a href="https://www.kivakit.org">
<img src="https://telenav.github.io/telenav-assets/images/icons/web-32.png" srcset="https://telenav.github.io/telenav-assets/images/icons/web-32-2x.png 2x"/>
</a>&nbsp;
<a href="https://twitter.com/openkivakit">
<img src="https://telenav.github.io/telenav-assets/images/logos/twitter/twitter-32.png" srcset="https://telenav.github.io/telenav-assets/images/logos/twitter/twitter-32-2x.png 2x"/>
</a>
&nbsp;
<a href="https://kivakit.zulipchat.com">
<img src="https://telenav.github.io/telenav-assets/images/logos/zulip/zulip-32.png" srcset="https://telenav.github.io/telenav-assets/images/logos/zulip/zulip-32-2x.png 2x"/>
</a>

<p></p>

<img src="https://telenav.github.io/telenav-assets/images/backgrounds/kivakit-background.png" srcset="https://telenav.github.io/telenav-assets/images/backgrounds/kivakit-background-2x.png 2x"/>

[//]: # (end-user-text)

# cactus 1.5.19 &nbsp;&nbsp; <img src="https://telenav.github.io/telenav-assets/images/logos/kivakit/kivakit-64.png" srcset="https://telenav.github.io/telenav-assets/images/logos/kivakit/kivakit-64-2x.png 2x"/>
 
Tools for building projects in Git submodules with Maven

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-512.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-512-2x.png 2x"/>

[//]: # (start-user-text)

This repository contains the `cactus-maven-plugin` and related libraries, for building, developing,
maintaining and releasing trees of projects that are managed using Git submodules and built with Maven.

<a name = "quick-start"></a>
### Quick Start &nbsp; <img src="https://telenav.github.io/telenav-assets/images/icons/rocket-32.png" srcset="https://telenav.github.io/telenav-assets/images/icons/rocket-32-2x.png 2x"/>

[**Cactus Quick Start**](#quick-start)  
[**Cactus Scripts**](#cactus-scripts)  
[**Release Script for Telenav Open Source**](https://github.com/Telenav/telenav-build/blob/release/current/documentation/releasing.md)

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

### Build Status <a name = "quick-start"></a>&nbsp; <img src="https://telenav.github.io/telenav-assets/images/icons/gears-32.png" srcset="https://telenav.github.io/telenav-assets/images/icons/gears-32-2x.png 2x"/>

| Repository and Branch       | Build Status                                                                                    |
|-----------------------------|-------------------------------------------------------------------------------------------------|
| `cactus develop`            | <img src="https://github.com/Telenav/cactus/actions/workflows/build-develop.yml/badge.svg"/>    |
| `cactus release/current`    | <img src="https://github.com/Telenav/cactus/actions/workflows/build-release.yml/badge.svg"/>    |

<br/>

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

### Background
 
[**Problem Definition**](#problem-definition)  
[**Maven**](#maven)  
[**Maven Limitations**](#maven-limitations)  

### Cactus

[**About Cactus**](#about-cactus)  
 * [**Project Families**](#project-families)  
 * [**Managing Versions**](#managing-versions)  
 * [**Version Property Patterns**](#property-patterns)  
 * [**Bumping Versions**](#bumping)  

### Maven

[**Cactus and Maven**](#cactus-and-maven)  
 * [**Documentation**](#documentation)  
 * [**Releasing**](#releasing)  
 * [**Telenav Open Source Releases**](#telenav-releases)  
 * [**Release Phases**](#release-phases)  
   * [**Release Phase 0 - Cloning**](#phase-0)  
   * [**Release Phase 1 - Bumping Versions**](#phase-1)  
   * [**Release Phase 2 - Publishing Documentation**](#phase-2)  
   * [**Release Phase 3 - Committing**](#phase-3)  
   * [**Release Phase 4 - Publishing**](#phase-4)  

### Appendices

[**Cactus Mojos**](#cactus-mojos)
 * [**filter-families**](#filter-families)  
 * [**filter-published**](#filter-published)  

[**Cactus Scripts**](#cactus-scripts)  

[**Road Map**](#road-map)

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "quick-start"></a>
## Cactus Quick Start  &nbsp; <img src="https://telenav.github.io/telenav-assets/images/icons/rocket-32.png" srcset="https://telenav.github.io/telenav-assets/images/icons/rocket-32-2x.png 2x"/>

The `cactus-maven-plugin` lets us perform tasks against *sets of git repositories* in a tree of
projects managed using [git submodules](https://git-scm.com/book/en/v2/Git-Tools-Submodules), as
if they were hosted in a single git repository.  Cactus tools use a concept of _project families_.
Project family names are derived from the Maven `groupId` shared by the projects in the family. For 
example, `kivakit`, `kivakit-extensions` and `kivakit-stuff` all belong to the project family 
`kivakit` (from `com.telenav.kivakit`). Project families can be used to specify which repositories 
to operate on. Tools can be told to operate on all families, one specific family, or some subset of 
families. For example, `all`, `kivakit`, or `kivakit,mesakit`.

In daily development, what these tools primarily do is ensure consistency and ensure that it is
impossible to, say, commit in one repository and forget about changes in another, or push the root
but fail to push submodules, which would result a broken checkout for anyone pulling.  So, Cactus 
handles cases like branching all checkouts containing a project family, or committing all of them, 
getting them all on the same branch, and so forth.

Invoking a maven plugin individually is somewhat verbose, so a mojo is included which will install
[scripts](#cactus-scripts) that take care of several daily-development problems that come up.
To install the scripts, we simply:

  * Put a `~/bin` folder or `~/.local/bin` folder on our `PATH` (if it is preferred to put the scripts 
    somewhere else, we can pass `-Dcactus.script.destination=[folder]` but `[folder]` must be on
    our `PATH`)
  * Run `mvn com.telenav.cactus:cactus-maven-plugin:install-scripts`

> More detailed control can always be had by invoking [Maven mojos](#orchestrating) directly, passing property arguments with -D

Scripts will be installed there, with easily discovered, verbose names starting with `cactus-` 
and sym-linked to shorter named aliases which do not conflict with any unix command.  Thereafter, 
simply run `cactus-update-scripts` to update them.

The set of scripts and their descriptions - which will also be printed out when we install or
update them - is included [at the bottom of this document](#cactus-scripts).

The Cactus plugin also includes tooling for updating versions across multiple projects, generating 
Lexakai documentation for use with Github Pages and doing full-blown releases while automating the 
most labor-intensive parts of branching and versioning.

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "problem-definition"></a>
## Problem Definition  
 
Say we have a bunch of sets of libraries, and we build applications with them - but not just _one_ application.
These libraries are also Open Source and should be buildable in isolation by a contributor only interested in
working on a single library. When we release them, we need to ensure that they all build and work together.

We want new developers to have easy ramp-up - just check out one thing from Git and they've got
everything they need, both to build and to get oriented within the codebase.

Git submodules are a great solution for managing a situation like this - we can create a git repository
that _contains no code itself_, just submodules that contain all of the libraries someone needs to be 
productive.  That root git repository just contains a build script (in the case of Maven, a [_bill of 
materials_](https://reflectoring.io/maven-bom/) `pom.xml`) that says what to build, and perhaps a 
script or two to get all of the submodules fully "hydrated" and built after a fresh clone.

Using submodules, we can create _multiple repositories_ for different libraries or applications.

A git checkout with submodules - what we will call a _workspace_ for the rest of this document - 
works like this:

  * When we clone it, it contains a `.gitmodules` file (and other metadata under `.git/`) that describes 
    an ad-hoc set of other git repositories to clone and pull from
  * The first time we clone a workspace, we need to run `git submodule init` to set up the
    submodules, create directories for them in our work tree - after this, we have folders for
    each checkout, and git metadata, but they are still in a sort of _dehydrated_ state - nothing
    is in them
  * After we run `git submodule init` the first time, we run `git submodule update` to actually
    populate it
  * The workspace lists _the specific commit_ in each repository that was last pushed to it - so it is
    not just a list of things to clone, but a record of the _state_ of those repositories, so
    that anyone cloning it can reproduce the exact set of bits it pointed to when it was pushed
  * A workspace can be branched and tagged, just like any other repository - and each branch
    or tag specifies its own set of commits for its submodules. Suppose we want to locally reproduce
    the bits for release 1.10.12?  Just checkout the `release/1.10.12` branch of the workspace, and
    `git submodule update` all of the child checkouts, and voila, we have release 1.10.12.
  * The `.gitmodules` file can optionally list specific _git branches_ that it expects child
    checkouts to be on

Git submodules are a great tool for managing large trees of projects, building them together,
and giving developers (and continuous build tools) a _batteries included_ way to get set up with
everything they need to be productive quickly.

But git submodules do create a few "[impedance mismatches](https://en.wikipedia.org/wiki/Impedance_matching)"
and it's helpful to have tooling to resolve those problems and make development as transparent and
straightforward as possible:

  1. A workspace points to a specific commit.  If we're doing ongoing development, we probably want
     to be at the head of a development branch, not on whatever commit the workspace pointed to the
     last time someone pushed to _that_.  So, a tool or script for the task of _get me ready to do development
     on branch x_ that brings everything up to date is helpful (the `cactus-development-prep` script is for that)
  2. If we're doing development that touches multiple sub-checkouts, it is easy to commit and push our
     changes in one, but forget to do it in another.  What we want is a tool that we can say
     _commit my changes in all of the submodules, using this message_ and have it simply figure out what
     needs committing and do it (the `cactus-commit-all-submodules` or `ccm` script is for that).
     The same goes for pushing.
  3. When we commit or push, we usually also want to update the workspace to point to our
     new commits, and if that requires remembering to manually run `git add -A && git ci -m Whatever && git push`
     in the root, it is easy to forget.  So, we want our tooling to do that automatically.
  4. When we branch - say, for a feature or release - we are likely to want to branch _everything_
     that may be touched in that work, not just one submodule.  _And_ we don't want the main 
     development branch of the workspace 
     to point to commits on our branch until our work is finished.  So we need a way to
     branch across the workspace _and multiple child repositories_ in one shot - and that tool
     should detect which child repositories do and don't need branching (see discussion of _project
     families_ in the Maven section for how we do that).
  5. Similarly, when we merge, say, a feature or release branch back to the development branch, we
     want to merge _everything affected_, without having to remember all the child checkouts that need
     merging or possibly miss one.
  6. Cloning and rehydrating a workspace and its children may leave them in [_detached head state_](https://www.git-tower.com/learn/git/faq/detached-head-when-checkout-commit/),
     not on any branch at all.  This is "right thing" when we want to reproduce a build or multi-repository
     state precisely, but not the right thing at all when we are about to do some coding.  The
     `cactus-development-prep` script solves this case as well.

So, these, along with some additional issues, are the problems Cactus development tools sets out to 
solve - to make it easy to work against a tree of git submodules as if it were just a single git
repository, and make it difficult-to-impossible to break someone else's work when doing so.

In building these tools, an important goal is that the results be _portable_ to different projects, 
different project layouts on disk, and so forth. If we have a git submodule that can only be built 
when cloned into the exact right directory of some other checkout, then we might as well have put 
it all in one git repository to begin with - it defeats the purpose.

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "maven"></a>
## General Maven Practices 

[Apache Maven](https://maven.apache.org/) comes with its own pros and cons and problems, and, in 
complex project trees, requires some discipline to use effectively.  A few practices can be helpful:

  * Distinguish [_bill-of-materials_](https://reflectoring.io/maven-bom/) POMs (known as BOMs) from shared configuration POMs:
    * A `pom.xml` can say it has a _parent_ that it inherits from
      * This is shared configuration - sets of dependencies, build and plugin settings, common metadata
    * A `pom.xml` can _also_ contain a `<modules>` section, listing some directories containing other
      Maven projects to build. This is a _bill-of-materials_ - just a list of things we want to tell 
      Maven to build. It has nothing to say about _how_ those things get built.
    * We should avoid mixing these two things unless we have a very shallow subtree of projects. Describing
      what to do is fundamentally different than describing _how to do it_.
    * By default, unless we spell it out, declaring a parent in our `pom.xml` 
      _implicitly creates an element `<relativePath>../pom.xml</relativePath>`_.  If that points
      outside the project's git submodule, the result is a git repository that can only be built
      if it happens to in the right place on the disk of the person who checked it out.
  * Avoid deep hierarchies
    * Each parent a `pom.xml` has is a _place for things to go wrong_ - and one more place we have
      to look when they do.  So if we have a project tree like
      `libfamily/myfamily-filesystems/remote-filesystems/nfs/super-nfs-impl` and `super-nfs-impl`
      parents off a `pom.xml` it its parent directory, and that does the same, all the way down
      to the root, then, say, someone makes a change that breaks compilation but only for _that
      one thing_ - every one of those `pom.xml` is something we have to examine.  It is far
      more debuggable to have child libraries parent off of _one_ `pom.xml` that is the only place
      where shared configuration could possibly have changed.
  * Manage _sets of_ dependencies through imported dependencies, not inheritance, where possible.
    * In a _shared configuration_ `pom.xml` (one used as `<parent>` by others), we can include
      the entire `<dependencyManagement>` section of another `pom.xml` simply by including it as
      a `<dependency>` element in our own `<dependencyManagement>` section.  This
      [composition rather than inheritance](https://betterprogramming.pub/inheritance-vs-composition-2fa0cdd2f939)
      approach allows related dependencies to be spelled out in a single place, and updated en-masse
      rather than manually, one-at-a-time.
  * Keep superpoms - _shared configuration_ - in their own separate hierarchy with their own versioning
    * Due to limitations of Maven (see below), we have to explicitly, separately build a superpom
      before we can build anything that uses it as a parent (unless it can get it from Maven central or 
      use `<relativePath>`, which it *must not do* if the parent lives in a different git submodule) - even though Maven
      is about to build it in the same [reactor](https://stackoverflow.com/questions/2050241/what-is-the-reactor-in-maven),
      it will refuse to load the poms that parent off of it.  So, we might as well have these
      in a separate Git submodule with a bill-of-materials and build all of them once, at the start
      of our build process, rather than needing an explicit, _first-build-the-superpom_ step
      for every single project family (Maven 4 _may_ improve this somewhat, but from our testing at present, not
      quite enough to git rid of this advice)
      * This means that building superpoms must (see above) be done separately, but this is only a problem in a
        _cold start_ situation (fresh clone, or empty local Maven repository, or both) or after a change, and generally
        only a problem then when using unpublished versions.
  * Keep folder names and artifact ids consistent, at least at the top level
    * There is nothing to stop us from creating a bill-of-materials that says to build the
      Maven project in a folder named `foo`, and having `foo/pom.xml` have the artifact id
      `bar`.  `<module>` declarations are nearly the only place where Maven (sadly) relies on the
      names of folders on disk.  Keeping them consistent or at least suffix-consistent will
      avoid a lot of confusions.
  * Use the maven-enforcer-plugin or similar to ensure conflicting dependency versions are detected early
  * Use Maven _properties_ to manage versions of dependencies, except in trivial cases
  * *Always* use properties to manage versions of inter-project dependencies
  * Inherit or import dependency versions from superpoms' `<dependencyManagement>` sections - don't
    have every project hard-code versions of things
  * Avoid redundant or superfluous configuration
    * If a project has the same `groupId` as the parent it names, it should inherit it, not declare it again
    * If a project has the same `version` as the parent it names, it should inherit it, not declare it again
    * Do not declare things where the default is the same as what is declared (ex.: `<packaging>jar</packaging>` or
      `<type>jar</type>` in dependencies)
  * Avoid re-declaring inherited items - for example, `<developers>` should only be declared in a child
    project if it is _changing_ the value from its parent - otherwise this sort of thing is just noise.
    You can always ask Maven to print out the _effective pom_ using `mvn -Dverbose=true help:effective-pom`
    to see what we're inheriting and where it comes from.

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "maven-limitations"></a>
### Maven Limitations

Being the [best of a flawed set of available tools](https://timboudreau.com/blog/maven), Maven
has some somewhat arbitrary limitations - most of which stem from its designers' naiveté about
how many distinct graphs-of-things are involved in building software.  Some can be worked around,
some are improved in (not yet released) Maven 4; some must be lived with:

  1. As mentioned above, if we have a bill-of-materials that wants to build some projects, and
     one of those projects has a superpom that has not been built locally, it will fail _even
     though Maven not only can see `pom.xml`, but is in fact about to build it and has all the information
     it needs to supply a parent to the others_.
  2. Circular test dependencies could work within Maven's model of the universe if it understood
     that a jar or classes folder is the root of a graph of things needed to build it, and that
     tests are actually a completely different graph of stuff that just happens to be described
     in the same `pom.xml` file.  Alas, it does not understand that.

    * In particular, this induces some pain when using the Java Module System, which does not
      play nicely with unit tests to begin with. Frequently this means, if we want to share some
      test logic, being unable to use the standard `test-jar` to share that logic, and instead,
      needing to create a separate project for tests that exports the shared logic in its main
      sources, and contains the unit tests that belong with the original project in _its_ test
      sources (because a test dependency from there to our shared logic would create a 
      circularity)
  3. The pros and cons of `-SNAPSHOT` versions.  Maven's altered behavior when it encounters the
     magic string `-SNAPSHOT` at the end of a version is likely responsible for most of the
     Maven hatred and loathing out there in the world - it is the reason for what are known as
     _download the internet builds_ (for real fun, try it through the great firewall of China
     to turn what should be a 2 minute build into one that takes 7 hours!).  On the one hand,
     using a suffix like `-dev` can be an effective substitute to avoid having our build tool
     behave differently.  On the other hand, when dealing with public repositories such as Maven
     Central, the fact that `-SNAPSHOT` is recognized and treated specially provides added protection
     against accidentally releasing code that's not ready for prime-time to the world.  Currently,
     we hold our nose and use it, but we may not continue doing so, and an update to these tools
     may support using `-dev` as an alternative.
  4. You will notice that you _can_ build your superpoms as part of a build that also builds projects
     that use them as a parent, _if you have already built them once into your local Maven repository.
     *BUT* what you are actually building against - using as a parent in those projects - is the
     old version from your `~/.m2/repository` directory, not the ones being built.  While it is
     rare for this to be a problem, it is also non-obvious what is wrong when it is.  When in doubt,
     just manually build your superpoms if you think anything in them has changed, to avoid surprises.

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "about-cactus"></a>
## About Cactus 

Cactus codifies some development practices that originated in [Apache Wicket](https://wicket.apache.org/)
and proved valuable - specifically, having [_rings of stability_](https://medium.com/@jonathanlocke/open-source-repository-structure-c1050d5840c6) 
that set expectations for users.  In Apache Wicket, `wicket` is a single _project family_, consisting of
`wicket`, `wicket-extensions`, `wicket-examples`, and `wicket-stuff`, in order of stability. Projects in these
rings migrate towards the core if they become more stable, and away from the core if they become unmaintained.

Cactus is built around this idea and so it supports families of projects that depend on each other. 
For example, the [KivaKit](https://www.kivakit.org) project family is built by Cactus, and it is structured 
in a similar way to Apache Wicket:

  * `kivakit` - the core library (most stable)
  * `kivakit-extensions` - contributed or added libraries built on `kivakit` (stable)
  * `kivakit-stuff` - libraries built on `kivakit` and/or `kivakit-extensions` (experimental)
  * `kivakit-examples` - sample code and applications

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "project-families"></a>
### Project Families 

Cactus Maven tooling groups things by _project family_ - a string derived from the text after the
final `.` character in its Maven [groupId](https://maven.apache.org/guides/mini/guide-naming-conventions.html)
with any `-`-delimited tail omitted.  So, if our `groupId` in our pom is `org.foo.snorkel-things`,
then our _project family_ is `snorkel`.

So, in the above case, `kivakit`, `kivakit-extensions`, `kivakit-stuff` and `kivakit-examples` are
separate git submodules, each buildable on its own for contributors or someone doing a quick fix.
Since all of them contain Maven projects using a `groupId` ending in `.kivakit`, when a developer 
asks the Cactus tooling to do something to all repositories in the family _kivakit_, it will find any 
git submodules containing KivaKit projects in the workspace and do whatever is needed.

So, for ongoing, intensive development, where it is important to quickly know if our change in, say, `kivakit` 
broke something in `kivakit-extensions`, `kivakit-stuff` or `kivakit-examples`, we know that quickly.

That is important because many of the [Mojos](https://maven.apache.org/developers/mojo-api-specification.html)
in the Cactus Maven Plugin perform git operations, and they decide which git repositories to operate
on based on the set of _project families_ expressed in all of the `pom.xml` files in each git
submodule.

Implicit in all of this is that projects are versioned _by family_, and all projects _within a family_
generally should have the same version.  That said, superpoms may have
completely different versions than the family(ies) they govern. And projects may have versions
that diverge intentionally (as in `lexakai` and `lexakai-annotations`).  In a mixed-version scenario, 
the most prevalent version wins. In the case that it is a choice between two equally matched versions, 
the version of the project whose `artifactId` is closest (by [levenshtein distance](https://en.wikipedia.org/wiki/Levenshtein_distance))
to the name of the family wins.

> NOTE: When using Cactus, we should try to avoid manually meddling with the versions of projects.
> This will help to ensure that we don't end up in a state where they are inconsistent. 

Cactus tooling assumes the following:

  * That it is operating in a tree of Maven projects
  * That they are (usually) part of a less granular tree of Git submodules
  * That the _families_ in a given checkout can be derived from the set of `<groupId>` elements of
    all `pom.xml` files within that checkout, using the algorithm described above
  * That any operation that is scoped to a family or set of families can use that information
    to decide what Git submodules should and should not be acted on

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "managing-versions"></a>
### Managing Versions 

Versioning software is a hard problem, to say the least.  A version number, name or identifier for a library
is a _human-created, fallible name_ which might or might not indicate something has actually changed, and
might or might not set expectations for consumers of it about how compatible or incompatible the
changes are.

And, as an industry, we then expect those version strings to be _machine-readable_ and _machine-sortable_.

Needless to say, this can and routinely does fail.

What we _can_ do - and what Cactus does - is remove as much of the pain and possibility for error from
the process as possible, to automate changing versions, and to ensure that anything that has changed
gets its version updated.

In order to do that, Cactus makes a few assumptions:

  * That version numbers for projects developed using it are (at least) 3-digit dewey-decimal,
    with an optional `-` separated suffix like Maven's magic `-SNAPSHOT` suffix - roughly 
    [semantic versioning](https://semver.org/) compatible
  * That all pom files for _Java projects_ that are members of a given _project family_ will use
    the same version string, and should be managed by that
  * That superpoms (_shared configuration POMs_) may have their own versioning scheme (still 3-digit
    as described above, but not necessarily the same string as code-containing POMs use, since these
    are likely to change less often)
  * Bill-of-materials POMs (BOMs) also may have their own versions - since these are useless as dependencies
    for anything and need not be published, their version is largely irrelevant, but since it is
    difficult to avoid publishing them to Maven central using the Nexus plugin, their versions will
    be bumped if they have been published before when automating version changes
  * That versions of things that may need their versions bumped are managed with Maven _properties_,
    and changing a property will change a dependency's version

Cactus' `bump-version` Mojo is the heart of version management here, and goes to great lengths to
_guarantee_ that versioning is accurate and reflects the actual changes we are trying to push or
publish.  Specifically:

  * Superpoms are checked against versions published on Maven central.  If they exist
    on Maven central and are not identical, then the version of the superpom is bumped.
  * If a superpom's version is changed, then that change will cascade through every POM that references
    it as a parent, if necessary, causing the versions of those POM files (if they declare their
    own version) to change too

This makes impossible such scenarios as:

  * I change some dependencies in `foo-superpom` to different versions, and then publish `foo` - my
    colleague can't build it because she has a different `foo-superpom` than I do, and in fact the
    set of dependencies for anyone getting it from Maven central will not be the same as what I think
    they are.

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "property-patterns"></a>
### Version Property Patterns 

Cactus will recognize properties with the suffixes `.version`, `.prev.version`, and `.previous.version`
as being _version indicating properties_, and will update them appropriately if the portion of the
property name preceding the suffix is the name of a project family _or the `artifactId` of a specific project_
underneath the workspace it is building.

In the case of an artifact id, the prefix may be the artifact id verbatim, or may substitute `.` characters
for `-` characters and it will be identified and mapped to the
referenced project.  So, `cactus.version`, `cactus.maven.plugin.version`, `cactus-maven-plugin.version`
would all be recognized and updated correctly if we were bumping the version of the Cactus family in
a tree containing it.

Versions that identify _previous_ versions of artifacts are important for cases where some project is
used as part of the build process _itself_, and the previous release must be used on some or all
projects to avoid creating a circular dependency Maven would reject.  In `telenav-build`, Cactus
and Lexakai are both examples of this phenomenon - the cactus plugin cannot be used to generate
metadata for itself while it is being compiled, but the previous release can be.

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "bumping"></a>
### Bumping Versions 

In general, for reasons described above, editing versions by hand is strongly discouraged - it is
easy to underestimate the scope of things that need updating as a consequence, while that is exactly
the sort of task computers excel at.

There are two aspects to a version - its dewey-decimal portion - the leading group of `.`-delimited
numbers - and its suffix, or _flavor_.  The `bump-version` mojo can change one or the other or both.
A change to the decimal portion is defined by the _magnitude_ property - the
 `-Dcactus.version.change.magnitude=` property with a value of `none`, `dot`, `minor` or `major` - and
the `-Dcactus.version.flavor.change=` which can be `unchanged`, `to-release` or `to-snapshot`.
More granular properties for applying different decimal changes to different project families are
described in detail in the release-profile-3 section.

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "cactus-and-maven"></a>
### Cactus and Maven 

Maven has a set of predefined [_lifecycle stages_, also known as "phases"](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html) -
`validate`, `compile`, `test`, `package`, `verify`, `install`, `deploy`, plus a number of pre- and post- phases
not usually used from the command-line.  These are hard-coded into Maven's API - a plugin cannot
invent its own.

Operations the cactus-maven-plugin performs don't fit easily into any one of the buckets that
Maven offers - is performing a git push a part of compilation?  Of testing?  Of deploying or packaging?
Nonetheless, each Maven mojo (the unit of work that can be invoked from the command-line) has to
specify _something_ as a default.

In general, the approach taken with the Cactus maven plugin is to treat Maven's phases as arbitrary
buckets to hang work off of, with an eye to _ensuring whatever a given Mojo does is placed in front
of any task that might need the consequences of that work_.

Most of the mojos run in the `validate` phase - the second phase, which is part of any Maven invocation.
Those that perform git operations - which effectively run against git repositories, but happen to get
invoked against some project or other - run either on the first project encountered or the last, and
are otherwise skipped (if we were doing a git `pull` operation, and happened to invoke it against
the workspace project, we would not want to run `git pull` once for every project times the
number of git submodules plus one!).

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "documentation"></a>
### Documentation 

The `cactus-maven-plugin` includes Mojos for building Javadoc, and [Lexakai](https://www.lexakai.org) documentation.
Lexakai is a tool for maintaining documentation indexes, documentation coverage, and UML diagrams (both
automatic, and curated). Lexakai updates sections of `README.md` files (such as this one) that are 
visible on Github:

<a href="https://www.lexakai.org/images/kivakit-1.png">
<img title="kivakit (page 1)" src="https://www.lexakai.org/images/kivakit-1.png" width="100" height="238" alt="kivakit (page 1)">
</a>&nbsp;&nbsp;&nbsp;&nbsp;
<a title="kivakit (page 2)" href="https://www.lexakai.org/images/kivakit-2.png">
<img src="https://www.lexakai.org/images/kivakit-2.png" width="100" height="238" alt="kivakit (page 2)">
</a>&nbsp;&nbsp;&nbsp;&nbsp;
<a title="kivakit-application (page 1)" href="https://www.lexakai.org/images/kivakit-application-1.png">
<img src="https://www.lexakai.org/images/kivakit-application-1.png" width="100" height="238" alt="kivakit-application (page 1)">
</a>&nbsp;&nbsp;&nbsp;&nbsp;
<a title="kivakit-application (page 2)" href="https://www.lexakai.org/images/kivakit-application-2.png">
<img src="https://www.lexakai.org/images/kivakit-application-2.png" width="100" height="238" alt="kivakit-application (page 2)">
</a>


The UML and Javadoc referenced by the automatically-maintained `README.md` indexes are written to 
content-only repositories (which end in '-assets' by convention). This content can be published via 
Github Pages, or using some other content system. For details see [http://www.lexakai.org](http://www.lexakai.org).

The branches of cross-repository documentation links are updated by the `replace` Mojo.

The cactus plugin includes some special treatment of assets repositories, which typically have
a single branch (by default, named `publish`). Mojos which operate on git repositories will,
by default, ignore assets repositories except when run with `-Dcactus.scope=all`.

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "caveats"></a>
### Caveats 

There are some operations that simply *require* more than once Maven invocation - there is no way
to update the version of a bunch of projects, and then build them in the same Maven process - Maven
has already loaded the `pom.xml` files for what is being built in-memory, and it will not detect
that the versions of some of them have changed.

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "releasing"></a>
## Releasing 

Doing a release, especially of many projects, tends to involve a predictable set of steps - roughly:

  * Ensure that all projects are on their `develop` branches
  * Ensure there are no outstanding changes
  * Ensure that the projects build without test failures
  * Make a clean clone of the root superpom and its contents somewhere, and set up `MAVEN_OPTS` to
    use a temporary repository instead of `$HOME/.m2/repository`, to guarantee that what is being published 
    can really be built from nothing but an internet connection and installs of Git, Maven and Java
  * Bump the versions of everything that will be published (likely removing `-SNAPSHOT`)
  * Create a release branch
  * Ensure that the result builds
  * Generate / update / publish any external documentation
  * Build the projects, along with javadoc, source jars and any other artifacts to publish to a Maven repository
  * Sign and publish the artifacts to a Maven repository
  * Push the changes to one or more release branches (we use both `release/n.n.n` and `release/current`)
  * Bump project versions to a new snapshot version
  * Merge the result back to a development branch

The Cactus plugin contains a number of Mojos that perform these tasks. Their functions are detailed in 
the Mojo appendix.  The most up-to-date documentation can be obtained simply by running

```sh
mvn com.telenav.cactus:cactus-maven-plugin:help
```

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "release-phases"></a>
## Release Phases

Here is the set of profiles we're using for releases of `cactus`, `kivakit`, `lexakai` and `mesakit` at
Telenav - consider them a work-in-progress, not the final word on the "Official Right Way" to do this - this
is a fairly new project, and subject to change.

<a name = "telenav-releases"></a>
### Telenav Open Source Releases

The telenav-build workspace contains a turn-key script called `release` which orchestrates the 
phases described here to make it easy to release the Telenav Open Source project families. The script:

 - Checks tool versions
 - Determines the cactus plugin version to use
 - Configures maven to use a temporary repository
 - Prompts for the project families and how to alter their versions
 - Installs superpoms
 - Removes project caches
 - Executes the release phases below
 - Pauses to allow documentation review

```
cd telenav-build
./release
```

For full details on the release script, see [telenav-build/releasing](https://github.com/Telenav/telenav-build/blob/release/current/documentation/releasing.md)

All release phases expect to be invoked with `-Dcactus.families=` set to the list of project
families being released.  In our case, since our checkout contains cactus itself, we explicitly
pass the cactus version.

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "phase-0"></a>
### Release Phase 0 - Check Local Checkout Consistency, Clone into Temporary Workspace

```xml
<plugin>
    <groupId>com.telenav.cactus</groupId>
    <artifactId>cactus-maven-plugin</artifactId>
    <version>${cactus.maven.plugin.version}</version>
    <configuration>
        <scope>family</scope>
        <verbose>true</verbose>
        <includeRoot>true</includeRoot>
        <tolerateVersionInconsistenciesIn>lexakai</tolerateVersionInconsistenciesIn>
    </configuration>
```

In our case, our workspace contains two projects that do not follow the ordinary
project-family layout - `lexakai-annotations` and `lexakai` are part of the same family,
but are versioned independently, and each is in a separate git submodule - 
so `</tolerateVersionInconsistenciesIn>` simply tells the consistency check not to 
fail when it sees that conflicting versions.

```xml
    <executions>
        <execution>
            <id>filter-families-from-plugins-1</id>
            <goals>
                <goal>filter-families</goal>
            </goals>
            <configuration>
                <familiesRequired>true</familiesRequired>
```

Here, the `<familiesRequired>` tag tells the filter-families plugin to fail the
build if the set of families being released is not _explicitly_ specified - it should
not implicitly take the family from whatever project it was invoked against.

```xml
                <properties>
                    cactus.generate.lexakai.skip,
                    cactus.publish.check.skip,
                    maven.javadoc.skip
                </properties>
            </configuration>
        </execution>
        <execution>
            <id>consistency-check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <checkRemoteModifications>false</checkRemoteModifications>
            </configuration>
```

The consistency check performs a number of checks of the project tree (all disable-able)
to ensure that what is there is suitable for release, including checking

  * That there are no remote modifications that have not been pulled
  * That there are no submodules that contain more than one project family,
    where none is used as a superpom (this indicates that either a new project
    was misplaced or has a typo in its `groupId`)
  * That no intermediate bill-of-materials POM files are declared as the `<parent>` of
    any project (many IDEs will configure a newly created project this way, and it
    means that that project will not share plugin and dependency configuration with
    the rest of its family)
  * That no checkout containing projects to be released or built is in the _detached head_ git state
  * That all checkouts containing non-superpom projects are on a branch with the same name
    (otherwise, we might be releasing a mix of a feature branch for one thing and other branches
    for others - almost never what we want)
  * That there are no poms that declare a parent where `<relativePath>` implicitly or explicitly
    points outside the git submodule they live in (this would result in a project that could
    not be built when checked out on its own, which defeats the purpose of using git submodules
    as a way to _manage_ sets of projects, not dictate what folder layout a developer uses on
    disk)
  * That there are no uncommitted changes in the checkout
  * That the _version flavor_ or suffix is consistent (we are not releasing a mix of
    release versions of some things and `-SNAPSHOT` versions of others)


```xml
        </execution>
        <execution>
            <id>clone-into-temp</id>
            <goals>
                <goal>clone</goal>
            </goals>
            <phase>validate</phase>
        </execution>
```

The `clone` goal simply takes the origin and URL of the workspace in whatever
tree it is run in, and

  * Clones it into a new directory under `/tmp`
  * Hydrates all of the submodules
  * Gets all submodules onto the development branch (settable with a property - the default is `develop`)
  * Prints out the directory it was cloned into on stdout, so a script can extract it with 
    string pattern-matching (all subsequent steps will execute in that directory)

The `print-message` mojo allows us to just attach a formatted message that will be printed
to the console at the end of a Maven run, on success, on failure or always, which allows the
operator to know what to do next (when using the `telenav-build/release` script, this can be ignored):

```xml
        <execution>
            <id>print-phase-zero-message</id>
            <goals>
                <goal>print-message</goal>
            </goals>
            <phase>install</phase>
            <configuration>
                <onFailure>false</onFailure>
                <message>
                    Your origin URL has been cloned to the directory displayed.

                    Change directories to that to proceed with phase-1:

                    `mvn \
                    \t-P release-phase-1 \
                    \t-Denforcer.skip=true \
                    \t-Dcactus.expected.branch=develop \
                    \t-Dcactus.maven.plugin.version="${CACTUS_VERSION}" \
                    \t-Dcactus.families=${FAMILIES_TO_RELEASE} \
                    \t-DreleaseBranchPrefix=${RELEASE_BRANCH_PREFIX} \
                    \t-Dmaven.test.skip.exec=true \
                    \t\tclean \
                    \t\tvalidate
                    `
                </message>
            </configuration>
        </execution>

    </executions>
</plugin>        
```

The `print-message` mojo is used in each of the subsequent phases, but will be
omitted from the rest of this document for brevity.

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "phase-1"></a>
### Release Phase 1 - Bump Project Versions

This phase, and the remainder, run in the workspace folder under `/tmp` created in phase 0.

Here we do one of the most far-reaching steps of release:

  1. Bump the versions of all projects being released, assigning each a
     `release/[major].[minor].[dot]` version
  2. Update versioning properties across all `pom.xml` files that reference any project or family
     being updated
  3. If any of those properties were in superpoms, then
     * Check if the current version of that superpom has already been published to Maven Central
     * If yes, bump its version
     * If no, omit it from the set of things to deploy
  4. If any of the above resulted in superpom version changes, also update every project that
     references them in a property or as a parent, bumping those projects' version if there
     is not one already being made for it
  5. Loop, repeating the steps from 2-4 until no further changes are generated
  6. Rewrite all of the `pom.xml` files that are to be changed

> Note: Publishing to Maven central takes some time, even after releasing a Nexus repository.
> If you have recently published anything that might be used in the build, be sure to wait
> until those artifacts are really available from Maven Central - otherwise, Cactus can check
> that something is unpublished when it actually has been - it just hasn't shown up yet.

> Also Note: Sonatype's Nexus, that deploys to Maven Central, will sometimes appear to succeed
> deploying a superpom that has already been published - the repository can be _closed_, and
> unless you are very fast to refresh its UI, it will appear that you successfully _released_
> the maven repository, when in fact it was silently dropped, and there is no longer any way
> to get diagnostics from it.  If a release seems to succeed, but still has not arrived on
> Maven Central after several hours, that may be the problem.

> There _are_ *non-release only* cases for updating versions of things during development,
> where we do *not* want to cascade changes across a vast slew of projects, and there are
> two properties we can use to define how such changes are applied: `<bumpPolicy>ignore</bumpPolicy>` (in a
> profile) or `-Dcactus.superpom.bump.policy=ignore` will cause properties in superpoms to
> be updated, but their versions not altered.  `<singleFamily>true</singleFamily>` or
> `-Dcactus.version.single.family=true` will not touch superpoms at all.  Both of these
> options are __very dangerous__ and we want to very clearly understand the
> inconsistencies they can create.

```xml
<profile>
    <id>release-phase-1</id>
    <activation>
        <activeByDefault>false</activeByDefault>
    </activation>
    <properties>
        <maven.test.skip.exec>true</maven.test.skip.exec>
    </properties>
    <build>
        <plugins>

            <plugin>
                <groupId>com.telenav.cactus</groupId>
                <artifactId>cactus-maven-plugin</artifactId>
                <version>${cactus.maven.plugin.version}</version>
                <configuration>
                    <scope>family</scope>
                    <verbose>true</verbose>
                    <includeRoot>true</includeRoot>
                    <tolerateVersionInconsistenciesIn>lexakai</tolerateVersionInconsistenciesIn>
                </configuration>
                <executions>

                    <execution>
                        <id>filter-families-from-plugins-1</id>
                        <goals>
                            <goal>filter-families</goal>
                        </goals>
                        <configuration>
                            <familiesRequired>true</familiesRequired>
                            <properties>cactus.publish.check.skip</properties>
                        </configuration>
                    </execution>

                    <execution>
                        <id>bump-versions-of-families</id>
                        <goals>
                            <goal>bump-version</goal>
                        </goals>
                        <configuration>
                            <scope>family</scope>
                            <bumpPublished>true</bumpPublished>
                            <commitChanges>true</commitChanges>
                            <commitMessage>Prepare for release</commitMessage>
                            <versionFlavor>to-release</versionFlavor>
                            <createReleaseBranch>true</createReleaseBranch>
                        </configuration>
                    </execution>
```

The set of properties here, where we describe what to do to is worth going through:

  * `<scope>family</scope>` - the operation we are doing applies to a list of families
    specified by `-Dcactus.families=...` on the command line (a few other scopes are
    available - `all`, `all-project-families`, `same-group-id` and `just-this`)
  * `<bumpPublished>true</bumpPublished>` - check all superpoms, and bump the version
    of anything that was already published, where the local copy differs from what was
    published - this is critical to avoid either failed deploys, or deploying jars that
    don't depend on what we think they do
  * `<commitChanges>true</commitChanges>` - create a commit in every affected repository
    after versions have been updated
  * `<versionFlavor>to-release</versionFlavor>` - instruct `bump-version` to strip
    `-SNAPSHOT` from any versions that have them
  * `<createReleaseBranch>true</createReleaseBranch>` - create an appropriate release
    branch in each repository.  This comes with a caveat - both the workspace root
    and the superpom submodules straddle _all_ of the _project families_ we have.
    What should be the branch name for those?  For those, we generate a branch
    name from _all_ of the families and versions we're releasing, lexically sorted for
    predictability - e.g. `release/kivakit-1.6.1_lexakai-1.0.9_mesakit-0.9.15`

A few properties are not shown above (because our release script asks questions on
the command-line and populates them):

  1. `-Dcactus.families` / `<families>` - this is the list of project families being released

  2. What precisely to do *to* the version of each project family.  The default is
        incrementing the _dot revision_ (third decimal).  To do something else, use
        `-Dcactus.version.change.magnitude=major/minor/dot/none` / `<versionChangeMagnitude>`
        to set what is applied to each project - *or* we can specify explicitly using
     * `cactus.no.bump.families` / `<noRevisionFamilies>` - set
     some families not to receive a version bump _at all_ (this is fine when going from snapshot
     to release, and not a good idea when going from release to snapshot)
     * `cactus.dot.bump.families` / `<dotRevisionFamilies>` - set
       some families to receive a dot-revision increment
     * `cactus.minor.bump.families` / `<minorRevisionFamilies>` - set some families to
       have their minor (second decimal) version incremented and their third decimal zeroed
     * `cactus.major.bump.families` / `<majorRevisionFamilies>` - set some families to have
       their _major_ version incremented (e.g. 2.9.1 -> 3.0.0)

The `bump-version` Mojo _will fail the build_ if it is told to change the version of
a family, but the changes it is told to apply add up to doing nothing to the version.

```xml
                </executions>
            </plugin>

        </plugins>
    </build>
</profile>
```

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "phase-2"></a>
### Release Phase 2 - Publishing Documentation, Testing 

This is the most intensive step of our build, because it involves generating
Javadoc and [Lexakai](https://www.lexakai.org) documentation. The generated
files are put into _assets repositories_ that are part of our git submodule tree,
and these assets repositories are served by Github Pages. Lexakai links these
assets into the `README.md` for each project.

Additionally, it contains a few hacks, because we are using JDK 9's module
system, but have a few application projects that use the `maven-shade-plugin`
to create "fat jars" that do not contain a `module-info.class` - and Javadoc
aggregation gets unfix-ably broken if we try to combine modular and non-modular
javadoc - so we disable the shade plugin entirely here (it will be enabled
when we build jars to deploy in phase 4).

```xml
<profile>
    <id>release-phase-2</id>
    <activation>
        <activeByDefault>false</activeByDefault>
    </activation>
    <properties>
        <maven.shade.skip>true</maven.shade.skip>
```

We need the shade plugin disabled to allow Javadoc aggregation to succeed;
this is not a standard property - the shade plugin just names it `skip`, but
we do not want it to collide with any other plugin doing the same thing, so
our superpom configuration for the shade plugin reads this property to decide
what to do.

```xml
    </properties>
    <build>
        <plugins>

            <plugin>
                <groupId>com.telenav.cactus</groupId>
                <artifactId>cactus-maven-plugin</artifactId>
                <version>${cactus.maven.plugin.version}</version>
                <configuration>
                    <scope>family</scope>
                    <verbose>true</verbose>
                    <includeRoot>true</includeRoot>
                    <tolerateVersionInconsistenciesIn>lexakai</tolerateVersionInconsistenciesIn>
                </configuration>

                <executions>

                    <execution>
                        <id>filter-families-from-plugins</id>
                        <goals>
                            <goal>filter-families</goal>
                        </goals>
                        <configuration>
                            <familiesRequired>true</familiesRequired>
                            <properties>
                                skipIfEmpty,
                                gpg.skip,
                                maven.deploy.skip,
                                do.not.publish,
                                cactus.codeflowers.skip,
                                cactus.copy.javadoc.skip,
                                cactus.lexakai.skip,
                                cactus.generate.lexakai.skip,
                                cactus.publish.check.skip,
                                maven.javadoc.skip,
                                skipNexusStagingDeployMojo
                            </properties>
                        </configuration>
                    </execution>
```

This time, we are turning off a whole bunch of things with `filter-families` - if
we're not going to deploy it, we don't want to generate documentation for it, and
we definitely don't want to generate spurious diffs in assets repositories for things
we don't intend to alter or publish - Lexakai, in particular, updates `README.md` files
for the projects it operates on, and that could generate changes in projects far beyond
what we're releasing if not controlled.

```xml
                    <execution>
                        <!-- Generate magic lexakai files from data in the pom  -->
                        <id>generate-lexakai-properties-files</id>
                        <goals>
                            <goal>lexakai-generate</goal>
                        </goals>
                    </execution>
```

Lexakai also requires some settings files existing in `documentation/` sub-folders
of projects;  most of the information in them can also be obtained from a Maven `pom.xml`
file, so this step just ensures that these files are generated from `pom.xml` contents for
any projects that don't already have one, since that would be a silly reason to go back
and start over on a release.

```xml
                    <execution>
                        <id>generate-codeflowers</id>
                        <goals>
                            <goal>codeflowers</goal>
                        </goals>
                        <phase>install</phase>
                    </execution>
```

Cactus also includes a mojo to generate
[codeflowers visualization of code line-count](http://www.redotheweb.com/CodeFlower/),
which we build into our _assets repositories_.

```xml
                    <execution>
                        <id>generate-lexakai-docs</id>
                        <goals>
                            <goal>lexakai</goal>
                        </goals>
                        <phase>install</phase>
                    </execution>

                    <execution>
                        <id>copy-javadoc-to-assets-dir</id>
                        <goals>
                            <goal>copy-javadoc</goal>
                        </goals>
                        <phase>verify</phase>
                    </execution>

                    <execution>
                        <id>copy-agg-javadoc-to-assets-dir</id>
                        <goals>
                            <goal>copy-aggregated-javadoc</goal>
                        </goals>
                        <phase>verify</phase>
                    </execution>
```

Our _assets repositories_ also contain the javadoc of all projects - in the Maven
`verify` phase, we copy it there.

```
                </executions>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-javadoc-plugin</artifactId>
                <version>${maven-javadoc-plugin.version}</version>
                <executions>

                    <execution>
                        <id>generate-javadoc</id>
                        <goals>
                            <goal>javadoc-no-fork</goal>
                        </goals>
                        <phase>prepare-package</phase>
                    </execution>

                    <execution>
                        <id>generate-aggregate-javadoc</id>
                        <goals>
                            <goal>aggregate-no-fork</goal>
                        </goals>
                        <phase>prepare-package</phase>
                    </execution>

                </executions>
            </plugin>
```

Here we are simply ensuring that javadoc is built, at a slightly earlier phase
than its default.

```xml
            <!-- Some javadoc won't be (and cannot be if it uses the shade plugin
            to clobber module-info.class files) rebuilt, so we night to
            sign NOW in addition to including the plugin in the next phase -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-gpg-plugin</artifactId>
                <version>3.0.1</version>
                <executions>

                    <execution>
                        <id>sign-artifacts</id>
                        <phase>install</phase>
                        <configuration>
                            <gpgArguments>
                                <arg>--pinentry-mode</arg>
                                <arg>loopback</arg>
                            </gpgArguments>
                        </configuration>
                        <goals>
                            <goal>sign</goal>
                        </goals>

                    </execution>
                </executions>
            </plugin>
```

This step may no longer be needed, but we ran into some issues with javadoc jars being
unsigned, and this was part of the process of fixing it.

```xml
        </plugins>
    </build>
</profile>

```

At the end of this phase, the user is requested to review the generated
documentation and make sure things look right before proceeding.

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "phase-3"></a>
### Release Phase 3 - Committing Changes and Updating Metadata 

A number of our projects use the `cactus-metadata` library, which generates a
couple of properties files into the sources that describe the project and build,
including the git hash of the commit they were built against, and whether or not
the repository the jar was built from contained local changes - this information
can be critical when debugging a production problem and trying to reproduce the
environment that created it.

So we want to perform a commit (but not a push) before we do our final build that
is going to be published, so that the metadata reflects the exact commit we are
building, and reflects the fact that it was built against an unmodified checkout
of that commit.

```xml
        <profile>
            <id>release-phase-3</id>
            <activation>
                <activeByDefault>false</activeByDefault>
            </activation>
            <properties>
                <releasePush>false</releasePush>
            </properties>
            <build>
                <plugins>

                    <plugin>
                        <groupId>com.telenav.cactus</groupId>
                        <artifactId>cactus-maven-plugin</artifactId>
                        <version>${cactus.maven.plugin.version}</version>
                        <configuration>
                            <scope>family</scope>
                            <verbose>true</verbose>
                            <includeRoot>true</includeRoot>
```

We see `<includeRoot>true</includeRoot>` in several places - it is used by
a number of Cactus mojos that perform Git operations that change what commit a
git submodule is on (by committing, or changing branches, or whatever).  Any change
of a submodule's commit puts the workspace into a _modified_ state - there is
a change of commit pointed-to that we could commit or not.

Depending on what we are doing, sometimes we want a commit to be automatically
generated (and or for the `branch` fields in `$SUBMODULE_ROOT/.gitmodules` to be
updated); sometimes we don't.  The default is not to do anything to the root - but
in this case, we definitely *do* want the root updated along with everything else.

```xml
                            <tolerateVersionInconsistenciesIn>lexakai</tolerateVersionInconsistenciesIn>
                        </configuration>
                        <executions>

                            <execution>
                                <id>filter-families-from-plugins-3</id>
                                <goals>
                                    <goal>filter-families</goal>
                                </goals>
                                <configuration>
                                    <familiesRequired>true</familiesRequired>
                                    <properties>
                                        skipIfEmpty,
                                        maven.deploy.skip,
                                        cactus.codeflowers.skip,
                                        cactus.copy.javadoc.skip,
                                        cactus.lexakai.skip,
                                        cactus.generate.lexakai.skip,
                                        cactus.publish.check.skip,
                                        maven.javadoc.skip,
                                        do.not.publish,
                                        skipNexusStagingDeployMojo,
                                        gpg.skip
                                    </properties>
                                </configuration>
                            </execution>
                            
                            <execution>
                                <!-- Ensure we don't try to publish a pom that
                                     was already published and is identical to the
                                     published one. -->
                                <id>filter-already-published-identical-poms</id>
                                <goals>
                                    <goal>filter-published</goal>
                                </goals>
                            </execution>
```

This simply turns off the Nexus and GPG plugins for superpoms that have already
been published in identical versions on Maven central (we can't publish the same
thing twice, so deployment would fail in sometimes difficult-to-debug ways).
It also serves as a final sanity check that we are not trying to use a superpom
we did _not_ bump the version of, but which has changed from its published version
(the bump version mojo should prevent that, but we can't be too careful).

```xml
                            <execution>
                                <id>commit-doc-changes</id>
                                <goals>
                                    <goal>commit</goal>
                                </goals>
                                <phase>validate</phase>
                                <configuration>
                                    <push>${releasePush}</push>
                                    <scope>all-project-families</scope>
                                    <commitChanges>true</commitChanges>
                                    <includeRoot>true</includeRoot>
                                    <commitMessage>Commit docs for release</commitMessage>
                                </configuration>
                            </execution>
```

This will perform a commit across all projects we updated docs in, with a clear,
descriptive message about what is going on, which lists _all_ of the things that
have been changed as part of this operation.

```xml
                            <execution>
                                <id>commit-asset-changes</id>
                                <goals>
                                    <goal>commit-assets</goal>
                                </goals>
                                <phase>validate</phase>
                                <configuration>
                                    <push>${releasePush}</push>
                                </configuration>
                            </execution>
```

This generates a similar commit in our assets repositories, so the updated docs
can be published to Github Pages.

```xml
                            <execution>
                                <id>check-already-published-version</id>
                                <goals>
                                    <goal>check-published</goal>
                                </goals>
                            </execution>

                            <execution>
                                <id>update-metadata-post-commit</id>
                                <goals>
                                    <goal>build-metadata</goal>
                                </goals>
                            </execution>
```

The ensures the `build.properties` and `project.properties` files the `cactus-metadata`
library reads are updated with the new commit-id following the commit.

```xml
                        </executions>
                    </plugin>

                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-javadoc-plugin</artifactId>
                        <version>${maven-javadoc-plugin.version}</version>
                        <executions>
                            <execution>
                                <id>generate-javadoc</id>
                                <goals>
                                    <goal>javadoc-no-fork</goal>
                                </goals>
                            </execution>
                            <execution>
                                <id>generate-aggregate-javadoc</id>
                                <goals>
                                    <goal>aggregate-no-fork</goal>
                                </goals>
                            </execution>
                            <execution>
                                <id>generate-javadoc-jar</id>
                                <goals>
                                    <goal>jar</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>

                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-source-plugin</artifactId>
                        <version>${maven-source-plugin.version}</version>
                        <executions>
                            <execution>
                                <id>generate-source-jar</id>
                                <goals>
                                    <goal>jar-no-fork</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>

                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-gpg-plugin</artifactId>
                        <version>${maven-gpg-plugin.version}</version>
                        <executions>

                            <execution>

                                <id>sign-artifacts</id>
                                <phase>verify</phase>
                                <configuration>
                                    <gpgArguments>
                                        <arg>--pinentry-mode</arg>
                                        <arg>loopback</arg>
                                    </gpgArguments>
                                </configuration>
                                <goals>
                                    <goal>sign</goal>
                                </goals>

                            </execution>
                        </executions>
                    </plugin>
```

The above just ensures javadoc and source jars are created and signed.  The next
step publishes to Maven central.

Note one caveat here:  We *must* set `skipLocalStaging` to true.  If we have an
aggregator project - a bill-of-materials POM - which is 
_not also the parent of all of the things built under it_, then the only thing
that will get published _is_ the bill-of-materials pom, not any of the projects that
got built.

Disabling local staging causes projects to be uploaded to Nexus as they are built,
rather than in a batch at the very end of the build process, ensuring that they
actually get published.

```
                    <plugin>
                        <groupId>org.sonatype.plugins</groupId>
                        <artifactId>nexus-staging-maven-plugin</artifactId>
                        <version>${nexus-staging-maven-plugin.version}</version>
                        <configuration>
                            <skipLocalStaging>true</skipLocalStaging>
                            <skipStaging>${do.not.publish}</skipStaging>
                            <autoReleaseAfterClose>${release.on.close}</autoReleaseAfterClose>
                            <keepStagingRepositoryOnCloseRuleFailure>true</keepStagingRepositoryOnCloseRuleFailure>
                        </configuration>

                        <executions>
                            <execution>
                                <goals>
                                    <goal>deploy</goal>
                                </goals>
                            </execution>
                        </executions>

                    </plugin>

                </plugins>
            </build>
        </profile>
```

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "phase-4"></a>
### Release Phase 4 - Publishing to Maven Central

At this point, our release-proper is done;  what remains is pushing changes, merging
them, and getting the development branch updated, so that everything is in sync
and ready for future development.

```xml
<profile>
    <id>release-phase-4</id>
    <activation>
        <activeByDefault>false</activeByDefault>
    </activation>
    <build>
        <plugins>
            <plugin>
                <groupId>com.telenav.cactus</groupId>
                <artifactId>cactus-maven-plugin</artifactId>
                <version>${cactus.maven.plugin.version}</version>
                <configuration>
                    <scope>family</scope>
                    <verbose>true</verbose>
                    <tolerateVersionInconsistenciesIn>lexakai</tolerateVersionInconsistenciesIn>
                    <push>${releasePush}</push>
```

Note that we use a `-DreleasePush=true` property, provided only from the command-line,
to enable a user to dry-run all of the steps of a release without actually pushing
to Github - since cleaning up branches is no fun, and the steps that create branches will
(intentionally) fail if the branches they would create already exist remotely.

```xml
                    <commitChanges>true</commitChanges>
                    <includeRoot>true</includeRoot>
                    <commitMessage>Commit docs for release</commitMessage>
                </configuration>
                <executions>

                    <execution>
                        <id>filter-families-from-plugins-4</id>
                        <goals>
                            <goal>filter-families</goal>
                        </goals>
                        <configuration>
                            <familiesRequired>true</familiesRequired>
                            <properties>skipIfEmpty,cactus.publish.check.skip,cactus.check.skip</properties>
                        </configuration>
                    </execution>

                    <execution>
                        <id>merge-release-into-develop</id>
                        <phase>validate</phase>
                        <goals>
                            <goal>merge</goal>
                        </goals>
                        <configuration>
                            <alsoMergeInto>release/current</alsoMergeInto>
```

This parameter to the merge plugin tells it to, before it merges changes back into develop,
to merge them into the `release/current` branch first.

```xml
                            <tag>true</tag>
                            <includeRoot>true</includeRoot>
                        </configuration>
                    </execution>

                    <execution>
                        <id>move-to-new-snapshot-version</id>
                        <goals>
                            <goal>bump-version</goal>
                        </goals>
                        <phase>generate-sources</phase>
                        <configuration>
                            <commitChanges>true</commitChanges>
                            <scope>family</scope>
                            <includeRoot>true</includeRoot>
                            <versionFlavor>to-snapshot</versionFlavor>
                            <updateDocs>false</updateDocs>
                            <superpomBumpPolicy>BUMP_ACQUIRING_NEW_FAMILY_FLAVOR</superpomBumpPolicy>
                        </configuration>
                    </execution>
```

Here we use the `bump-version` Mojo again, to switch to a snapshot version.  Switching to
a snapshot version will automatically increment the last decimal - but if we passed
arguments for altering other decimals when we bumped versions to get onto a release
version, make sure *not* to pass them here, or we will wind up altering versions in more
ways than we intend.

```
                    <execution>
                        <id>commit-new-snapshots</id>
                        <goals>
                            <goal>commit</goal>
                        </goals>
                        <phase>generate-resources</phase>
                        <configuration>
                            <commitMessage>Update to new snapshot version</commitMessage>
                            <scope>all</scope>
                            <includeRoot>true</includeRoot>
                        </configuration>
                    </execution>

                    <execution>
                        <id>push-new-snapshots</id>
                        <goals>
                            <goal>push</goal>
                        </goals>
                        <phase>process-resources</phase>
                        <configuration>
                            <pushAll>true</pushAll>
                        </configuration>
                    </execution>
```

This executes a `git push --all` which will cause both our release branch changes
_and_ the updated development branch to be pushed, in each affected repository.

```xml
                </executions>
            </plugin>
        </plugins>
    </build>
</profile>
```

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "cactus-mojos"></a>
## Cactus Mojos 

The following are a list of Cactus Mojos used in development and release processes. Mojos are
invoked with `com.telenav.cactus:cactus-maven-plugin:[mojo-name]`. 

<a name = "filter-families"></a>
### filter-families 

The `filter-families` Mojo serves as a general swiss-army knife for turning other mojos - including those
built into Maven - *off* for projects that are not part of what is being released.  It literally just takes
a set of _project families_ and a set of _properties to set to true_ - most Maven mojos have a `skip`
property we can set to tell them _don't run against this project_.

We can use the `FilterFamiliesMojo` to guarantee we don't accidentally publish anything we don't intend
to, or do expensive work against projects that are irrelevant to the release - so, when we get ready to
deploy our jars to Maven central, we just give it the property `skipNexusStagingDeployMojo` as one
of the properties to set to true for anything *not* part of the project family (or in the superpom
parent hierarchy of) anything we're publishing.

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "filter-published"></a>
### filter-published 

The `filter-published` Mojo works similarly, but specifically turns off publishing (and whatever else
we tell it to) specifically for projects which have already been published to Maven central (or wherever
we point it to) and are unaltered from their bits there.  It will also fail the build - early - if we
are trying to publish something, and it has already been published, but our local copy differs.


<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "cactus-scripts"></a>
## Cactus Scripts 

At the time of this writing, cactus 1.5.19, this is the set of installed scripts and
their descriptions, as mentioned in the quick-start section at the top of this document:

### Commit all submodules (ccm)

	$HOME/bin/cactus-commit-all-submodules
	$HOME/bin/ccm

	Commit all changes in all git submodules in one
	shot, with one commit message.


### Push all submodules (cpush)

	$HOME/bin/cactus-push-all-submodules
	$HOME/bin/cpush

	Push all changes in all submodules in one shot, after
	ensuring that our local checkouts are all up-to-date.


### Pull all submodules (cpull)

	$HOME/bin/cactus-pull-all-submodules
	$HOME/bin/cpull

	Pull changes in all submodules


### Development preparation (cdev)

	$HOME/bin/cactus-development-preparation
	$HOME/bin/cdev

	Switch to the 'develop' branch in all java project checkouts.


### Simple bump version (cbump)

	$HOME/bin/cactus-simple-bump-version
	$HOME/bin/cbump

	Bump the version of the Maven project family it is invoked against,
	updating superpom properties with the new version but NOT UPDATING
	THE VERSIONS OF THOSE SUPERPOMS.

	This is suitable for the simple case of updating the version
	of one thing during active development, not for doing a full
	product release.


### Last change by project (cch)

	$HOME/bin/cactus-last-change-by-project
	$HOME/bin/cch

	Prints git commit info about the last change that altered a java
	source file in a project, or with --all, the entire tree.


### Family versions (cver)

	$HOME/bin/cactus-family-versions
	$HOME/bin/cver

    Prints the inferred version of each project family in the current
	project tree.  These versions are what will be the basis used by
	BumpVersionMojo when computing a new revision.


### Release one project (crel)

	$HOME/bin/cactus-release-one-project
	$HOME/bin/crel

	Release a single project - whatever pom we run it against - to ossrh or wherever it is configured to send it.


### Update scripts (cactus-script-update)

	$HOME/bin/cactus-update-scripts
	$HOME/bin/cactus-script-update

	Finds the latest version of cactus we have installed, and runs
	its install-scripts target to update/refresh the scripts
	we are installing right now.

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

<a name = "road-map"></a>
## Road Map

### More Scripts

The set of scripts installed by the `install-scripts` mojo is fairly incomplete, and
most take no arguments and do one canned thing.  This should be improved, and scripts
for common tasks like branching added.

### Fully-Automated Granular Versioning

Updating the versions of entire families of libraries, whether or not any code in
them or their dependencies has changed is a concession to the reality of managing trees
of hundreds of projects while keeping one's sanity.

But Maven's `import` dependencies - which pulls in an entire `<dependencyManagement>` section
from another `pom.xml` offers a sane solution - if we want to depend on libraries in the
family `kivakit`, we just pull in its dependencies - we only need the version of one
superpom, not everything - and we automatically get a set of dependencies that were tested
and released together, without needing to know anything about the versions of individual
libraries within that family.

So it is possible to have all the benefits of having just a single-version to remember,
without all the churn of releasing identical-but-for-the-version-number things to Maven
central.

We already have tooling - the `last-change` mojo and the `cactus-last-change-by-project`
script that will tell we the commit and commit date of the last change made to any file
in a project (filterable by file extension).  And we also have tooling to walk the complete
dependency graph of a project and determine if anything in _that_ has changed.

The only thing one has to give up to do that is manually monkeying with the versions of
projects within the codebase - ever.  A requirement of software versions is that they be
machine-readable;  the best way to keep that process reliable and mistake-free is if they
are also machine- - not human - _written_.

## Building Cactus

> [How to build this project](https://github.com/Telenav/telenav-build/blob/release/1.5.23/documentation/building.md) <!-- [cactus.replacement-branch-name] --> 

## Source Code

[//]: # (end-user-text)

### Projects <a name = "projects"></a> &nbsp; <img src="https://telenav.github.io/telenav-assets/images/icons/gears-32.png" srcset="https://telenav.github.io/telenav-assets/images/icons/gears-32-2x.png 2x"/>

[**maven-model**](maven-model/README.md)  
[**maven-plugin**](maven-plugin/README.md)  
[**metadata**](metadata/README.md)  

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-128-2x.png 2x"/>

### Javadoc Coverage <a name = "javadoc-coverage"></a> &nbsp; <img src="https://telenav.github.io/telenav-assets/images/icons/bargraph-24.png" srcset="https://telenav.github.io/telenav-assets/images/icons/bargraph-24-2x.png 2x"/>

&nbsp; <img src="https://telenav.github.io/telenav-assets/images/meters/meter-40-96.png" srcset="https://telenav.github.io/telenav-assets/images/meters/meter-40-96-2x.png 2x"/>
 &nbsp; &nbsp; [**maven-model**](maven-model/README.md)  
&nbsp; <img src="https://telenav.github.io/telenav-assets/images/meters/meter-40-96.png" srcset="https://telenav.github.io/telenav-assets/images/meters/meter-40-96-2x.png 2x"/>
 &nbsp; &nbsp; [**maven-plugin**](maven-plugin/README.md)  
&nbsp; <img src="https://telenav.github.io/telenav-assets/images/meters/meter-100-96.png" srcset="https://telenav.github.io/telenav-assets/images/meters/meter-100-96-2x.png 2x"/>
 &nbsp; &nbsp; [**metadata**](metadata/README.md)

[//]: # (start-user-text)



[//]: # (end-user-text)

<img src="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-512.png" srcset="https://telenav.github.io/telenav-assets/images/separators/horizontal-line-512-2x.png 2x"/>

<sub>Copyright &#169; 2011-2021 [Telenav](https://telenav.com), Inc. Distributed under [Apache License, Version 2.0](LICENSE)</sub>  
<sub>This documentation was generated by [Lexakai](https://www.lexakai.org). UML diagrams courtesy of [PlantUML](https://plantuml.com).</sub>
