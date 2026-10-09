package regressions

func reviewDeferSet(pointer *int, value int) { *pointer = value }

func deferredArguments() (result int) {
	defer reviewDeferSet(&result, 1)
	reviewDeferSet(&result, 2)
	return
}

func repeatedDeferArguments() (result int) {
	defer reviewDeferSet(&result, 1)
	defer reviewDeferSet(&result, 2)
	return
}

func deferredStructArgument() (result int) {
	value := valueRecord{number: 1}
	defer func(captured valueRecord) { result = captured.number }(value)
	value.number = 2
	return
}

func recursiveDeferFrames(depth int) (result int) {
	if depth < 0 || depth > 2 {
		return -1
	}
	defer func() { result++ }()
	if depth > 0 {
		result = recursiveDeferFrames(depth - 1)
	}
	return
}

func recursiveDeferredArguments() int {
	return recursiveDeferFrames(2) + recursiveDeferFrames(-1) + recursiveDeferFrames(3)
}

type reviewError string

func (value reviewError) Error() string { return string(value) }

func interfaceAssertion() bool {
	var value any = reviewError("ok")
	_, ok := value.(error)
	return ok
}

func interfaceAssertionNoComma() bool {
	var value any = reviewError("ok")
	return value.(error) != nil
}

type reviewReader interface{ Read() int }
type reviewCounter struct{ number int }

func (counter *reviewCounter) Read() int { return counter.number }

func pointerInterfaceCall() int {
	var reader reviewReader = &reviewCounter{number: 1}
	return reader.Read()
}

func valueHasPointerMethods() bool {
	var value any = reviewCounter{number: 1}
	_, ok := value.(reviewReader)
	return ok
}

func symbolicInterfaceReceiver(reader reviewReader) int { return reader.Read() }

func mapStructCopy() int {
	original := valueRecord{number: 1}
	values := map[int]valueRecord{0: original}
	original.number = 2
	return values[0].number
}

func mapArrayCopy() int {
	original := [1]int{1}
	values := map[int][1]int{0: original}
	original[0] = 2
	return values[0][0]
}

func mapLookupStructCopy() int {
	values := map[int]valueRecord{0: {number: 1}}
	copied := values[0]
	copied.number = 2
	return values[0].number
}

func missingStructLookup() int {
	values := map[int]valueRecord{}
	return values[0].number
}

func missingNamedLookup() int {
	values := map[int]namedNumber{}
	return int(values[0])
}

func missingArrayLookup() int {
	values := map[int][1]int{}
	return values[0][0]
}

func missingStructLookupComma() int {
	values := map[int]valueRecord{}
	value, _ := values[0]
	return value.number
}

func sliceStructCopy() int {
	source := []valueRecord{{number: 1}}
	destination := make([]valueRecord, 1)
	copy(destination, source)
	destination[0].number = 2
	return source[0].number
}

func appendStructCopy() int {
	source := []valueRecord{{number: 1}}
	destination := append([]valueRecord{}, source...)
	destination[0].number = 2
	return source[0].number
}

func sliceArrayCopy() int {
	source := [][1]int{{1}}
	destination := make([][1]int, 1)
	copy(destination, source)
	destination[0][0] = 2
	return source[0][0]
}

func appendArrayCopy() int {
	source := [][1]int{{1}}
	destination := append([][1]int{}, source...)
	destination[0][0] = 2
	return source[0][0]
}

func overlapCompositeCopy() int {
	values := []valueRecord{{number: 1}, {number: 2}, {number: 3}}
	copy(values[1:], values)
	values[1].number = 9
	return values[0].number + values[1].number + values[2].number
}

func appendCompositeReuse() int {
	original := make([]valueRecord, 1, 2)
	original[0] = valueRecord{number: 1}
	source := []valueRecord{{number: 2}}
	destination := append(original, source...)
	destination[1].number = 9
	destination[0].number = 3
	return source[0].number*10 + original[0].number
}

func appendCompositeAllocate() int {
	source := []valueRecord{{number: 1}}
	destination := append(source, valueRecord{number: 2})
	destination[0].number = 9
	return source[0].number
}

func narrowSignedIndex() int {
	values := make([]int, 256)
	index := int8(-1)
	return values[index]
}

func negativeInt16Index() int {
	values := make([]int, 65536)
	index := int16(-1)
	return values[index]
}

func symbolicNarrowIndex(index int8) int {
	values := make([]int, 256)
	return values[index]
}

func symbolicCompositeCopy(length int) int {
	if length < 0 || length > 3 {
		return -1
	}
	source := []valueRecord{{number: 1}, {number: 2}, {number: 3}}
	destination := make([]valueRecord, length)
	count := copy(destination, source)
	if count > 0 {
		destination[0].number = 9
	}
	return count*10 + source[0].number
}

func symbolicCompositeAppend(length int) int {
	if length < 0 || length > 3 {
		return -1
	}
	source := []valueRecord{{number: 1}, {number: 2}, {number: 3}}
	destination := append([]valueRecord{}, source[:length]...)
	if length > 0 {
		destination[0].number = 9
	}
	return len(destination)*10 + source[0].number
}

func namedMapAssertionZero() bool {
	type namedMap map[int]int
	var boxed any
	value, _ := boxed.(namedMap)
	return value == nil
}

func namedSliceAssertionZero() bool {
	type namedSlice []int
	var boxed any
	value, _ := boxed.(namedSlice)
	return value == nil
}

func namedPointerAssertionZero() bool {
	type namedPointer *int
	var boxed any
	value, _ := boxed.(namedPointer)
	return value == nil
}

func compositePointerFieldsRemainShared() int {
	type record struct {
		number  int
		pointer *int
	}

	pointed := 1
	source := []record{{number: 1, pointer: &pointed}}
	destination := make([]record, 1)
	copy(destination, source)
	appended := append([]record{}, source...)
	values := map[int]record{0: source[0]}

	destination[0].number = 9
	*destination[0].pointer = 3

	return source[0].number*1000 + *source[0].pointer*100 + appended[0].number*10 + *values[0].pointer
}
