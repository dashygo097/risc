#!/bin/bash
set -euo pipefail

BASE_DIR=$(dirname $(cd "$(dirname "$0")" && pwd))
BUILD_DIR=${BASE_DIR}/build

# Build script for a CMake project
mkdir -p $BUILD_DIR
cd $BUILD_DIR
cmake $BASE_DIR
cd $BASE_DIR
