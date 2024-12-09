package examples

func checkGoodString(s string) {
	if len(s) < 3 {
		panic("string too small")
	}

	for _, r := range s {
		if r < 97 || r > 122 {
			panic("bad char")
		}
	}
}
