package examples

func simple(a int) (b int) {
	defer func() {
		b++
		defer func() {
			b++
			recover()
		}()
		defer func() {
			defer func() {
				defer func() {
					b++
					if b > 3 {
						panic("fail")
					}
				}()
				b++
			}()
			b++
		}()
	}()

	return a
}

func verySimple(a int) (b int) {
	if a == 3 {
		return 3
	}
	defer func() {
		b = 5
	}()
	return a
}

func panicking() {
	panic("oh no")
}

func panicRecoverComplex(a int) (b int) {
	defer func() {
		if r := recover(); r != nil {
			b = 3
		}
	}()
	defer func() {
		b = -227
	}()
	defer func() {
		b = 2
		if r := recover(); r != nil {
			b += 17
		}
	}()
	defer func() {
		b = 5
	}()

	panicking()
	b = a + 1000
	return b
}

func panicRecoverSimple() int {
	defer func() {
		recover()
	}()

	panic("fail")
}

func panicRecoverResultSimple() (a int) {
	defer func() {
		a += 3
		recover()
		a += 5
	}()

	panic("fail")
}
