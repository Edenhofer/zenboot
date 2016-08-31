#!/bin/bash

./grailsw -plain-output -non-interactive war
docker build . -t hybris/zenboot --build-arg VERSION=ignored --build-arg ZENBOOT_WAR=target/zenboot.war
