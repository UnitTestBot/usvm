package ssa_helpers

import (
	"fmt"
	"os"
	"path/filepath"
	"slices"

	"GoToKotlin/constants"
)

const (
	instruction_ = iota
	expression_
	value_
	expressionValue_
	type_
	instructionValue_
	call_
	project_
	method_

	none_ = -1
)

func GenerateJacoStructs(dirPath string) error {
	filePath := filepath.Join(".", dirPath, "ssaToJacoExpr.kt")
	file, err := os.Create(filePath)
	if err != nil {
		return err
	}

	_, err = file.Write([]byte(constants.PackageLine + ssaToJacoExpr))
	if err != nil {
		return err
	}

	filePath = filepath.Join(".", dirPath, "ssaToJacoValue.kt")
	file, err = os.Create(filePath)
	if err != nil {
		return err
	}

	_, err = file.Write([]byte(constants.PackageLine + ssaToJacoValue))
	if err != nil {
		return err
	}

	filePath = filepath.Join(".", dirPath, "ssaToJacoInst.kt")
	file, err = os.Create(filePath)
	if err != nil {
		return err
	}

	_, err = file.Write([]byte(constants.PackageLine + ssaToJacoInst))
	if err != nil {
		return err
	}

	filePath = filepath.Join(".", dirPath, "ssaToJacoType.kt")
	file, err = os.Create(filePath)
	if err != nil {
		return err
	}

	_, err = file.Write([]byte(constants.PackageLine + ssaToJacoType))
	if err != nil {
		return err
	}

	filePath = filepath.Join(".", dirPath, "ssaToJacoProject.kt")
	file, err = os.Create(filePath)
	if err != nil {
		return err
	}

	_, err = file.Write([]byte(constants.PackageLine + ssaToJacoProject))
	if err != nil {
		return err
	}

	filePath = filepath.Join(".", dirPath, "ssaToJacoMethod.kt")
	file, err = os.Create(filePath)
	if err != nil {
		return err
	}

	_, err = file.Write([]byte(constants.PackageLine + ssaToJacoMethod))
	if err != nil {
		return err
	}

	filePath = filepath.Join(".", dirPath, "ssa_CallExpr.kt")
	file, err = os.Create(filePath)
	if err != nil {
		return err
	}

	_, err = file.Write([]byte(constants.PackageLine + ssaCallExpr))
	return err
}

func getJacoInterface(name string) int {
	if name == "ssa_Call" {
		return call_
	}
	if name == "ssa_Program" {
		return project_
	}
	if name == "ssa_Function" {
		return method_
	}
	if slices.Contains(instructions, name) {
		if slices.Contains(values, name) {
			return instructionValue_
		}
		return instruction_
	}
	if slices.Contains(types, name) {
		return type_
	}

	if slices.Contains(expressions, name) {
		if slices.Contains(values, name) {
			return expressionValue_
		}
		return expression_
	}

	if slices.Contains(values, name) {
		return value_
	}
	return -1
}

func AddImportAndDefinition(structDef, name string) string {
	structDef += jacoImport

	iface := getJacoInterface(name)

	if iface == none_ {
		structDef += fmt.Sprintf(constants.StructDefinition, name)
	} else {
		structDef += fmt.Sprintf(structDefinitionWithInterface, name, ifaceToStringMap[iface])
	}

	return structDef
}

func AddInterfaceFunctions(structDef, name string) string {
	if extra, ok := nameToExtra[name]; ok {
		structDef += extra
	}

	return structDef
}

func AddStubs(dirPath string, used map[string]bool) error {
	for name, stub := range nameToStub {
		if used[name] {
			continue
		}

		filePath := filepath.Join(".", dirPath, name+".kt")
		file, err := os.Create(filePath)
		if err != nil {
			return err
		}

		file.Write([]byte(stub))
	}

	return nil
}
