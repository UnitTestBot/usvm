package examples

func max2(a, b int) int {
	if a > b {
		return a
	}
	return b
}

func max2Closure(a, b int) int {
	mx := func(x, y int) int {
		if x > y {
			return x
		}
		return y
	}
	return mx(a, b)
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

func inc(a int, f bool) int {
	if f {
		a++
	}
	return a
}
