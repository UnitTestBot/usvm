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
	if found {
		return -1
	}
	delete(values, key)
	return len(values)*10 + value
}

func floatMapNaNRange() int {
	key := mapNaN(0)
	values := map[float64]int{}
	values[key] = 7
	values[key] = 9
	result := 0
	for key, value := range values {
		if key == key {
			return -1
		}
		result += value
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
	if len(values) != 1 || values[0] != 9 {
		return -1
	}
	values[nan] = 7
	values[nan] = 9
	delete(values, zero)
	result := len(values) * 100
	for key, value := range values {
		if key == key {
			return -1
		}
		result += value
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
	for key := range values {
		if 1/key < 0 {
			return -1
		}
		return 1
	}
	return 0
}

func inputFloatMapZeroRangeSign(values map[float64]int) int {
	if len(values) != 1 {
		return 0
	}
	_, found := values[0]
	if !found {
		return 0
	}
	for key := range values {
		if key == 0 && 1/key < 0 {
			return -1
		}
		if key == 0 {
			return 1
		}
	}
	return 0
}
