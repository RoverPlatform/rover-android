#!/bin/bash

# Sets you up to make a release.  Pass in release name, no prefixed `v`.

if [ -z $1 ]
then
    echo "Pass in release name, no prefixed 'v'."
    exit -1
fi

export JAVA_HOME=/Applications/Android\ Studio.app/Contents/jre/jdk/Contents/Home

set -x
set -e

# confirm that git flow is present:
git flow version

# confirm that working directory has no untracked stuff:
git diff-index --quiet HEAD --

# make sure remote is refresh:
git fetch origin

git checkout master

# destroy master state!
git reset --hard origin/master

git checkout develop

# destroy develop state!
git reset --hard origin/develop

echo "Verifying SDK."
DEPLOY_MAVEN_PATH=`pwd`/maven ./gradlew clean test

echo "Making release branch for: $1"

git flow release start $1

echo "Edit your version numbers (build.gradle and README.md) and press return!"
read -n 1

echo "Building SDK."
DEPLOY_MAVEN_PATH=`pwd`/maven ./gradlew clean test sdk:assembleRelease sdk:publishProductionPublicationToMavenRepository

# add the untracked files that have appeared under maven with the new bits.
git add maven

git commit -a -m "Releasing $1."

git flow release finish $1

# move the gh-pages branch to develop to keep hosting (and allowing any pre-release builds in develop to be exposed for download).
git branch -f gh-pages develop

git push origin master
git push origin develop
git push origin gh-pages
