#!/bin/sh

# Summary: SHACL validation of example .ttl agains DCAT-AP SHACL shapes

# Requires APACHE Jena shacl application https://jena.apache.org/documentation/shacl/

DATA=./resources/cars/expected/cars.ttl
SHACLshapes=DCAT-AP/releases/3.0.0/shacl/dcat-ap-SHACL.ttl
DCATrepo=https://github.com/SEMICeu/DCAT-AP.git 
DCATbranch=3.0.0

# download DCAT-AP repo to test/DCAT-AP, in order to get the shacl shapes
if ! [ -d "DCAT-AP" ]; then
    git clone --depth 1 --branch $DCATbranch $DCATrepo
fi


# validate
shacl validate --shapes $SHACLshapes --data $DATA

