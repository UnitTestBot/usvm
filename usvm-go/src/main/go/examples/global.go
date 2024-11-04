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
