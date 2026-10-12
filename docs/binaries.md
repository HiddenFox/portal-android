# Bundled binaries

## `libportal.so`

- Source artifact: `D:\Workspace\neo-workspace\tmp\cak-ci\heart-portal-android`
- Provenance: `cak-ci` branch `hp-android-smoke`, GitHub Actions run `38139921541`
- Source filename: `heart-portal-android`
- Source size: `10,986,328` bytes
- SHA-256: `996d9f88ef9b0e8305605ea99a05bdaae5f54da6be86e4e95f50c89b6721a070`
- Packaged location: `app/src/main/jniLibs/arm64-v8a/libportal.so`
- ABI: `arm64-v8a`

The file is a verified NDK bionic AArch64 dynamically linked portal executable. It is
renamed with a `.so` suffix so Android packages it as a native library; the application
will execute the extracted file from `ApplicationInfo.nativeLibraryDir` in TASK-002.
