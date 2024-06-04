package main

func Max2(a, b int) int {
	if a > b {
		return a
	}
	return b
}

func Max2Improved(a, b int) int {
	c := a
	if b > a {
		c = b
	}
	return c
}

func main() {
	Max2(1, 2)
	Max2Improved(1, 2)
}
