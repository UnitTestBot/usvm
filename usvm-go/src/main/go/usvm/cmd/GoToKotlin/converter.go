package GoToKotlin

import (
	"encoding/binary"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"reflect"
	"strconv"
	"strings"

	"GoToKotlin/constants"
	"GoToKotlin/ssa_helpers"
)

type Converter struct {
	DirPath string
	genName int
	ptrCnt  int
	ptrNow  bool
	currPtr uintptr

	used     map[string]bool
	usedPtr  map[uintptr]map[string]int
	inlineId map[string]int

	isJacoSupported bool

	doneWriting chan struct{}

	bufferSize int
}

func CreateConverter(dirPath string, isJacoSupported bool) Converter {
	return Converter{
		DirPath:         dirPath,
		genName:         0,
		used:            map[string]bool{},
		usedPtr:         map[uintptr]map[string]int{},
		inlineId:        map[string]int{},
		isJacoSupported: isJacoSupported,
		doneWriting:     make(chan struct{}, 1),
		bufferSize:      MAXLEN,
	}
}

func (conv *Converter) SetBufferSize(size int) {
	conv.bufferSize = size
}

func convertBaseType(goName string) string {
	switch goName {
	case "int", "int32", "uint16", "rune":
		return "Long"
	case "int16", "uint8", "byte":
		return "Long"
	case "int64", "uint32":
		return "Long"
	case "uint64", "uint":
		return "ULong"
	case "float32":
		return "Float"
	case "float64":
		return "Double"
	case "string":
		return "String"
	case "bool":
		return "Boolean"
	case "interface {}":
		return "Any"
	}
	return "Long"
}

func (conv *Converter) getInnerStructs(fieldType reflect.Type, kind reflect.Kind) (string, error) {
	switch kind {
	case reflect.Func:
		// skip
		return "", nil
	case reflect.Interface:
		fieldVal := reflect.Zero(fieldType)

		if !fieldVal.IsNil() {
			fieldType = fieldType.Elem()
			kind = fieldType.Kind()

			conv.getInnerStructs(fieldType, kind)
		}

		return "Any", nil
	case reflect.Pointer:
		fieldType = fieldType.Elem()
		kind = fieldType.Kind()

		return conv.getInnerStructs(fieldType, kind)
	case reflect.Slice, reflect.Array:
		fieldType = fieldType.Elem()
		kind = fieldType.Kind()

		name, _ := conv.getInnerStructs(fieldType, kind)

		return "List<" + name + ">", nil
	case reflect.Map:
		keyType := fieldType.Key()
		keyKind := keyType.Kind()

		valType := fieldType.Elem()
		valKind := valType.Kind()

		keyName, _ := conv.getInnerStructs(keyType, keyKind)
		valName, _ := conv.getInnerStructs(valType, valKind)

		return fmt.Sprintf("Map<%s, %s>", keyName, valName), nil
	case reflect.Struct:
		name := fieldType.String()

		if strings.Contains(name, "struct {") {
			id, ok := conv.inlineId[name]
			if !ok {
				id = conv.genName
				conv.genName++
				conv.inlineId[name] = id
			}
			name = fmt.Sprintf("generatedInlineStruct_%03d", id)
		}

		name = strings.ReplaceAll(name, ".", "_")

		if strings.Contains(name, "/") {
			return "", fmt.Errorf("name of the structure contains the '/' symbol")
		}
		if conv.used[name] {
			return name, nil
		}
		conv.used[name] = true

		filePath := filepath.Join(".", conv.DirPath, strconv.Itoa(len(conv.used))+"_"+name+".kt")
		file, err := os.Create(filePath)
		if err != nil {
			return "", err
		}
		defer file.Close()

		structDef := constants.PackageLine + readerImports

		if conv.isJacoSupported {
			structDef = ssa_helpers.AddImportAndDefinition(structDef, name)
		} else {
			structDef += fmt.Sprintf(constants.StructDefinition, name)
		}

		deserializer := fmt.Sprintf(deserializeFunStart, name, name, name, name)

		for i := 0; i < fieldType.NumField(); i++ {
			field := fieldType.Field(i)
			innerFieldType := field.Type

			if field.Name == "_" {
				// This is a blank identifier, no need to send.
				continue
			}
			if field.Name == "object" {
				// Invalid kotlin name.
				field.Name = "Object"
			}
			if field.Name == "val" {
				// Invalid kotlin name.
				field.Name = "Val"
			}
			if strings.Contains(innerFieldType.String(), "/") {
				continue
			}

			innerKind := innerFieldType.Kind()

			ktName, _ := conv.getInnerStructs(innerFieldType, innerKind)

			if ktName == "" {
				// unsupported, ex functions
				continue
			}

			structDef += fmt.Sprintf(structField,
				field.Name, ktName)
			deserializer += fmt.Sprintf(deserializeField, field.Name, ktName)

			if innerKind == reflect.Func {
				continue
			}

			conv.getInnerStructs(innerFieldType, innerKind)
		}

		if conv.isJacoSupported {
			structDef = ssa_helpers.AddInterfaceFunctions(structDef, name)
		}

		structDef += "}\n\n"
		file.Write([]byte(structDef))

		deserializer += deserializeEnd
		file.Write([]byte(deserializer))

		return name, nil
	default:
		return convertBaseType(kind.String()), nil
	}
}

func (conv *Converter) convertStruct(structure interface{}) (string, error) {
	structType := reflect.TypeOf(structure)
	structKind := structType.Kind()

	return conv.getInnerStructs(structType, structKind)
}

func getFieldString(conv *Converter, startString string) (string, bool) {
	skip := false

	if conv.ptrNow && conv.currPtr != 0 {
		var id int

		nameToID, ok := conv.usedPtr[conv.currPtr]

		if !ok {
			conv.usedPtr[conv.currPtr] = make(map[string]int)
			id = conv.ptrCnt
			conv.ptrCnt++

			conv.usedPtr[conv.currPtr][startString] = id
		} else {
			if id, ok = nameToID[startString]; ok {
				skip = true
			} else {
				id = conv.ptrCnt
				conv.ptrCnt++

				conv.usedPtr[conv.currPtr][startString] = id
			}
		}

		startString += " " + strconv.Itoa(id)

		conv.ptrNow = false
	}
	conv.ptrNow = false

	startString += "\n"
	return startString, skip
}

func (conv *Converter) fillInnerStructs(fieldType reflect.Type, fieldVal reflect.Value, kind reflect.Kind, fillerFile io.Writer) {
	switch kind {
	case reflect.Func:
		// skip
		return
	case reflect.Interface:
		var realVal reflect.Value

		if fieldVal.IsValid() {
			realVal = fieldVal.Elem()
		}

		if realVal.Kind() != 0 {
			fieldVal = realVal
			fieldType = fieldVal.Type()
			kind = fieldType.Kind()

			conv.fillInnerStructs(fieldType, fieldVal, kind, fillerFile)
		} else {
			conv.ptrNow = false
			binary.Write(fillerFile, binary.LittleEndian, 4)
			binary.Write(fillerFile, binary.LittleEndian, []byte("nil\n"))
		}
		return

	case reflect.Pointer:
		conv.ptrNow = true
		conv.currPtr = uintptr(fieldVal.UnsafePointer())

		fieldType = fieldType.Elem()
		fieldVal = fieldVal.Elem()
		kind = fieldType.Kind()

		conv.fillInnerStructs(fieldType, fieldVal, kind, fillerFile)

	case reflect.Slice, reflect.Array:
		arrayString, skip := getFieldString(conv, "array")

		binary.Write(fillerFile, binary.LittleEndian, len(arrayString))
		binary.Write(fillerFile, binary.LittleEndian, []byte(arrayString))

		if skip {
			return
		}

		fieldType = fieldType.Elem()
		kind = fieldType.Kind()

		if fieldVal.Kind() != 0 {
			for i := 0; i < fieldVal.Len(); i++ {
				conv.fillInnerStructs(fieldType, fieldVal.Index(i), kind, fillerFile)
			}
		}

		binary.Write(fillerFile, binary.LittleEndian, 4)
		binary.Write(fillerFile, binary.LittleEndian, []byte("end\n"))

	case reflect.Map:
		mapString, skip := getFieldString(conv, "map")

		binary.Write(fillerFile, binary.LittleEndian, len(mapString))
		binary.Write(fillerFile, binary.LittleEndian, []byte(mapString))

		if skip {
			return
		}

		keyType := fieldType.Key()
		keyKind := keyType.Kind()

		valType := fieldType.Elem()
		valKind := valType.Kind()

		if fieldVal.Kind() != 0 {
			for _, k := range fieldVal.MapKeys() {
				conv.fillInnerStructs(keyType, k, keyKind, fillerFile)
				conv.fillInnerStructs(valType, fieldVal.MapIndex(k), valKind, fillerFile)
			}
		}

		binary.Write(fillerFile, binary.LittleEndian, 4)
		binary.Write(fillerFile, binary.LittleEndian, []byte("end\n"))
	case reflect.Struct:
		name := fieldType.String()

		if strings.Contains(name, "struct {") {
			id, ok := conv.inlineId[name]
			if !ok {
				id = conv.genName
				conv.genName++
				conv.inlineId[name] = id
			}
			name = fmt.Sprintf("generatedInlineStruct_%03d", id)
		}

		name = strings.ReplaceAll(name, ".", "_")

		if _, ok := conv.used[name]; !ok {
			conv.convertStruct(reflect.Zero(fieldType).Interface())
		}

		structString, skip := getFieldString(conv, name)

		binary.Write(fillerFile, binary.LittleEndian, len(structString))
		binary.Write(fillerFile, binary.LittleEndian, []byte(structString))

		if skip {
			return
		}

		if fieldVal.Kind() != 0 {
			for i := 0; i < fieldType.NumField(); i++ {
				field := fieldType.Field(i)
				innerFieldType := field.Type

				if field.Name == "_" {
					// This is a blank identifier, no need to send.
					continue
				}
				if field.Name == "object" {
					// Invalid kotlin name.
					field.Name = "Object"
				}
				if field.Name == "val" {
					// Invalid kotlin name.
					field.Name = "Val"
				}
				if strings.Contains(innerFieldType.String(), "/") {
					continue
				}

				innerFieldVal := fieldVal.Field(i)
				innerKind := innerFieldVal.Kind()

				if innerKind == reflect.Func {
					continue
				}

				binary.Write(fillerFile, binary.LittleEndian, len(field.Name)+1)
				binary.Write(fillerFile, binary.LittleEndian, []byte(field.Name+" "))

				conv.fillInnerStructs(innerFieldType, innerFieldVal, innerKind, fillerFile)
			}
		}

		binary.Write(fillerFile, binary.LittleEndian, 4)
		binary.Write(fillerFile, binary.LittleEndian, []byte("end\n"))
	default:
		conv.ptrNow = false

		ktType := convertBaseType(kind.String())
		defaultVal := "0"
		if ktType == "Any" {
			defaultVal = "nil"
		}

		filled := ""
		if fieldVal.IsValid() {
			switch kind {
			case reflect.String:
				filled = fmt.Sprintf("%s\n%q\n", ktType, fieldVal.String())
			case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
				filled = fmt.Sprintf("%s\n%v\n", ktType, strconv.FormatInt(fieldVal.Int(), 10))
			case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
				filled = fmt.Sprintf("%s\n%v\n", ktType, strconv.FormatUint(fieldVal.Uint(), 10))
			case reflect.Bool:
				filled = fmt.Sprintf("%s\n%v\n", ktType, strconv.FormatBool(fieldVal.Bool()))
			default:
				filled = fmt.Sprintf("%s\n%q\n", ktType, fieldVal)
			}
		} else {
			filled = fmt.Sprintf("%s\n%s\n", ktType, defaultVal)
		}
		binary.Write(fillerFile, binary.LittleEndian, len(filled))
		binary.Write(fillerFile, binary.LittleEndian, []byte(filled))
	}
}

func (conv *Converter) fillValues(structure interface{}, fillerFile io.Writer) error {
	structVal := reflect.ValueOf(structure)
	structType := reflect.TypeOf(structure)
	structKind := structType.Kind()

	acc := NewAccumulator(fillerFile, conv.bufferSize)

	conv.fillInnerStructs(structType, structVal, structKind, acc)

	acc.WriteRest()

	return nil
}

func (conv *Converter) generateBaseDeserializers() error {
	filePath := filepath.Join(".", conv.DirPath, "baseDeserializers.kt")
	file, err := os.Create(filePath)
	if err != nil {
		return err
	}
	des := constants.PackageLine + readerImports + readBaseTypes
	_, err = file.Write([]byte(des))
	return err
}

func (conv *Converter) generateEntrypoint() error {
	filePath := filepath.Join(".", conv.DirPath, "Entrypoint.kt")
	file, err := os.Create(filePath)
	if err != nil {
		return err
	}
	start := constants.PackageLine + readerImports + kotlinConstants

	for name := range conv.used {
		start += ",\n"
		start += fmt.Sprintf(funcMapLine, name, name)
	}

	start += "\n)\n" + entrypoint

	_, err = file.Write([]byte(start))
	return err
}

func (conv *Converter) GenerateStructures(structure interface{}) error {
	if conv.isJacoSupported {
		err := ssa_helpers.GenerateJacoStructs(conv.DirPath)
		if err != nil {
			return err
		}
	}

	_, err := conv.convertStruct(structure)
	if err != nil {
		return err
	}

	err = conv.generateBaseDeserializers()
	if err != nil {
		return err
	}

	err = conv.generateEntrypoint()

	return err
}

func (conv *Converter) FillStructures(fillerFile io.Writer, structure interface{}) error {
	err := conv.fillValues(structure, fillerFile)
	if err != nil {
		return err
	}

	if conv.isJacoSupported {
		err = ssa_helpers.AddStubs(conv.DirPath, conv.used)
		if err != nil {
			return err
		}
		err := ssa_helpers.GenerateJacoStructs(conv.DirPath)
		if err != nil {
			return err
		}
	}

	err = conv.generateBaseDeserializers()
	if err != nil {
		return err
	}

	err = conv.generateEntrypoint()

	return err
}
