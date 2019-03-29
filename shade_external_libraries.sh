#!/bin/bash

export ZXING_SOURCE=https://github.com/zxing/zxing/archive/zxing-3.3.1.zip

set -x
set -e

if [ -d "core/src/main/java/io/rover/shaded/zxing" ]; then
    echo "ZXing library already fetched and shaded into the build.  Skipping."
    exit
fi

mkdir -p shade/bits

curl -C - -L $ZXING_SOURCE -o shade/bits/zxing.zip 

rm -rf shade/extracted
mkdir -p shade/extracted

unzip shade/bits/zxing.zip -d shade/extracted

# Now that we have all of our third party code downloaded locally, let's blow away our current shaded source directory and rebuild it:
rm -rf core/src/main/java/io/rover/shaded
mkdir -p core/src/main/java/io/rover/shaded
cp -R shade/extracted/zxing-zxing-3.3.1/core/src/main/java core/src/main/java/io/rover/shaded/zxing
# Now we want to patch all the java files (both package and import directives) to be their shaded equivalent: com.google.zxing -> io.rover.shaded.com.google.zxing
# I might be assuming BSD-like (cough-Apple-cough) userland here.
find core/src/main/java/io/rover/shaded/zxing -type f -print0 | xargs -0 -I {} sed -i.bak s/com.google.zxing/io.rover.shaded.zxing.com.google.zxing/g {}
