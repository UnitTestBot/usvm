package examples

func nameSmall(p Person) bool {
	if len(p.Name) < 3 {
		return false
	}

	return true
}
