#############################################################################
 # Copyright (c) 2017 Cisco and/or its affiliates.
 # Licensed under the Apache License, Version 2.0 (the "License");
 # you may not use this file except in compliance with the License.
 # You may obtain a copy of the License at:
 #
 #     http://www.apache.org/licenses/LICENSE-2.0
 #
 # Unless required by applicable law or agreed to in writing, software
 # distributed under the License is distributed on an "AS IS" BASIS,
 # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 # See the License for the specific language governing permissions and
 # limitations under the License.
 ##############################################################################

#!/bin/bash

set -ex

OS=`echo $OS | tr '[:upper:]' '[:lower:]'`
export BASE_DIR=`pwd`

mkdir -p src
cd src


if [ ! -d hicn ]; then
        echo "hicn not found"
        git clone -b devel ssh://git@bitbucket-eng-gpk1.cisco.com:7999/icn/hicn.git
fi

if [ ! -d hproxy ]; then
        echo "hproxy not found"
        git clone ssh://git@bitbucket-eng-gpk1.cisco.com:7999/ngl/hproxy.git
fi

if [ ! -d cframework ]; then
	echo "cframework not found"
	git clone -b cframework/master https://gerrit.fd.io/r/cicn cframework
fi

cd ..

if [ ! -d hproxy-aar ]; then
	echo "hproxy-aar not found"
	git clone ssh://git@bitbucket-eng-gpk1.cisco.com:7999/ngl/hproxy-aar.git
fi
