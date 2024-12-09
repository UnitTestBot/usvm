package examples

func stringGetByte(s string, i int) byte {
	checkGoodString(s)
	return s[i]
}

func stringGetRune(s string, i int) rune {
	checkGoodString(s)
	return rune(s[i])
}
