#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
./gradlew packageUbuntuDeb

echo
echo "Pacote criado em: build/packages/linux"
