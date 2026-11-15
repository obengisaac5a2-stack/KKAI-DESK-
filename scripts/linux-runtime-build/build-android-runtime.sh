#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(realpath "${1:?project root required}")"
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SOURCES="$RUNNER_TEMP/kkdex-proot-sources"
OUTPUT="$RUNNER_TEMP/kkdex-proot-output"
mkdir -p "$SOURCES" "$OUTPUT"
cd "$SOURCES"
curl -fL --retry 3 -o android-ndk-r27c-linux.zip https://dl.google.com/android/repository/android-ndk-r27c-linux.zip
curl -fL --retry 3 -o talloc-2.5.0.tar.gz https://www.samba.org/ftp/talloc/talloc-2.5.0.tar.gz
curl -fL --retry 3 -o proot-5.1.107.96.zip https://github.com/termux/proot/archive/refs/tags/v5.1.107.96.zip
curl -fL --retry 3 -o libandroid-shmem-0.7.tar.gz https://github.com/termux/libandroid-shmem/archive/refs/tags/v0.7.tar.gz
printf '%s  %s\n' \
  59c2f6dc96743b5daf5d1626684640b20a6bd2b1d85b13156b90333741bad5cc android-ndk-r27c-linux.zip \
  912afa237510ae542a7733998eb18a12bcda35ab6729c8e2ddb43e8d0ebab007 talloc-2.5.0.tar.gz \
  75f654fe60dea92dabff2bf083ae8bfe4f91baa6a1a374786a6bf391015eebaa proot-5.1.107.96.zip \
  1e5ff8459bc0a8c229dd8a94b27d119987e09ef3414331c2b5ebfff20b98e867 libandroid-shmem-0.7.tar.gz | sha256sum -c -
cd "$REPO_ROOT"
docker build --platform linux/amd64 -t kkdex-proot-build scripts/linux-runtime-build
docker run --rm --platform linux/amd64 \
  -v "$SOURCES:/dl:ro" \
  -v "$OUTPUT:/out" \
  kkdex-proot-build
sudo chmod -R a+rX "$OUTPUT"
JNI="$PROJECT_ROOT/android/app/src/main/jniLibs/arm64-v8a"
LICENSES="$PROJECT_ROOT/android/app/src/main/assets/third-party-licenses"
mkdir -p "$JNI" "$LICENSES"
install -m 755 "$OUTPUT/libproot.so" "$JNI/libproot.so"
install -m 755 "$OUTPUT/libproot-loader.so" "$JNI/libproot-loader.so"
install -m 755 "$OUTPUT/libtalloc.so" "$JNI/libtalloc.so"
install -m 644 "$OUTPUT/COPYING.proot" "$LICENSES/COPYING.proot"
install -m 644 "$OUTPUT/LICENSE.talloc" "$LICENSES/LICENSE.talloc"
install -m 644 "$OUTPUT/LICENSE.libandroid-shmem" "$LICENSES/LICENSE.libandroid-shmem"
install -m 644 "$OUTPUT/SHA256SUMS" "$LICENSES/PROOT-SHA256SUMS.txt"
file "$JNI"/*.so
