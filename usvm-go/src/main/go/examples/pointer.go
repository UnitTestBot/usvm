package examples

func pointer(i int) int {
	j := &i
	k := &j
	l := &k
	**k += 8
	**l = *k
	return ***l + 1
}

func sample(i int) int {
	j := &i
	*j += 5
	k := &j
	return **k + 1
}

func sampleAnother(i int) int {
	a := 5
	b := &a
	*b = 3
	return a
}
