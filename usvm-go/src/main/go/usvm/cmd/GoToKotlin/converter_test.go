package GoToKotlin_test

import (
	"os"
	"os/exec"
	"slices"
	"testing"

	"GoToKotlin"
)

type nonEmptyInterface interface {
	SomeFunc() bool
}

type someStruct struct {
	fl1 string
}

type interfaceImpl struct {
	fl21 []string
}

func (l interfaceImpl) SomeFunc() bool {
	return true
}

type customType int16

type innerStruct struct {
	if1 int
	if2 bool
}

type BigStruct struct {
	innerStruct
	_ interfaceImpl

	Field1      int
	FieldString string
	field3      bool
	F4          byte
	f5          *int16
	f6          []uint
	f7          *[]uint
	f8          interface{}
	f85         interface{}
	f9          someStruct
	f10         nonEmptyInterface
	f11         map[string]bool
	f12         map[interface{}]interface{}
	f13         *map[someStruct]interfaceImpl
	f14         *map[*interfaceImpl]*someStruct
	f15         []someStruct
	f16         *[]*someStruct
	f17         *nonEmptyInterface
	f18         *someStruct
	f19         customType
	f20         *customType
	f21         []*customType
	f22         map[customType]*customType
	f23         *BigStruct
	f24         interface{}
}

var testDir = "./test_struct"
var genFiles = []string{
	"GoToKotlin_test_BigStruct.kt",
	"GoToKotlin_test_innerStruct.kt",
	"GoToKotlin_test_interfaceImpl.kt",
	"GoToKotlin_test_someStruct.kt",
}

func TestConvert(t *testing.T) {
	st := BigStruct{}

	os.MkdirAll(testDir, os.ModePerm)
	file, _ := os.Create(testDir + "/filled.txt")

	conv := GoToKotlin.CreateConverter(testDir, false)

	err := conv.GenerateStructures(st)
	if err != nil {
		t.Errorf("unexpected error: %s", err.Error())
	}
	err = conv.FillStructures(file, st)
	if err != nil {
		t.Errorf("unexpected error: %s", err.Error())
	}

	dir, err := os.ReadDir(testDir)
	if err != nil {
		t.Errorf("unexpected error: %s", err.Error())
	}

	if len(dir) != len(genFiles)+3 {
		t.Errorf("wrong number of files, want %d, got %d", len(genFiles), len(dir))
	}
	cnt := 0
	fillerCnt := 0
	baseCnt := 0
	entryCnt := 0
	for _, file := range dir {
		if file.Name() == "filled.txt" {
			fillerCnt++
			continue
		}
		if file.Name() == "baseDeserializers.kt" {
			baseCnt++
			continue
		}
		if file.Name() == "Entrypoint.kt" {
			entryCnt++
			continue
		}
		if !slices.Contains(genFiles, file.Name()[2:]) {
			t.Errorf("unexpected file, want %s, got %s", genFiles[cnt], file.Name()[2:])
		}
		cnt++
	}
	if fillerCnt != 1 || baseCnt != 1 || entryCnt != 1 {
		t.Errorf("Wrong number of files")
	}

	cmd := exec.Command("kotlinc", testDir, "-d", testDir)
	_, err = cmd.Output()

	if err != nil {
		t.Errorf("Kotlin files did not compile")
	}
}

func TestMain(m *testing.M) {
	code := m.Run()

	os.RemoveAll(testDir)

	os.Exit(code)
}
