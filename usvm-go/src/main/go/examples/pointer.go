package examples

func pointerSimple(i int) int {
	j := &i
	k := &j
	l := &k
	**k += 8
	**l = *k
	return ***l + 1
}

func pointerOther(i int) int {
	j := &i
	*j += 5
	k := &j
	return **k + 1
}

func pointerAnother(i int) int {
	a := 5
	b := &a
	*b = 3
	return a
}

func pointerChangeType(i *int) *NamedInt {
	return (*NamedInt)(i)
}
