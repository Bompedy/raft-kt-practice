#!/bin/bash

cd ~/raft-kt-practice/
sudo chmod +x gradlew
git pull
./gradlew build
cd Server
java -jar raft-practice.jar
