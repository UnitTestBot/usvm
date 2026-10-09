package examples

import (
	"encoding/json"
	"os"
	"testing"
)

type replayRequest struct {
	Method string              `json:"method"`
	Inputs [][]json.RawMessage `json:"inputs"`
}

type replayResult struct {
	Value          any   `json:"value"`
	IsPanic        bool  `json:"isPanic"`
	ArgumentsAfter []any `json:"argumentsAfter"`
}

func TestReplayExamples(t *testing.T) {
	filename := os.Getenv("USVM_GO_REPLAY_FILE")
	if filename == "" {
		t.Skip("No symbolic inputs supplied")
	}
	data, err := os.ReadFile(filename)
	if err != nil {
		t.Fatal(err)
	}
	var request replayRequest
	if err := json.Unmarshal(data, &request); err != nil {
		t.Fatal(err)
	}

	results := make([]replayResult, len(request.Inputs))
	for index, arguments := range request.Inputs {
		results[index] = replayExample(t, request.Method, arguments)
	}

	data, err = json.Marshal(results)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filename, data, 0o600); err != nil {
		t.Fatal(err)
	}
}

func replayExample(t *testing.T, method string, arguments []json.RawMessage) (result replayResult) {
	t.Helper()
	defer func() {
		if recover() != nil {
			result.IsPanic = true
		}
	}()
	decode := func(index int, target any) {
		if err := json.Unmarshal(arguments[index], target); err != nil {
			t.Fatal(err)
		}
	}

	switch method {
	case "sliceOverwrite", "sliceCopySimple":
		var values []int
		decode(0, &values)
		// The frontend currently models input capacity as equal to length.
		values = values[:len(values):len(values)]
		result.ArgumentsAfter = []any{values}
		if method == "sliceOverwrite" {
			result.Value = sliceOverwrite(values)
		} else {
			result.Value = sliceCopySimple(values)
		}
	case "arrayIndex":
		var values [3]int
		var index int
		decode(0, &values)
		decode(1, &index)
		result.ArgumentsAfter = []any{values, index}
		result.Value = arrayIndex(values, index)
	case "pointerChangeType":
		var value *int
		decode(0, &value)
		result.ArgumentsAfter = []any{value}
		result.Value = pointerChangeType(value)
	case "(*usvm/examples.Object).Set":
		var fields *struct {
			Value int `json:"field0"`
		}
		var value int
		decode(0, &fields)
		decode(1, &value)
		var object *Object
		if fields != nil {
			object = &Object{value: fields.Value}
		}
		result.ArgumentsAfter = []any{nil, value}
		defer func() {
			if object != nil {
				result.ArgumentsAfter[0] = map[string]int{"field0": object.value}
			}
		}()
		object.Set(value)
		result.Value = ""
	case "mapLoopLen":
		var values map[int]int
		decode(0, &values)
		result.ArgumentsAfter = []any{values}
		result.Value = mapLoopLen(values)
	default:
		t.Fatalf("Unknown replay method %s", method)
	}
	return result
}
