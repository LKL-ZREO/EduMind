#!/bin/sh
set -eu

if [ "$(id -u)" -ne 0 ]; then
    echo "ERROR: run this script as root: sudo /bin/sh scripts/prepare-ubuntu.sh" >&2
    exit 1
fi

if [ ! -r /etc/os-release ]; then
    echo "ERROR: /etc/os-release was not found" >&2
    exit 1
fi

. /etc/os-release
case "${ID:-}" in
    ubuntu|debian) ;;
    *)
        echo "ERROR: this script supports Ubuntu and Debian only (detected: ${ID:-unknown})" >&2
        exit 1
        ;;
esac

echo "Installing base packages..."
apt-get update
apt-get install -y ca-certificates curl git gnupg openssl

if ! command -v docker >/dev/null 2>&1; then
    echo "Installing Docker Engine from Docker's official apt repository..."
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL "https://download.docker.com/linux/${ID}/gpg" \
        -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc

    ARCH="$(dpkg --print-architecture)"
    CODENAME="${VERSION_CODENAME:-}"
    if [ -z "${CODENAME}" ]; then
        echo "ERROR: VERSION_CODENAME is missing from /etc/os-release" >&2
        exit 1
    fi
    printf '%s\n' \
        "deb [arch=${ARCH} signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/${ID} ${CODENAME} stable" \
        > /etc/apt/sources.list.d/docker.list
    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi

systemctl enable --now docker
docker info >/dev/null
docker compose version

if ! swapon --show=NAME --noheadings | grep -qx '/swapfile'; then
    if [ -e /swapfile ]; then
        echo "ERROR: /swapfile exists but is not active; inspect it before continuing" >&2
        exit 1
    fi
    echo "Creating a 4 GiB swap file..."
    fallocate -l 4G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
fi

if ! grep -q '^/swapfile[[:space:]]' /etc/fstab; then
    printf '%s\n' '/swapfile none swap sw 0 0' >> /etc/fstab
fi

printf '%s\n' 'vm.swappiness=10' > /etc/sysctl.d/99-edumind.conf
sysctl --system >/dev/null

echo "Host preparation completed."
free -h
df -h /
