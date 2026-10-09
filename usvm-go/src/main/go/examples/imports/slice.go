package imports

import (
	"sort"
)

func sliceCast(arr []int) []int {
	s := sort.IntSlice(arr)
	x := s[0]
	s[0] = x + 2
	return []int(s)
}
