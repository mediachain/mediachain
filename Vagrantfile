# -*- mode: ruby -*-
# vi: set ft=ruby :

# All Vagrant configuration is done below. The "2" in Vagrant.configure
# configures the configuration version (we support older styles for
# backwards compatibility). Please don't change it unless you know what
# you're doing.
Vagrant.configure(2) do |config|
  # Use a box compatible with both virtualbox and vmware_fusion providers
  config.vm.box = "phusion/ubuntu-14.04-amd64"

  # Forward transactor rpc service port 10001
  config.vm.network "forwarded_port", guest: 10001, host: 10001
  
  # Forward port for local dynamodb (for development)
  config.vm.network "forwarded_port", guest: 8000, host: 8000

  # Forward jvm debugger port 5005 on the guest to 5006 on the host
  config.vm.network "forwarded_port", guest: 5005, host: 5006

  # Set ram, etc for vm providers
  config.vm.provider "virtualbox" do |vb|
    vb.memory = "2048"
  end

  config.vm.provider "vmware_fusion" do |vm|
    vm.memory = "2048"
  end

  # Set hostname for vm
  config.vm.define "transactor"

  # Ansible config
  config.vm.provision :ansible do |ansible|
    ansible.limit = "transactors"
    ansible.playbook = "meta/dev.yml"
    ansible.groups = { "transactors" => ["transactor"] }
  end
end
