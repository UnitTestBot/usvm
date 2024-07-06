package main

func Max2(a, b int) int {
	if a > b {
		return a
	}
	return b
}

func Max2Improved(a, b int) int {
	c := -1
	if b > a && b > 5 && a > 3 {
		c = b
	}
	if b < a {
		c = a
	}
	return c
}

func panicking() {
	panic(nil)
}

func panicRecover(a int) (b int) {
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

func panicRecoverSimple(a int) (b int) {
	defer func() {
		b = 5
	}()

	panic(nil)
	return b
}

func loopSimple() int {
	s := 0
	for i := 0; i < 10; i++ {
		s += i
	}
	return s
}

func main() {}
