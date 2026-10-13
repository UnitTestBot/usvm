package regressions

type mapFloatKey float64
type mapStringKey string
type mapRecordKey struct {
	name  string
	index int
}

func negativeMapZero(value float64) float64 { return -value }
func mapNaN(value float64) float64          { return value / value }

func floatMapZeroLookup() int {
	values := map[float64]int{0: 7}
	return values[negativeMapZero(0)]
}

func floatMapZeroOverwrite() int {
	values := map[float64]int{0: 7}
	values[negativeMapZero(0)] = 9
	return len(values)*10 + values[0]
}

func floatMapZeroDelete() int {
	values := map[float64]int{0: 7}
	delete(values, negativeMapZero(0))
	return len(values)
}

func namedFloatMapZeroLookup() int {
	values := map[mapFloatKey]int{0: 7}
	return values[mapFloatKey(negativeMapZero(0))]
}

func floatMapNaNLookup() int {
	key := mapNaN(0)
	values := map[float64]int{}
	values[key] = 7
	values[key] = 9
	value, found := values[key]
	// NaN is never found; encode the observation without an unreachable branch.
	foundPenalty := map[bool]int{false: 0, true: -1}[found]
	delete(values, key)
	return len(values)*10 + value + foundPenalty
}

func floatMapNaNRange() int {
	key := mapNaN(0)
	values := map[float64]int{}
	values[key] = 7
	values[key] = 9
	result := 0
	for key, value := range values {
		// Every key is NaN, so reflexivity is observed without an impossible alternative.
		reflexivePenalty := map[bool]int{false: 0, true: -1}[key == key]
		result += value + reflexivePenalty
	}
	return result
}

func stringMapValueLookup() int {
	values := map[string]int{"same": 7}
	return values["same"]
}

func namedStringMapValueLookup() int {
	values := map[mapStringKey]int{"same": 9}
	return values[mapStringKey("same")]
}

func arrayMapKeySnapshot() int {
	key := [2]int{1, 2}
	values := map[[2]int]int{key: 7}
	key[0] = 9
	return values[[2]int{1, 2}]*10 + values[key]
}

func structMapValueLookup() int {
	values := map[mapRecordKey]int{{name: "same", index: 2}: 11}
	return values[mapRecordKey{name: "same", index: 2}]
}

func arrayMapRangeKeyCopy() int {
	values := map[[2]int]int{{1, 2}: 7}
	for key := range values {
		key[0] = 9
	}
	return values[[2]int{1, 2}]
}

func interfaceMapValueLookup() int {
	values := map[any]int{[2]int{1, 2}: 7}
	return values[[2]int{1, 2}]
}

type mapArrayKey [2]int

func float32MapZeroAndNaN() int {
	zero := float32(negativeMapZero(0))
	nan := float32(mapNaN(0))
	values := map[float32]int{0: 7}
	values[zero] = 9
	// Signed zeros overwrite one entry; keep both observations in the native checksum.
	zeroChecksum := (len(values)-1)*1000 + values[0] - 9
	values[nan] = 7
	values[nan] = 9
	delete(values, zero)
	result := len(values)*100 + zeroChecksum
	for key, value := range values {
		// Every key is NaN, so reflexivity is observed without an impossible alternative.
		reflexivePenalty := map[bool]int{false: 0, true: -1}[key == key]
		result += value + reflexivePenalty
	}
	return result
}

func interfaceMapUncomparableLookup() int {
	values := map[any]int{}
	key := []int{1}
	return values[key]
}

func interfaceMapUncomparableUpdate() int {
	values := map[any]int{}
	key := []int{1}
	values[key] = 7
	return len(values)
}

func floatMapNaNResult() map[float64]int {
	key := mapNaN(0)
	values := map[float64]int{}
	values[key] = 7
	values[key] = 9
	return values
}

func pointerMapKeyResult() map[*int]int {
	first, second := new(int), new(int)
	values := map[*int]int{first: 7, second: 9}
	return values
}

func namedStringMapResult() map[mapStringKey]int {
	return map[mapStringKey]int{"same": 9}
}

func namedArrayMapResult() map[mapArrayKey]int {
	return map[mapArrayKey]int{{1, 2}: 7}
}

func inputFloatMapLookup(values map[float64]int) int {
	value, found := values[negativeMapZero(0)]
	if found {
		return value
	}
	return -1
}

func inputStringMapLookup(values map[string]int) int {
	value, found := values["same"]
	if found {
		return value
	}
	return -1
}

func inputFloatMapExtraEntry(values map[float64]int) int {
	if len(values) != 2 {
		return -1
	}
	value, found := values[0]
	if !found {
		return -2
	}
	return value
}

func inputStringMapExtraEntry(values map[string]int) int {
	if len(values) != 2 {
		return -1
	}
	value, found := values["same"]
	if !found {
		return -2
	}
	return value
}

type mapPointerNumber int

func pointerMapConvertedAlias() int {
	pointer := new(int)
	values := map[*int]int{pointer: 7}
	converted := (*mapPointerNumber)(pointer)
	restored := (*int)(converted)
	return len(values)*10 + values[restored]
}

func floatMapZeroRangeAfterOverwrite() int {
	values := map[float64]int{0: 7}
	values[negativeMapZero(0)] = 9
	result := 0
	for key := range values {
		result += map[bool]int{false: 1, true: -1}[1/key < 0]
	}
	return result
}

func inputFloatMapZeroRangeSign(values map[float64]int) int {
	if len(values) != 1 {
		return 0
	}
	_, found := values[0]
	if !found {
		return 0
	}
	// A one-entry map containing zero can only range over one of its two signs.
	result := 0
	for key := range values {
		if 1/key < 0 {
			result = -1
		} else {
			result = 1
		}
	}
	return result
}

type mapMixedKey struct {
	number  float64
	payload any
}

func mapStructNaNUncomparablePanics() int {
	values := map[mapMixedKey]int{}
	key := mapMixedKey{number: mapNaN(0), payload: []int{1}}
	values[key] = 7
	return len(values)
}

func inputStringMapSymbolicKeyAndExtra(values map[string]int, key string) int {
	if len(values) != 2 {
		return -1
	}
	value, found := values[key]
	if !found {
		return -2
	}
	return value
}

func inputFloatMapCanonicalGhosts(values map[float64]int, key float64) int {
	if key != 0 {
		return -1
	}
	if len(values) != 2 {
		return -2
	}
	first, firstFound := values[key]
	second, secondFound := values[negativeMapZero(key)]
	// +/-zero must select the same entry; retain consistency in every replayed result.
	inconsistencyCount := map[bool]int{false: 0, true: 1}[firstFound != secondFound] +
		map[bool]int{false: 0, true: 1}[first != second]
	penalty := map[bool]int{false: 0, true: -3}[inconsistencyCount != 0]
	if !firstFound {
		return -4 + penalty
	}
	return first + penalty
}

func markMapKeyValue(sideEffect *int) int {
	*sideEffect = 7
	return 1
}

func recoverMapKeyAssignment(sideEffect *int, result *int) {
	if recover() != nil {
		*result = *sideEffect
	}
}

func mapKeyHashAfterValueEvaluation() (result int) {
	sideEffect := 0
	defer recoverMapKeyAssignment(&sideEffect, &result)
	values := map[any]int{}
	values[[]int{1}] = markMapKeyValue(&sideEffect)
	return 0
}
