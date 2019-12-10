#!/bin/bash

# Decrypt private ssh key file
# (File encryption and key/IV injection courtesy of Travis: https://docs.travis-ci.com/user/encrypting-files/)
openssl aes-256-cbc -K $encrypted_9f19d2edcfdd_key -iv $encrypted_9f19d2edcfdd_iv -in .travis/travis_apjdbc_rsa.enc -out /tmp/id_rsa -d
eval "$(ssh-agent -s)"
chmod 600 /tmp/id_rsa
# remove passphrase from private key
ssh-keygen -p -P "$RSYNC_PASSPHRASE" -N "" -f /tmp/id_rsa
ssh-add /tmp/id_rsa
sbt -jvm-opts .jvmopts-travis docs/publishRsync
