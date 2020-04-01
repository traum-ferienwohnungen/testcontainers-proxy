#!/usr/bin/env bash

./mvnw versions:set -DnewVersion="$1"
./mvnw verify

if [ $? -eq 0 ]
then
  git ls-files . | grep 'pom\.xml$' | xargs git add
  git commit -m "[release] v$1"
  git tag "v$1" -m "[release] v$1"
  git push origin master "v$1"

  ./mvnw versions:set -DnewVersion="999-SNAPSHOT"
  git ls-files . | grep 'pom\.xml$' | xargs git add
  git commit -m "[release] v$1 -> 999-SNAPSHOT"
  git push origin master
else
  echo "Failed to validate."
fi
