#!/bin/bash

cp=$(find libs -name '*.jar' -print | awk 'BEGIN { cp=""; } { cp = sprintf("%s:%s",cp,$0); } END { print cp; }')
java -cp bin:$cp catalog.Uploader $@
