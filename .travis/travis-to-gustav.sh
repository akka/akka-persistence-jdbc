#!/bin/bash

eval "$(ssh-agent -s)"
# remove passphrase from private key
cp .travis/deploy-key /tmp/id_rsa
chmod 600 /tmp/id_rsa
ssh-keygen -p -P "$RSYNC_PASSPHRASE" -N "" -f /tmp/id_rsa
ssh-add /tmp/id_rsa
sbt -jvm-opts .jvmopts-travis docs/publishRsync
