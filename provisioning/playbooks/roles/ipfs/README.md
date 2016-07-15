# ansible-ipfs

[![Build Status](https://travis-ci.org/insanity54/ansible-ipfs.svg?branch=master)](https://travis-ci.org/insanity54/ansible-ipfs)

## Links

* [GitHub Repo](https://github.com/insanity54/ansible-ipfs/)
* [Ansible Galaxy page](https://galaxy.ansible.com/insanity54/ipfs/)


## Description

Quick and easy playbook for installing an IPFS node as a service

~~If you are looking for a way of using Ansible to deploy and manage one or more ipfs gateways meant for pinning and serving content, you might like my other project, [ipfs-kloud](https://github.com/insanity54/ipfs-kloud)~~ (abandoned project)


## What it does

* extract and copy pre-built ipfs binary to /usr/local/bin/ipfs
* create ipfs system service (files/ipfs.conf)
* initialize IPFS
* run ipfs service as user ipfs
  * ipfs daemon exposes ONLY the swarm port 4001 to the world.



## What it does not do

* set up FUSE


## Future potential

* run on centos (tests and install logic is here, but I got hung up on systemd)
* use gx to download and install ipfs
* use gx api to make a GitHub README.md badge showing whether or not this repo is using the latest stable ipfs


## Credits

* [Inter Planetary File System](https://ipfs.io/)
* [/etc/init.d template](https://github.com/fhd/init-script-template)

## Contributing

Pull requests and new issues are very much appreciated! Thanks
