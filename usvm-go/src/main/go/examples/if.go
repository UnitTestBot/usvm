package examples

func max2(a, b int) int {
	if a > b {
		return a
	}
	return b
}

func max2Anon(a, b int) int {
	mx := func(x, y int) int {
		if x > y {
			return x
		}
		return y
	}
	return mx(a, b)
}

func max2Closure(a, b int) int {
	mx := func() int {
		if a > b {
			return a
		}
		return b
	}
	return mx()
}

func max3(a, b, c int) int {
	if a > b && a > c {
		return a
	}
	if b > c {
		return b
	}
	return c
}

func max3Call(a, b, c int) int {
	return max2(max2(a, b), c)
}

func max4Call(a, b, c, d int) int {
	return max2(max2(a, b), max2(c, d))
}

func MinPublic(a, b int) int {
	if a < b {
		return a
	}
	return b
}

func inc(a int, f bool) int {
	if f {
		a++
	}
	return a
}

func beforeAndAfter(a int) (int, int) {
	return a - 1, a + 1
}

func sumBeforeAndAfter(a int) int {
	b, c := beforeAndAfter(a)
	return b + c
}

func call(f func() int) int {
	return f()
}
