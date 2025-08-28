#!/bin/bash

# Script to vendor Google's material-color-utilities Java library into Rover notifications module
# This downloads the latest version and applies necessary transformations

set -e  # Exit on any error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
TEMP_DIR=$(mktemp -d)
DOWNLOAD_URL="https://github.com/material-foundation/material-color-utilities/archive/refs/heads/main.zip"
TARGET_DIR="$PROJECT_ROOT/notifications/src/main/java/io/rover/sdk/notifications/materialcolor/internal"

echo "🔍 Vendoring material-color-utilities into Rover notifications module..."

# Clean up function
cleanup() {
    echo "🧹 Cleaning up temporary files..."
    rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

# Step 1: Download the latest version
echo "📥 Downloading latest material-color-utilities from GitHub..."
cd "$TEMP_DIR"
curl -L -o material-color-utilities.zip "$DOWNLOAD_URL"

# Step 2: Extract the archive
echo "📦 Extracting archive..."
unzip -q material-color-utilities.zip
SOURCE_DIR="$TEMP_DIR/material-color-utilities-main/java"

if [ ! -d "$SOURCE_DIR" ]; then
    echo "❌ Error: Expected Java source directory not found in download"
    exit 1
fi

# Step 3: Remove existing vendored code
echo "🧹 Removing existing vendored code..."
rm -rf "$TARGET_DIR"

# Step 4: Create target directory structure
echo "📁 Creating target directory structure..."
mkdir -p "$TARGET_DIR"

# Step 5: Copy Java files and transform them
echo "🔄 Processing and transforming Java files..."

find "$SOURCE_DIR" -name "*.java" | while read -r file; do
    # Get relative path from source directory (portable version)
    relative_path="${file#$SOURCE_DIR/}"
    target_file="$TARGET_DIR/$relative_path"
    
    # Create target directory if it doesn't exist
    mkdir -p "$(dirname "$target_file")"
    
    echo "  Processing: $relative_path"
    
    # Transform the file content
    sed \
        -e 's/^package \([^;]*\);$/package io.rover.sdk.notifications.materialcolor.internal.\1;/' \
        -e 's/^import com\.google\.errorprone\.annotations\.[^;]*;//' \
        -e '/^import com\.google\.errorprone\.annotations\./d' \
        -e 's/@CanIgnoreReturnValue//g' \
        -e 's/@CheckReturnValue//g' \
        -e 's/@Var//g' \
        -e 's/^import blend\./import io.rover.sdk.notifications.materialcolor.internal.blend./' \
        -e 's/^import contrast\./import io.rover.sdk.notifications.materialcolor.internal.contrast./' \
        -e 's/^import dislike\./import io.rover.sdk.notifications.materialcolor.internal.dislike./' \
        -e 's/^import dynamiccolor\./import io.rover.sdk.notifications.materialcolor.internal.dynamiccolor./' \
        -e 's/^import hct\./import io.rover.sdk.notifications.materialcolor.internal.hct./' \
        -e 's/^import palettes\./import io.rover.sdk.notifications.materialcolor.internal.palettes./' \
        -e 's/^import quantize\./import io.rover.sdk.notifications.materialcolor.internal.quantize./' \
        -e 's/^import scheme\./import io.rover.sdk.notifications.materialcolor.internal.scheme./' \
        -e 's/^import score\./import io.rover.sdk.notifications.materialcolor.internal.score./' \
        -e 's/^import temperature\./import io.rover.sdk.notifications.materialcolor.internal.temperature./' \
        -e 's/^import utils\./import io.rover.sdk.notifications.materialcolor.internal.utils./' \
        -e 's/^import static blend\./import static io.rover.sdk.notifications.materialcolor.internal.blend./' \
        -e 's/^import static contrast\./import static io.rover.sdk.notifications.materialcolor.internal.contrast./' \
        -e 's/^import static dislike\./import static io.rover.sdk.notifications.materialcolor.internal.dislike./' \
        -e 's/^import static dynamiccolor\./import static io.rover.sdk.notifications.materialcolor.internal.dynamiccolor./' \
        -e 's/^import static hct\./import static io.rover.sdk.notifications.materialcolor.internal.hct./' \
        -e 's/^import static palettes\./import static io.rover.sdk.notifications.materialcolor.internal.palettes./' \
        -e 's/^import static quantize\./import static io.rover.sdk.notifications.materialcolor.internal.quantize./' \
        -e 's/^import static scheme\./import static io.rover.sdk.notifications.materialcolor.internal.scheme./' \
        -e 's/^import static score\./import static io.rover.sdk.notifications.materialcolor.internal.score./' \
        -e 's/^import static temperature\./import static io.rover.sdk.notifications.materialcolor.internal.temperature./' \
        -e 's/^import static utils\./import static io.rover.sdk.notifications.materialcolor.internal.utils./' \
        "$file" > "$target_file"
done

# Step 6: Add a README explaining the vendored code
echo "📝 Creating documentation..."
cat > "$TARGET_DIR/README.md" << 'EOF'
# Vendored Material Color Utilities

This directory contains a vendored copy of Google's material-color-utilities Java library.

## Source
- Original: https://github.com/material-foundation/material-color-utilities
- License: Apache 2.0

## Modifications
- Package names changed from `<module>` to `io.rover.sdk.notifications.materialcolor.internal.<module>`
- Google ErrorProne annotations removed
- Imports updated to reflect new package structure
- Classes remain public but are in `.internal` package to indicate they're not part of the public API

## Updates
To update this vendored code, run:
```bash
./vendor-material-color-utilities.sh
```

This script will download the latest version and apply all necessary transformations automatically.
EOF

# Step 7: Count processed files
file_count=$(find "$TARGET_DIR" -name "*.java" | wc -l)
echo "✅ Successfully processed $file_count Java files"

# Step 8: Show directory structure
echo "📋 Created directory structure:"
find "$TARGET_DIR" -type d | sort | sed 's|^'"$TARGET_DIR"'|notifications/src/main/java/io/rover/sdk/notifications/materialcolor|'

echo ""
echo "🎉 Material color utilities successfully vendored!"
echo "   Target: notifications/src/main/java/io/rover/sdk/notifications/materialcolor/"
echo "   Files: $file_count Java files processed"
echo ""
echo "Next steps:"
echo "1. Build the notifications module to verify integration"
echo "2. Commit the changes to version control"