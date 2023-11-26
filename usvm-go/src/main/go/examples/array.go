package examples

func arrayIndex(arr [3]int, i int) int {
	return arr[i]
}

func arrayIndexMake(i int) int {
	arr := [3]int{1, 2, 3}
	return arr[i]
}

func arraySlice() []int {
	arr := [3]int{1, 2, 3}
	return arr[1:2]
}
