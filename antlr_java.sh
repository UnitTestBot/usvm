#!/bin/bash

generate() {
  if ! cd templates-db/antlr; then
    exit 1
  fi

  java -jar /home/stepanov/psi-fuzz/templates-db/libs/antlr-4.13.1-complete.jar -o /home/stepanov/psi-fuzz/templates-db/src/generated/com/spbpu/bbfinfrastructure/template/parser -Dlanguage=Java -package com.spbpu.bbfinfrastructure.template.parser /home/stepanov/psi-fuzz/templates-db/antlr/*.g4
  cp /home/stepanov/psi-fuzz/templates-db/src/generated/com/spbpu/bbfinfrastructure/template/parser/TemplateLexer.tokens /home/stepanov/psi-fuzz/templates-db
}

clean() {
  rm -rf templates-db/src/generated
  rm templates-db/antlr/TemplateLexer.tokens
}

case $1 in
  --generate)
    generate
    ;;
  --clean)
    clean
    ;;
  *)
    echo "FIX ERROR DESCRIPTION"
    ;;
esac
