package regressions

type mapIntegerKey int
type mapUnsignedKey uint64
type mapBooleanKey bool

func namedScalarMapLookup() int {
	values := map[mapIntegerKey]int{}
	values[mapIntegerKey(1)] = 7
	return values[mapIntegerKey(1)]
}

func namedScalarMapOverwrite() int {
	values := map[mapIntegerKey]int{}
	values[mapIntegerKey(1)] = 7
	values[mapIntegerKey(1)] = 9
	return len(values)*100 + values[mapIntegerKey(1)]
}

func namedScalarMapDelete() int {
	values := map[mapIntegerKey]int{mapIntegerKey(1): 7}
	delete(values, mapIntegerKey(1))
	return len(values) + values[mapIntegerKey(1)]
}

func namedScalarMapRange() int {
	values := map[mapIntegerKey]int{mapIntegerKey(1): 7, mapIntegerKey(2): 9}
	sum := 0
	for key, value := range values {
		sum += int(key) + value
	}
	return sum
}

func namedBooleanMapKeys() int {
	values := map[mapBooleanKey]int{mapBooleanKey(true): 7, mapBooleanKey(false): 9}
	values[mapBooleanKey(true)] = 11
	delete(values, mapBooleanKey(false))
	return len(values)*100 + values[mapBooleanKey(true)]
}

func namedScalarMapResult() map[mapIntegerKey]int {
	return map[mapIntegerKey]int{mapIntegerKey(1): 7}
}

func namedUnsignedMapResult() map[mapUnsignedKey]int {
	return map[mapUnsignedKey]int{mapUnsignedKey(18446744073709551615): 7}
}

type variadicCounter interface{ Count(...int) int }
type sliceCounter interface{ Count([]int) int }
type sliceSignatureReceiver struct{}
type variadicSignatureReceiver struct{}

func (sliceSignatureReceiver) Count(values []int) int     { return len(values) }
func (variadicSignatureReceiver) Count(values ...int) int { return len(values) }

func sliceMethodDoesNotImplementVariadic() bool {
	var value any = sliceSignatureReceiver{}
	_, ok := value.(variadicCounter)
	return ok
}

func variadicMethodImplementsVariadic() bool {
	var value any = variadicSignatureReceiver{}
	_, ok := value.(variadicCounter)
	return ok
}

func variadicMethodDoesNotImplementSlice() bool {
	var value any = variadicSignatureReceiver{}
	_, ok := value.(sliceCounter)
	return ok
}
