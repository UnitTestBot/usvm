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
