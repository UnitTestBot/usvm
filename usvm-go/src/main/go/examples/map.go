package examples

func mapAlloc(l int) map[int]int64 {
	m := make(map[int]int64, l)
	m[3] = 111
	m[-226] = 13
	m[0] = -1
	return m
}

func mapLookup(m map[int]int, k int) int {
	v := m[k]
	if v == 123 {
		return -1
	}
	return v
}

func mapLookupComma(m map[int]int, k int) int {
	v, ok := m[k]
	if !ok {
		return 0
	}
	if v == 123 {
		return -1
	}
	return v
}

func mapLookupCommaReturn(m map[int]int, k int) (int, bool) {
	v, ok := m[k]
	if ok {
		return v, true
	}
	return -1, false
}

func mapUpdate(m map[int]int, k, v int) int {
	vOld := m[k]
	m[k] = v
	if v > vOld {
		return v
	}
	return vOld
}

func mapLoop(m map[int]int, n int) int {
	mx := 0
	for k, v := range m {
		if k > n && v > mx {
			mx = v
		}
	}

	return mx
}

func mapLoopLen(m map[int]int) int {
	if len(m) < 4 {
		return -1
	}

	minKey, minValue := 0, 0
	maxKey, maxValue := 0, 0
	for k, v := range m {
		if v > maxValue {
			maxKey = k
			maxValue = v
		}
		if v < minValue {
			minKey = k
			minValue = v
		}
	}
	if minValue == maxValue {
		return minValue
	}

	return m[maxKey] - m[minKey]
}

func mapDeleteSimple(a map[int]int, k int) int {
	l := len(a)

	if l < 5 {
		panic("too smol map")
	}

	delete(a, k)
	if len(a) == l {
		panic("not found")
	}

	return len(a)
}

type customMap map[int]int

func mapCustomAlloc() customMap {
	m := make(customMap)
	m[2] = 3
	return m
}
func mapCustomLookup(m customMap, k int) int {
	v := m[k]
	if v == 123 {
		return -1
	}
	return v
}

func mapCustomLookupComma(m customMap, k int) int {
	v, ok := m[k]
	if !ok {
		return 0
	}
	if v == 123 {
		return -1
	}
	return v
}

func mapCustomLookupCommaReturn(m customMap, k int) (int, bool) {
	v, ok := m[k]
	if ok {
		return v, true
	}
	return -1, false
}

func mapCustomUpdate(m customMap, k, v int) int {
	vOld := m[k]
	m[k] = v
	if v > vOld {
		return v
	}
	return vOld
}

func mapCustomDeleteSimple(a customMap, k int) int {
	l := len(a)

	if l < 5 {
		panic("too smol map")
	}

	delete(a, k)
	if len(a) == l {
		panic("not found")
	}

	return len(a)
}
