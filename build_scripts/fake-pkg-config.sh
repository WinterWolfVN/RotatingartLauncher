#!/bin/bash
# Fake pkg-config that blocks host-only libraries for cross-compilation
for arg in "$@"; do
    case "$arg" in
        SPIRV-Tools*|spirv-tools*|libdrm*|libudev*)
            exit 1
            ;;
    esac
done
exec /usr/sbin/pkg-config "$@"
