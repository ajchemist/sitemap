#!/bin/bash


set -euo pipefail


clojure -A:provided:test -m cognitect.test-runner
clojure -A:provided:test -m script.build
mvn deploy:deploy-file -DpomFile="pom.xml" -Dfile="target/package.jar" -Dpackaging=jar -DrepositoryId="clojars" -Durl="https://clojars.org/repo"
