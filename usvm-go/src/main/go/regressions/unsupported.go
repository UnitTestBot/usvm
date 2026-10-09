package regressions

func unsupportedGoroutine(value int) {
	go symbolicBranch(value)
}

func unsupportedCaller(value int) {
	unsupportedGoroutine(value)
}

func symbolicStringEquality(left, right string) bool {
	return left == right
}
