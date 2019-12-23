#!/usr/bin/env bash
#
# Based on
#
# Author: Stefan Buck
# License: MIT
# https://gist.github.com/stefanbuck/ce788fee19ab6eb0b4447a85fc99f447
#
#
# This script accepts the following parameters:
#
# * repo
# * id
# * filename
#
# Script to upload a release asset using the GitHub API v3.
#
# Example:
#
# upload-github-release-asset.sh id=<release id> repo=<owner/repo> filename=<file>
#

# Check dependencies.
set -e
xargs=$(which gxargs || which xargs)

# Validate settings.
[ "$TRACE" ] && set -x

CONFIG=$@

for line in $CONFIG; do
  eval "$line"
done

# Construct url
UPLOAD_URL="https://uploads.github.com/repos/$repo/releases/$id/assets?name=$(basename $filename)"

curl --data-binary @"$filename" -H "Authorization: token $GITHUB_TOKEN" -H "Content-Type: application/octet-stream" $UPLOAD_URL
