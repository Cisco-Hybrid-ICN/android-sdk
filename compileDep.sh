set -e
make download-dep
export ANDROID_ARCH="arm64"
make compile-dep
export ANDROID_ARCH="x86_64"
make compile-dep
