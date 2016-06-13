#!/bin/bash

type vagrant >/dev/null 2>&1 || { echo >&2 "Please install vagrant: https://www.vagrantup.com/"; exit 1; }

export VAGRANT_CWD=${PWD}/provisioning

vagrant $@