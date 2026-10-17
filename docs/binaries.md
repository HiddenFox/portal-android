# Bundled binaries

## `libportal.so`

- Provenance: `HiddenFox/heart-portal` branch `android`, commit `8871181`, GitHub Actions run `39044115390`
- Channel: release `android-ci` (https://github.com/HiddenFox/heart-portal/releases/tag/android-ci), asset fetched via api.github.com octet-stream
- Source size: `11,515,000` bytes
- SHA-256: `2E2CE3CB9DCC9CDEB656F78CCE1A593F5FAB317DFC26DA6736FE6FE71BAFA59B`
- CI verification: ELF 64-bit LSB PIE ARM aarch64, interpreter `/system/bin/linker64`, NEEDED only libdl/libm/libc, stripped - `verification=passed` (libportal-report.txt)
- Toolchain: Rust `1.90.0`, Android NDK `r27c` (27.2.12479018), target API 24
- Packaged location: `app/src/main/jniLibs/arm64-v8a/libportal.so`
- ABI: `arm64-v8a`
- Swapped in: 2026-10-17 (vc11 / 0.1.9)

### History

- vc10 (`861aed3`, 2026-10-15): first keepalive-fixed CI build (10,989,672 B, SHA-256 `448BC8A85FBD2A7B78E15EA840FF7E6C06E5902D86DE7BCABC80E7088A252809`), built from cak-ci staging; superseded.
- `8b02d05` (2026-10-12): original smoke build (cak-ci `hp-android-smoke`, run `38139921541`, 10,986,328 B, SHA-256 `996d9f88ef9b0e8305605ea99a05bdaae5f54da6be86e4e95f50c89b6721a070`); superseded.

The file is a verified NDK bionic AArch64 dynamically linked portal executable. It is
renamed with a `.so` suffix so Android packages it as a native library; the application
will execute the extracted file from `ApplicationInfo.nativeLibraryDir` in TASK-002.