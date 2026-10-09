package examples

func stringGetByte(s string, i int) byte {
	checkGoodString(s)
	return s[i]
}

func stringGetRune(s string, i int) rune {
	checkGoodString(s)
	return rune(s[i])
}

func stringAppend() string {
	s1 := "hello, "
	s2 := "world"
	return s1 + s2
}

func stringToByteArray() []byte {
	return []byte("hello")
}

func stringFromByteArray() string {
	return string([]byte{104, 101, 108, 108, 111})
}
