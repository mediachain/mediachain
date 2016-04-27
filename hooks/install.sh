#!/bin/bash

script_dir=$(dirname $0)

git_dir=".git"

copy_hook() {
    local from="$script_dir/$1"
    local to="$git_dir/hooks/$2"
    echo "Copying $from to $to" >&2
    cp $from $to
    chmod +x $to
}

copy_hook post-merge-checkout post-merge
copy_hook post-merge-checkout post-checkout

exit 0

