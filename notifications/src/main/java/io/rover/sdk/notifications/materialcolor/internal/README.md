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
