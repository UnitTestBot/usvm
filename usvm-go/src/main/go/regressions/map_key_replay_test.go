package regressions

import (
	"encoding/json"
	"math"
	"os"
	"strconv"
	"testing"
)

type mapKeyReplayEntry struct {
	FloatKeyBits string `json:"floatKeyBits"`
	StringKey    string `json:"stringKey"`
	Value        int64  `json:"value"`
}

type mapKeyReplayInput struct {
	Nil     bool                `json:"nil"`
	Entries []mapKeyReplayEntry `json:"entries"`
}

type mapKeyReplayRequest struct {
	Method string              `json:"method"`
	Inputs []mapKeyReplayInput `json:"inputs"`
}

func TestReplayMapKeys(t *testing.T) {
	filename := os.Getenv("USVM_GO_REPLAY_FILE")
	if filename == "" {
		t.Skip("No map witnesses supplied")
	}
	data, err := os.ReadFile(filename)
	if err != nil {
		t.Fatal(err)
	}
	var request mapKeyReplayRequest
	if err := json.Unmarshal(data, &request); err != nil {
		t.Fatal(err)
	}
	results := make([]int, len(request.Inputs))
	for index, input := range request.Inputs {
		var floats map[float64]int
		var strings map[string]int
		if !input.Nil {
			floats = make(map[float64]int)
			strings = make(map[string]int)
			for _, entry := range input.Entries {
				strings[entry.StringKey] = int(entry.Value)
				if entry.FloatKeyBits != "" {
					bits, err := strconv.ParseUint(entry.FloatKeyBits, 16, 64)
					if err != nil {
						t.Fatal(err)
					}
					floats[math.Float64frombits(bits)] = int(entry.Value)
				}
			}
		}
		switch request.Method {
		case "inputFloatMapLookup":
			results[index] = inputFloatMapLookup(floats)
		case "inputStringMapLookup":
			results[index] = inputStringMapLookup(strings)
		case "inputFloatMapExtraEntry":
			results[index] = inputFloatMapExtraEntry(floats)
		case "inputStringMapExtraEntry":
			results[index] = inputStringMapExtraEntry(strings)
		case "floatMapNaNResult":
			values := floatMapNaNResult()
			result := len(values) * 100
			for key, value := range values {
				if !math.IsNaN(key) {
					t.Fatal("Expected NaN key")
				}
				result += value
			}
			results[index] = result
		case "pointerMapKeyResult":
			values := pointerMapKeyResult()
			result := len(values) * 100
			for key, value := range values {
				if key == nil || *key != 0 {
					t.Fatal("Expected distinct pointers to zero")
				}
				result += value
			}
			results[index] = result
		case "namedStringMapResult":
			values := namedStringMapResult()
			results[index] = len(values)*100 + values[mapStringKey("same")]
		case "namedArrayMapResult":
			values := namedArrayMapResult()
			result := len(values) * 100
			for key, value := range values {
				result += key[0]*10 + key[1] + value
			}
			results[index] = result
		default:
			t.Fatal("Unknown map witness method")
		}
	}
	data, err = json.Marshal(results)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filename, data, 0o600); err != nil {
		t.Fatal(err)
	}
}
