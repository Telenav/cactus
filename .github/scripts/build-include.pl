#!/usr/bin/perl

#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#
#  © 2011-2021 Telenav, Inc.
#  Licensed under Apache License, Version 2.0
#
#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

use strict;
use warnings;

my $WORKSPACE = `pwd`;
$WORKSPACE =~ s/\n+//g;

my $KIVAKIT_HOME = "$WORKSPACE/kivakit";
my $MESAKIT_HOME = "$WORKSPACE/mesakit";

$ENV{$KIVAKIT_HOME} = $KIVAKIT_HOME;
$ENV{$MESAKIT_HOME} = $MESAKIT_HOME;

#
# Print the given string
#

sub say
{
    my ($text) = @_;

    print "===> $text\n"
}

#
# Run the given command in bash
#

sub run
{
    my ($command) = @_;

    say("Executing: $command");
    return !system($command);
}

#
# Checks the given branch name
#

sub check_branch
{
    my ($branch) = @_;

    die "Must supply a branch" if (!defined $branch || $branch eq "");
}

#
# Checks the build type
#

sub check_build_type
{
    my ($build_type) = @_;

    die "Must supply build type" if (!defined $build_type || $build_type eq "");
    die "Unrecognized build type: $build_type" if (!($build_type eq "package" || $build_type eq "publish"));
}

#
# Check the given repository name, returning the full GitHub URL if it is valid
#

sub check_repository
{
    my ($repository) = @_;

    die "Must supply a repository" if ($repository eq "");
    die "Must supply a github repository URL" if (index($repository, "https://github.com/Telenav/") != 0);
}

#
# Converts a GitHub ref like "refs/heads/feature/lasers" into a branch name like "feature/lasers"
#

sub reference_to_branch
{
    my ($reference) = @_;

    if ($reference =~ m!.*/(\d+)/.*!)
    {
        return "pull/$1"
    }

    $reference =~ s!refs/heads/!!;

    return $reference;
}

#
# Install the pom.xml file in the current folder
#

sub install_pom
{
    my ($folder) = @_;

    say("Installing super POM in $folder");
    die "Cannot install super pom" if !run("cd $folder && mvn --batch-mode --no-transfer-progress clean install");
}

sub branch_exists
{
    my ($branch) = @_;

    return `git branch --list $branch`;
}

#
# Clones the given repository and branch
#

sub clone
{
    my ($repository, $branch, $allow_pull_request) = @_;
    check_repository($repository);
    check_branch($branch);

    say("Cloning $repository ($branch)");

    my $is_pull_request = (index($branch, "pull/") == 0);
    my $pull_request_allowed = defined $allow_pull_request && ($allow_pull_request eq "allow-pull-request");

    # If the branch is a pull request,
    if ($is_pull_request)
    {
        # and pull requests are allowed,
        if ($pull_request_allowed)
        {
            # then check out the branch as a pull request
            die "Cannot check out pull request $branch" if !run("cd $WORKSPACE && git clone $repository master && git fetch origin '$branch/head:pull-request' && git checkout pull-request");
        }
        else
        {
            # otherwise if pull requests are not allowed, check out the develop branch.
            die "Cannot check out develop branch" if !run("cd $WORKSPACE && git clone --branch develop --quiet $repository");
        }
    }
    else
    {
        # If the branch is not a pull request, clone the requested branch (which may fail),
        run("cd $WORKSPACE && git clone --branch $branch --quiet $repository");

        # and if that fails (because there is no such branch),
        if (!branch_exists($branch))
        {
            # then clone the develop branch.
            die "Cannot check out develop branch" if !run("cd $WORKSPACE && git clone --branch develop --quiet $repository");
        }
    }
}

#
# Build project
#

sub build
{
    my ($build_type, $folder) = @_;
    my $passphrase = $ENV{"secrets.OSSRH_GPG_SECRET_KEY_PASSWORD"};
    check_build_type($build_type);

    say("Building $folder ($build_type)");

    if ($build_type eq "package")
    {
        die "Package build failed" if !run("cd $folder && mvn -Dmaven.javadoc.skip=true -DKIVAKIT_DEBUG='!Debug' -P shade -P tools --no-transfer-progress --batch-mode clean install");
    }
    elsif ($build_type eq "publish")
    {
        die "Publish failed" if !run("cd $folder && mvn -P attach-jars -P sign-artifacts -P shade -P tools --no-transfer-progress --batch-mode -Dgpg.passphrase='$passphrase' clean deploy");
    }
    else
    {
        die "Unrecognized build type: $build_type"
    }
}

sub build_kivakit
{
    my ($build_type) = @_;
    check_build_type($build_type);

    install_pom("$KIVAKIT_HOME/superpom");
    build($build_type, "$KIVAKIT_HOME");
}

sub build_kivakit_extensions
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/kivakit-extensions");
}

sub build_kivakit_examples
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/kivakit-examples");
}

sub build_lexakai
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/lexakai");
}

sub build_lexakai_annotations
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/lexakai-annotations");
}

sub build_mesakit
{
    my ($build_type) = @_;
    check_build_type($build_type);

    say("Installing shape file reader");
    die "Unable to install shape file reader" if !run("mvn install:install-file -Dfile='$WORKSPACE/mesakit/mesakit-map/geography/libraries/shapefilereader-1.0.jar' -DgroupId=org.nocrala -DartifactId=shapefilereader -Dversion=1.0 -Dpackaging=jar");

    install_pom("$MESAKIT_HOME/superpom");
    build($build_type, $MESAKIT_HOME);
}

sub build_mesakit_extensions
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/mesakit-extensions");
}

sub build_mesakit_examples
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/mesakit-examples");
}

