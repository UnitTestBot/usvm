package examples

const globalIntConst = 555

var globalIntVar = 444

func globalSimple(a int) int {
	if a > globalIntConst {
		globalIntVar = a - globalIntConst
	} else if a < globalIntConst {
		globalIntVar = globalIntConst - a
	}
	return globalIntVar
}

var globalArray [256]int

func globalArraySimple(i int) int {
	if i > 255 || i < 0 {
		return -1
	}

	globalArray[i] = 5
	return globalArray[i]
}
