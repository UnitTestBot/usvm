package regressions

func shiftByBitWidth() uint32 {
	value := uint32(1)
	count := uint32(32)
	return value << count
}

func bitwiseComplement() int32 {
	value := int32(1)
	return ^value
}

func bitwiseAndNot() uint32 {
	left := uint32(7)
	right := uint32(3)
	return left &^ right
}

func nativeIntWidth() int {
	value := int(2147483647)
	return value + 1
}

func sliceCapacity() int {
	values := make([]int, 1, 3)
	return cap(values)
}

func mapHintLength() int {
	values := make(map[int]int, 8)
	return len(values)
}

func mapInsertLength() int {
	values := make(map[int]int)
	values[1] = 2
	return len(values)
}

func sliceAlias() int {
	values := []int{1}
	alias := values[:]
	alias[0] = 2
	return values[0]
}

func stringEquality() bool {
	left := "same"
	right := "same"
	return left == right
}

func stringInequality() bool {
	left, right := "abcd", "abce"
	return left != right
}

func stringLengthDifference() bool {
	left, right := "a", "ab"
	return left == right
}

func stringOrdering() bool {
	left, right := "\xff", "z"
	return left > right
}

func stringPrefixOrdering() bool {
	left, right := "a", "ab"
	return left < right
}

func emptyStringEquality() bool {
	left, right := "", ""
	return left == right
}

func stringConcatenation() bool {
	left, right := "ab", "cd"
	combined := left + right
	expected := "abcd"
	return combined == expected
}

func stringBytesCopy() int {
	original := "abc"
	values := []byte(original)
	values[0] = 'x'
	return int(original[0])
}

func bytesStringCopy() int {
	values := []byte{'a', 'b'}
	original := string(values)
	values[0] = 'x'
	return int(original[0])
}

func sliceOffsetAlias() int {
	values := []int{1, 2, 3}
	alias := values[1:]
	alias[0] = 7
	return values[1]
}

func sliceFullCapacity() int {
	values := make([]int, 2, 5)
	return cap(values[1:2:3])
}

func sliceCopyOffset() int {
	values := []int{1, 2, 3, 4}
	copy(values[1:3], values[2:4])
	return values[1]*10 + values[2]
}

func sliceCopyString() int {
	values := make([]byte, 3)
	source := "abc"
	copy(values[1:], source)
	return int(values[1])*1000 + int(values[2])
}

func sliceAppendReuse() int {
	values := make([]int, 1, 3)
	alias := values[:3]
	result := append(values, 7)
	return alias[1]*10 + result[1]
}

func sliceAppendAllocate() int {
	values := []int{1, 2}
	result := append(values, 7)
	result[0] = 9
	return values[0]*10 + result[2]
}

func sliceAppendOffset() int {
	values := []int{1, 2, 3, 4}
	result := append(values[1:2], 7)
	return values[2]*10 + result[1]
}

func sliceArrayPointerAlias() int {
	values := []int{1, 2, 3}
	pointer := (*[2]int)(values[1:])
	pointer[0] = 7
	return values[1]
}

func nilSliceLength() int {
	var values []int
	return len(values) + cap(values)
}

func nilSliceAppend() int {
	var values []int
	result := append(values, 7)
	return result[0]
}

func mapOverwriteLength() int {
	values := make(map[int]int)
	values[1] = 2
	values[1] = 3
	return len(values)
}

func mapDeleteLength() int {
	values := make(map[int]int)
	values[1] = 2
	delete(values, 1)
	delete(values, 1)
	return len(values)
}

func bitwiseAndOr() uint32 {
	left, right := uint32(7), uint32(3)
	return (left & right) | uint32(8)
}

func oversizedShiftCount() uint32 {
	value, count := uint32(1), uint64(1)<<32
	return value << count
}

func signedRightShift() int32 {
	value, count := int32(-7), uint64(32)
	return value >> count
}

func unsignedRightShift() uint32 {
	value, count := uint32(0xffffffff), uint64(32)
	return value >> count
}

func nativeIntOverflow() bool {
	maximum := int(^uint(0) >> 1)
	return maximum+1 < 0
}

func unsignedWidening() uint64 {
	value := uint32(0xffffffff)
	return uint64(value)
}

func symbolicBranch(value int) int {
	if value < 0 {
		return -1
	}
	if value == 0 {
		return 0
	}
	return 1
}

func oversizedIndex() int {
	values := []int{7}
	index := int64(1) << 32
	return values[index]
}

func negativeIndex() int {
	values := []int{7}
	index := -1
	return values[index]
}

func negativeShift() uint32 {
	value := uint32(1)
	count := -1
	return value << count
}

func divideByZero() int {
	value, divisor := 7, 0
	return value / divisor
}

func remainderByZero() int {
	value, divisor := 7, 0
	return value % divisor
}

func utf8StringLength() int {
	value := "\x00я"
	return len(value)
}

func unsignedSliceLength() int {
	length := uint8(128)
	return len(make([]byte, length))
}

func narrowIndex() int {
	values := [300]int{50: 7}
	index := uint8(50)
	return values[index]
}

func negativeSliceHigh() int {
	values := []int{7}
	high := -1
	return len(values[:high])
}

func symbolicSliceAlias(value int) int {
	values := []int{1, 2}
	alias := values[1:]
	if value < 0 {
		alias[0] = 3
	} else {
		alias[0] = 7
	}
	return values[1]
}

func unsignedResultWidth() uint64 {
	value := uint64(0xffffffffffffffff)
	return value
}

func nilMapLookup() int {
	var values map[int]int
	return values[7]
}

func nilMapLookupComma() bool {
	var values map[int]int
	_, ok := values[7]
	return ok
}

func nilMapDelete() int {
	var values map[int]int
	delete(values, 7)
	return len(values)
}

func nilMapAssignment() int {
	var values map[int]int
	values[7] = 42
	return values[7]
}

type namedMap map[int]int

func nilNamedMapLookup() int {
	var values namedMap
	return values[7]
}

func nilNamedMapDelete() int {
	var values namedMap
	delete(values, 7)
	return len(values)
}

func missingMapLookupCommaValue() int {
	values := map[int]int{7: 42}
	value, _ := values[8]
	return value
}

func symbolicMapLookupComma(values map[int]int, key int) (int, bool) {
	value, ok := values[key]
	if values == nil {
		return value, ok
	}
	if ok {
		return value, true
	}
	return value, false
}

func nilMapRange() int {
	var values map[int]int
	iterations := 0
	for range values {
		iterations++
	}
	return iterations
}

func nilNamedMapRange() int {
	var values namedMap
	iterations := 0
	for range values {
		iterations++
	}
	return iterations
}

type namedNumber int64
type namedByte uint8
type namedBool bool

func namedNegation() int64 {
	value := namedNumber(7)
	return int64(-value)
}

func namedComplement() uint8 {
	value := namedByte(15)
	return uint8(^value)
}

func namedBooleanNot() bool {
	value := namedBool(true)
	return bool(!value)
}

func unsignedToFloat64() bool {
	value := uint64(0xffffffffffffffff)
	return float64(value) == 0x1p64
}

func unsignedToFloat32() bool {
	value := uint32(0xffffffff)
	return float32(value) == 0x1p32
}

func floatToInt8() int8 {
	value := -123.75
	return int8(value)
}

func floatToUint8() uint8 {
	value := 200.75
	return uint8(value)
}

func floatToInt16() int16 {
	value := -12345.99999
	return int16(value)
}

func floatToUint16() uint16 {
	value := 60000.75
	return uint16(value)
}

func symbolicNamedNegation(value namedNumber) namedNumber {
	return -value
}

func symbolicNamedComplement(value namedByte) namedByte {
	return ^value
}

func symbolicNamedIdentity(value namedNumber) namedNumber {
	return value
}

func namedInterfaceAssert() int64 {
	var value any = namedNumber(7)
	return int64(value.(namedNumber))
}

func nilScalarAssertion() bool {
	var value any
	_, ok := value.(namedNumber)
	return ok
}

func failedNamedAssertionZero() int64 {
	var input any = int64(7)
	value, _ := input.(namedNumber)
	return int64(value)
}

func symbolicNamedInterfaceRoundTrip(value namedNumber) namedNumber {
	if value == 0 {
		return 0
	}
	var boxed any = value
	return boxed.(namedNumber)
}

func nilPointerConversion() bool {
	var value *int64
	return (*namedNumber)(value) == nil
}

func pointerConversionAlias() int64 {
	value := int64(3)
	alias := (*namedNumber)(&value)
	*alias = 8
	value += 2
	return int64(*alias) + value
}

func pointerConversionRoundTrip() bool {
	value := int64(3)
	pointer := &value
	return (*int64)((*namedNumber)(pointer)) == pointer
}

func namedPointerConversionAlias() int64 {
	value := namedNumber(3)
	alias := (*int64)(&value)
	*alias = 8
	value += 2
	return *alias + int64(value)
}

type valueRecord struct{ number int }

type nestedValueRecord struct{ record valueRecord }

func structValueCopy() int {
	original := valueRecord{number: 3}
	copied := original
	copied.number = 8
	return original.number + copied.number
}

func nestedStructValueCopy() int {
	original := nestedValueRecord{record: valueRecord{number: 3}}
	copied := original
	copied.record.number = 8
	return original.record.number + copied.record.number
}

func arrayValueCopy() int {
	original := [2]int{3, 4}
	copied := original
	copied[0] = 8
	return original[0] + copied[0]
}

func structArgumentCopy() int {
	original := valueRecord{number: 3}
	result := func(value valueRecord) int {
		value.number = 8
		return value.number
	}(original)
	return original.number + result
}

func arrayArgumentCopy() int {
	original := [2]int{3, 4}
	result := func(value [2]int) int {
		value[0] = 8
		return value[0]
	}(original)
	return original[0] + result
}

func interfaceStructCopy() int {
	original := valueRecord{number: 3}
	var boxed any = original
	original.number = 8
	return boxed.(valueRecord).number + original.number
}

func nilStructAssertionZero() int {
	var boxed any
	value, _ := boxed.(valueRecord)
	return value.number
}

func typedNilPointerAssertion() bool {
	var value *valueRecord
	var boxed any = value
	result, ok := boxed.(*valueRecord)
	return ok && result == nil && boxed != nil
}

func nilStructAssertionOk() bool {
	var boxed any
	_, ok := boxed.(valueRecord)
	return ok
}

func pointerToInterfaceDoesNotImplement() bool {
	var value *error
	var boxed any = value
	_, ok := boxed.(error)
	return ok
}

func oversizedResolvedSlice(_ int) []int {
	return make([]int, 10001)
}
