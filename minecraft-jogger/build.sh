#!/usr/bin/env bash
set -euo pipefail
jogger_script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
jogger_build_dir="${JOGGER_BUILD_DIR:-$jogger_script_dir/jogger-source}"
jogger_revision=28d6e12d68121b7dd75f2e841058a6b7cdfe4a29
if [[ -e "$jogger_build_dir" ]]; then
    echo "Build directory exists. Choose a new JOGGER_BUILD_DIR to avoid overwriting it." >&2
    exit 1
fi
: "${JOGGER_KEYSTORE:?Set JOGGER_KEYSTORE to your private release keystore}"
: "${JOGGER_KEYSTORE_PASSWORD:?Set JOGGER_KEYSTORE_PASSWORD}"
git init "$jogger_build_dir"
git -C "$jogger_build_dir" remote add origin https://github.com/AngelAuraMC/Amethyst-Android.git
git -C "$jogger_build_dir" fetch --depth 1 origin "$jogger_revision"
git -C "$jogger_build_dir" checkout --detach FETCH_HEAD
git -C "$jogger_build_dir" config submodule.androidnsbypass.url https://github.com/alexytomi/androidnsbypass.git
git -C "$jogger_build_dir" submodule update --init --recursive
cp -a "$jogger_script_dir/overlay/." "$jogger_build_dir/"
python3 "$jogger_build_dir/scripts/jogger-prepare.py"
cd "$jogger_build_dir"
bash scripts/languagelist_updater.sh
gradle --no-daemon --console=plain --max-workers=3 :app_pojavlauncher:assembleRelease :jogger_touch_agent:verifyTouchProtocol
echo "Built: $jogger_build_dir/app_pojavlauncher/build/outputs/apk/release/app_pojavlauncher-release.apk"
