package regressions

import (
	"encoding/json"
	"fmt"
	"os"
	"testing"
)

func TestNativeOracle(t *testing.T) {
	cases := map[string]func() any{
		"nilMapLookupComma":          func() any { return nilMapLookupComma() },
		"nilMapDelete":               func() any { return nilMapDelete() },
		"nilMapAssignment":           func() any { return nilMapAssignment() },
		"nilNamedMapLookup":          func() any { return nilNamedMapLookup() },
		"nilNamedMapDelete":          func() any { return nilNamedMapDelete() },
		"missingMapLookupCommaValue": func() any { return missingMapLookupCommaValue() },
		"nilMapRange":                func() any { return nilMapRange() },
		"nilNamedMapRange":           func() any { return nilNamedMapRange() },
		"nilMapLookup":               func() any { return nilMapLookup() },
		"unsignedResultWidth":        func() any { return unsignedResultWidth() },
		"shiftByBitWidth":            func() any { return shiftByBitWidth() },
		"bitwiseComplement":          func() any { return bitwiseComplement() },
		"bitwiseAndNot":              func() any { return bitwiseAndNot() },
		"nativeIntWidth":             func() any { return nativeIntWidth() },
		"sliceCapacity":              func() any { return sliceCapacity() },
		"mapHintLength":              func() any { return mapHintLength() },
		"mapInsertLength":            func() any { return mapInsertLength() },
		"sliceAlias":                 func() any { return sliceAlias() },
		"stringEquality":             func() any { return stringEquality() },
		"stringInequality":           func() any { return stringInequality() },
		"stringLengthDifference":     func() any { return stringLengthDifference() },
		"stringOrdering":             func() any { return stringOrdering() },
		"stringPrefixOrdering":       func() any { return stringPrefixOrdering() },
		"emptyStringEquality":        func() any { return emptyStringEquality() },
		"stringConcatenation":        func() any { return stringConcatenation() },
		"stringBytesCopy":            func() any { return stringBytesCopy() },
		"bytesStringCopy":            func() any { return bytesStringCopy() },
		"sliceOffsetAlias":           func() any { return sliceOffsetAlias() },
		"sliceFullCapacity":          func() any { return sliceFullCapacity() },
		"sliceCopyOffset":            func() any { return sliceCopyOffset() },
		"sliceCopyString":            func() any { return sliceCopyString() },
		"sliceAppendReuse":           func() any { return sliceAppendReuse() },
		"sliceAppendAllocate":        func() any { return sliceAppendAllocate() },
		"sliceAppendOffset":          func() any { return sliceAppendOffset() },
		"sliceArrayPointerAlias":     func() any { return sliceArrayPointerAlias() },
		"nilSliceLength":             func() any { return nilSliceLength() },
		"nilSliceAppend":             func() any { return nilSliceAppend() },
		"mapOverwriteLength":         func() any { return mapOverwriteLength() },
		"mapDeleteLength":            func() any { return mapDeleteLength() },
		"bitwiseAndOr":               func() any { return bitwiseAndOr() },
		"oversizedShiftCount":        func() any { return oversizedShiftCount() },
		"signedRightShift":           func() any { return signedRightShift() },
		"unsignedRightShift":         func() any { return unsignedRightShift() },
		"nativeIntOverflow":          func() any { return nativeIntOverflow() },
		"unsignedWidening":           func() any { return unsignedWidening() },
		"oversizedIndex":             func() any { return oversizedIndex() },
		"negativeIndex":              func() any { return negativeIndex() },
		"negativeShift":              func() any { return negativeShift() },
		"divideByZero":               func() any { return divideByZero() },
		"remainderByZero":            func() any { return remainderByZero() },
		"utf8StringLength":           func() any { return utf8StringLength() },
		"unsignedSliceLength":        func() any { return unsignedSliceLength() },
		"narrowIndex":                func() any { return narrowIndex() },
		"negativeSliceHigh":          func() any { return negativeSliceHigh() },
	}
	results := make(map[string]string, len(cases))

	for name, run := range cases {
		results[name] = captureNativeResult(run)
	}

	output := os.Getenv("USVM_GO_ORACLE_FILE")
	if output == "" {
		t.Logf("Native Go results: %v", results)
		return
	}

	data, err := json.Marshal(results)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(output, data, 0o600); err != nil {
		t.Fatal(err)
	}
}

func TestReplaySymbolicInputs(t *testing.T) {
	filename := os.Getenv("USVM_GO_REPLAY_FILE")
	if filename == "" {
		t.Skip("No symbolic inputs supplied")
	}
	data, err := os.ReadFile(filename)
	if err != nil {
		t.Fatal(err)
	}
	var inputs []int
	if err := json.Unmarshal(data, &inputs); err != nil {
		t.Fatal(err)
	}
	outputs := make([]int, len(inputs))
	for index, input := range inputs {
		switch os.Getenv("USVM_GO_REPLAY_METHOD") {
		case "symbolicBranch":
			outputs[index] = symbolicBranch(input)
		case "symbolicSliceAlias":
			outputs[index] = symbolicSliceAlias(input)
		default:
			t.Fatal("Unknown replay method")
		}
	}
	data, err = json.Marshal(outputs)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filename, data, 0o600); err != nil {
		t.Fatal(err)
	}
}

func captureNativeResult(run func() any) (result string) {
	defer func() {
		if recover() != nil {
			result = "panic"
		}
	}()
	return fmt.Sprint(run())
}
