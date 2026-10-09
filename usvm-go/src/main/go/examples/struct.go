package examples

func nameSmall(p Person) bool {
	checkGoodString(p.Name)

	return len(p.Name) < 5
}
