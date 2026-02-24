#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

MODE="headless"
SETUP_ONLY="false"

usage() {
  cat <<EOF
Usage:
  ./run-aniflow.sh [--gui] [--setup-only]

Options:
  --gui         Jalankan dengan DISPLAY host (butuh X server).
  --setup-only  Hanya setup dependency Ubuntu + Maven cache.
EOF
}

for arg in "$@"; do
  case "$arg" in
    --gui)
      MODE="gui"
      ;;
    --setup-only)
      SETUP_ONLY="true"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Argumen tidak dikenal: $arg"
      usage
      exit 1
      ;;
  esac
done

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
TMP_DIR="$PROJECT_DIR/.tmp"
MAVEN_MARKER="$TMP_DIR/maven-offline.ready"
UBUNTU_ROOTFS="/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/ubuntu"

mkdir -p "$TMP_DIR"

if ! command -v proot-distro >/dev/null 2>&1; then
  echo "proot-distro tidak ditemukan. Install dulu: pkg install proot-distro"
  exit 1
fi

run_ubuntu() {
  local cmd="$1"
  proot-distro login ubuntu --shared-tmp -- /bin/bash -lc "
    set -e
    export PATH=/usr/sbin:/usr/bin:/sbin:/bin
    $cmd
  "
}

ensure_ubuntu() {
  if [ ! -d "$UBUNTU_ROOTFS" ]; then
    echo "Ubuntu belum ter-install. Menjalankan: proot-distro install ubuntu"
    proot-distro install ubuntu
  fi

  if ! proot-distro login ubuntu -- /bin/true >/dev/null 2>&1; then
    echo "Ubuntu terdeteksi tapi gagal dijalankan via proot-distro."
    exit 1
  fi
}

ensure_packages() {
  if run_ubuntu "command -v java >/dev/null 2>&1 && command -v mvn >/dev/null 2>&1 && command -v xvfb-run >/dev/null 2>&1 && command -v xauth >/dev/null 2>&1" >/dev/null 2>&1; then
    return
  fi

  echo "Install dependency Ubuntu: openjdk-21-jdk maven xvfb xauth"
  run_ubuntu "
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -y
    apt-get install -y openjdk-21-jdk maven xvfb xauth ca-certificates
  "
}

warm_maven_cache() {
  if [ ! -f "$MAVEN_MARKER" ] || [ "$PROJECT_DIR/pom.xml" -nt "$MAVEN_MARKER" ]; then
    echo "Prefetch dependency Maven (sekali jalan)..."
    run_ubuntu "
      cd '$PROJECT_DIR'
      mvn -B -q -DskipTests dependency:go-offline
    "
    date -u +"%Y-%m-%dT%H:%M:%SZ" > "$MAVEN_MARKER"
  fi
}

ensure_ubuntu
ensure_packages
warm_maven_cache

if [ "$SETUP_ONLY" = "true" ]; then
  echo "Setup selesai."
  exit 0
fi

if [ "$MODE" = "gui" ]; then
  HOST_DISPLAY="${DISPLAY:-}"
  if [ -z "$HOST_DISPLAY" ]; then
    echo "DISPLAY belum diset. Fallback ke mode headless."
    MODE="headless"
  fi
fi

if [ "$MODE" = "gui" ]; then
  echo "Menjalankan AniFlow mode GUI (DISPLAY=$HOST_DISPLAY)"
  run_ubuntu "
    export DISPLAY='$HOST_DISPLAY'
    export LIBGL_ALWAYS_SOFTWARE=1
    export JAVA_TOOL_OPTIONS='-Dprism.order=sw'
    cd '$PROJECT_DIR'
    mvn -B -DskipTests -Dstyle.color=never javafx:run
  "
else
  echo "Menjalankan AniFlow mode headless (Xvfb)."
  run_ubuntu "
    export LIBGL_ALWAYS_SOFTWARE=1
    export JAVA_TOOL_OPTIONS='-Dprism.order=sw'
    cd '$PROJECT_DIR'
    xvfb-run -a -s '-screen 0 1366x900x24' mvn -B -DskipTests -Dstyle.color=never javafx:run
  "
fi
