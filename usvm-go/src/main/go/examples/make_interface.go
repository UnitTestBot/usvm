package examples

var shiftError = error(errorString("negative shift amount"))

type errorString string

func (e errorString) Error() string {
	return "runtime error: " + string(e)
}

func shiftErrorToString() string {
	return shiftError.Error()
}

func appendErrorStrings() errorString {
	s1 := errorString("hello, ")
	s2 := errorString("world!")
	return s1 + s2
}
