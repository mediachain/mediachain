#!/bin/bash

type vagrant >/dev/null 2>&1 || { echo >&2 "Please install vagrant: https://www.vagrantup.com/"; exit 1; }


SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."

export VAGRANT_CWD="${PROJECT_ROOT}/provisioning"

vagrant $@
