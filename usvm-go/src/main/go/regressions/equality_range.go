package regressions

func mapDeletedDuringRange() int {
	values := map[int]int{1: 1, 2: 1}
	count := 0
	for range values {
		delete(values, 1)
		delete(values, 2)
		count++
	}
	return count
}

func mapRangeCurrentValues() int {
	values := map[int]int{1: 1, 2: 1}
	sum := 0
	for _, value := range values {
		sum += value
		values[1] = 9
		values[2] = 9
	}
	return sum
}

func mapDeletedReferenceKeysDuringRange() int {
	first, second := 1, 2
	values := map[*int]int{&first: 1, &second: 1}
	count := 0
	for range values {
		delete(values, &first)
		delete(values, &second)
		count++
	}
	return count
}

func utf8RangeRunesAndIndexes() int {
	result := 0
	for index, value := range "Aé€😀" {
		result += int(value) + index*1000000
	}
	return result
}

func utf8RangeInvalidBytes() int {
	result := 0
	// Invalid start, overlong encodings, surrogate, out-of-range rune, and truncated tail.
	for index, value := range "\xff\xc0\xaf\xe0\x80\x80\xed\xa0\x80\xf4\x90\x80\x80\xf0\x9f\x98" {
		result += int(value) + index*1000000
	}
	return result
}

func utf8RangeBoundaryRunes() int {
	result := 0
	for index, value := range "\x00\x7f\xc2\x80\xdf\xbf\xe0\xa0\x80\xed\x9f\xbf\xee\x80\x80\xef\xbf\xbf\xf0\x90\x80\x80\xf4\x8f\xbf\xbf" {
		result += int(value) + index*1000000
	}
	return result
}

func utf8RangeMalformedContinuation() int {
	result := 0
	for index, value := range "\xe2A\x82\xf0\x9fB\x80" {
		result += int(value) + index*1000000
	}
	return result
}

func utf8RangeNamedString() int {
	type text string
	result := 0
	for index, value := range text("é😀") {
		result += int(value) + index*1000000
	}
	return result
}

func copiedArrayEquality() bool {
	original := [2]int{7, 9}
	copied := original
	return original == copied
}

func copiedArrayInequality() bool {
	original := [2]int{7, 9}
	copied := original
	copied[1] = 8
	return original != copied
}

func copiedStructEquality() bool {
	type record struct {
		numbers [2]int
		text    string
	}
	original := record{numbers: [2]int{7, 9}, text: "same"}
	copied := original
	return original == copied
}

func structPointerFieldsCompareIdentity() bool {
	type record struct{ pointer *int }
	first, second := 7, 7
	left := record{pointer: &first}
	right := record{pointer: &second}
	return left == right
}

func copiedStructSharesPointerIdentity() bool {
	type record struct{ pointer *int }
	value := 7
	original := record{pointer: &value}
	copied := original
	return original == copied
}

func interfaceArrayValueEquality() bool {
	array := [1]int{7}
	var left any = array
	var right any = array
	return left == right
}

func interfaceNamedTypeInequality() bool {
	type first int
	type second int
	var left any = first(7)
	var right any = second(7)
	return left != right
}

func interfaceUncomparablePanics() bool {
	value := []int{7}
	var left any = value
	var right any = value
	return left == right
}

func interfaceDifferentUncomparableTypes() bool {
	var left any = []int{7}
	var right any = map[int]int{7: 7}
	return left == right
}

func structEqualityStopsBeforeUncomparableField() bool {
	type record struct {
		number int
		value  any
	}
	left := record{number: 1, value: []int{7}}
	right := record{number: 2, value: []int{7}}
	return left == right
}

func utf8RangeReplacementRune() int {
	result := 0
	for index, value := range "\xef\xbf\xbd\xffA" {
		result += int(value) + index*1000000
	}
	return result
}
