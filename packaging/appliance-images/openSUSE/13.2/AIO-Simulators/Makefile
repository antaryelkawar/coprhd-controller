#
# Copyright 2016 EMC Corporation
# All Rights Reserved
#

##################################################
#  Setup
##################################################
WORKSPACE := $(shell dirname $(MAKEFILE_LIST))
NAME := AIO-Simulators
VERSION := 2.5.0.0
JOB := 999
VENDOR := EMC
ISO_FILE := openSUSE-13.2-DVD-x86_64.iso
ISO_URL := http://download.opensuse.org/distribution/13.2/iso/$(ISO_FILE)

##################################################
#  Target files/folders
##################################################
IMAGE_DIR := $(WORKSPACE)/image/$(JOB)
OUTPUT_DIR := $(WORKSPACE)/output/$(JOB)
BUILD_DIR := $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)
VMDK := $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
OVF := $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)/$(NAME).x86_64-$(VERSION).$(JOB).ovf
ZYPPER_PARAMS := --reposd-dir $(WORKSPACE)/zypper/reposd --cache-dir $(WORKSPACE)/zypper/cache --solv-cache-dir $(WORKSPACE)/zypper/solv --pkg-cache-dir $(WORKSPACE)/zypper/pkg

.DEFAULT: all
.PHONY: downloadISO downloadRPM createREPO appliance clean destroy
all: appliance

##################################################
#  Functions
##################################################
define targetLog
	@echo "###############################################";
	@echo "$(1)[$@] on $$( date )";
	@echo "###############################################";
endef

##################################################
#  Targets
##################################################
downloadISO:
	$(call targetLog,+)
	mkdir -p $(WORKSPACE)/iso/mount
	[ ! -f /disks/adgbuild/OPENSUSE13.2/$(ISO_FILE) ] || ln -fs /disks/adgbuild/OPENSUSE13.2/$(ISO_FILE) $(WORKSPACE)/iso/
	[ -s $(WORKSPACE)/iso/$(ISO_FILE) ] || wget $(ISO_URL) --continue --progress=bar --output-document=$(WORKSPACE)/iso/$(ISO_FILE)

	wget $(ISO_URL) --continue --progress=bar --output-document=$(WORKSPACE)/iso/$(ISO_FILE)
	$(call targetLog,-)

downloadRPM:
	$(call targetLog,+)
	rm -fr $(WORKSPACE)/zypper/reposd
	mkdir -p $(WORKSPACE)/zypper/{reposd,cache,solv,pkg}
	@# openSUSE's OSS packages
	zypper $(ZYPPER_PARAMS) addrepo --name repo-oss --no-gpgcheck --no-check http://download.opensuse.org/distribution/13.2/repo/oss repo-oss.repo
	zypper $(ZYPPER_PARAMS) refresh
	zypper $(ZYPPER_PARAMS) --non-interactive download python-py python-requests python-setools python-iniparse python-gpgme
	zypper $(ZYPPER_PARAMS) --non-interactive download libserf-devel fribidi fbiterm openssh-fips perl-Tk setools-libs sshpass sysstat ca-certificates-cacert yum keepalived
	@# openSUSE's non-OSS packages
	zypper $(ZYPPER_PARAMS) addrepo --name devel-languages-python --no-gpgcheck --no-check http://download.opensuse.org/repositories/devel:/languages:/python/openSUSE_13.2 devel-languages-python.repo
	zypper $(ZYPPER_PARAMS) addrepo --name server-monitoring --no-gpgcheck --no-check http://download.opensuse.org/repositories/server:/monitoring/openSUSE_13.2 server-monitoring.repo
	zypper $(ZYPPER_PARAMS) addrepo --name home-seife-testing --no-gpgcheck --no-check http://download.opensuse.org/repositories/home:/seife:/testing/openSUSE_13.2 home-seife-testing.repo
	zypper $(ZYPPER_PARAMS) refresh
	$(call targetLog,-)


createREPO: | downloadRPM 
	$(call targetLog,+)
	rm -fr $(WORKSPACE)/zypper/repo
	mkdir -p $(WORKSPACE)/zypper/repo
	make --directory=$(WORKSPACE)/../configure-network
	cp -f $(WORKSPACE)/../configure-network/RPMS/noarch/* $(WORKSPACE)/zypper/repo/
	find $(WORKSPACE)/zypper/pkg -iname \*.rpm -exec cp -f {} $(WORKSPACE)/zypper/repo/ \;
	createrepo $(WORKSPACE)/zypper/repo
	$(call targetLog,-)

clean:
	$(call targetLog,+)
	rm -fr $(WORKSPACE)/{build/$(NAME)-$(VERSION).$(JOB),image/$(JOB),output,zypper/repo,zypper/reposd}
	$(call targetLog,-)

destroy:
	$(call targetLog,+)
	rm -fr $(WORKSPACE)/{iso,build,image,output,zypper}
	$(call targetLog,-)

appliance: | $(OVF)

$(VMDK): | downloadISO createREPO
	$(call targetLog,+)
	bash $(WORKSPACE)/patch.sh
	cp -f $(WORKSPACE)/templates/config.orig.xml $(WORKSPACE)/templates/config.xml
	sed -i "s|__NAME__|$(NAME)|g" $(WORKSPACE)/templates/config.xml
	sed -i "s|__ISO__|$(WORKSPACE)/iso/mount|g" $(WORKSPACE)/templates/config.xml
	sed -i "s|__REPO__|$(WORKSPACE)/zypper/repo|g" $(WORKSPACE)/templates/config.xml
	sed -i "s|__VERSION__|$(VERSION).$(JOB)|g" $(WORKSPACE)/templates/config.xml
	-umount $(IMAGE_DIR)/var/cache/kiwi
	-mount $(WORKSPACE)/iso/$(ISO_FILE) $(WORKSPACE)/iso/mount
	rm -fr $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)
	mkdir -p $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)
	mkdir -p $(WORKSPACE)/image
	mkdir -p $(WORKSPACE)/build
	mkdir -p $(WORKSPACE)/output
	kiwi --verbose 3 --yes --prepare $(WORKSPACE)/templates --add-profile appliance --force-new-root --root $(IMAGE_DIR)
	kiwi --verbose 3 --yes --create $(IMAGE_DIR) --add-profile appliance --type vmx --destdir $(OUTPUT_DIR)
	-umount $(WORKSPACE)/iso/mount
	$(call targetLog,-)

$(OVF): | $(VMDK)
	$(call targetLog,+)
	cp -f $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk.bak
	VBoxManage createvm --name "$(NAME)-$(VERSION).$(JOB)" --register
	VBoxManage modifyvm "$(NAME)-$(VERSION).$(JOB)" --memory 8192 --cpus 1 --acpi on --boot1 dvd --nic1 bridged --bridgeadapter1 eth0 --ostype Linux_64
	VBoxManage storagectl "$(NAME)-$(VERSION).$(JOB)" --name "IDE Controller" --add ide
	VBoxManage storageattach "$(NAME)-$(VERSION).$(JOB)" --storagectl "IDE Controller" --port 0 --device 0 --type hdd --medium $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
	VBoxManage export "$(NAME)-$(VERSION).$(JOB)" --vsys 0 --product $(NAME) --version $(VERSION).$(JOB) --vendor $(VENDOR) --output $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)/$(NAME).x86_64-$(VERSION).$(JOB).ovf
	VBoxManage unregistervm "$(NAME)-$(VERSION).$(JOB)" --delete
	mv -f $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk.bak $(OUTPUT_DIR)/$(NAME).x86_64-$(VERSION).$(JOB).vmdk
	bash $(WORKSPACE)/configure.sh $(WORKSPACE)/build/$(NAME)-$(VERSION).$(JOB)/$(NAME).x86_64-$(VERSION).$(JOB).ovf
	$(call targetLog,-)
