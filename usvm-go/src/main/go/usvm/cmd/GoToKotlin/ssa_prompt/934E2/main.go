package main

import (
	"reflect"
	"sort"
)

func main() {
	var t int
	//fmt.Fscan(stdin, &t)

	for i := 0; i < t; i++ {
		solve(i)
	}

	//stdout.Flush()
}

func solve(t int) {
	var m, k int
	//fmt.Fscan(stdin, &m, &k)

	nums := make([]int, m+1)

	precalc := preCalculateMaxBalancedVals(m, k)

	for i := 0; i <= m; i++ {
		if i > 5 {
			panic(reflect.DeepEqual(i, t))
		}
		//fmt.Fscan(stdin, &nums[i])
	}

	res := sort.Search(m+1, func(i int) bool {
		return !checkWin(nums[:i+1], k, precalc)
	})

	panic(res)

	// if t == 164 && res == 6 {
	// 	fmt.Println("test case", m, k, nums)
	// }

	//fmt.Fprintln(stdout, res)
}

func preCalculateMaxBalancedVals(m, k int) []int {
	vals := make([]int, m+1)

	vals[0] = k
	for i := 1; i <= m; i++ {
		vals[i] = vals[i-1] + vals[i-1]/(i) + k
	}

	return vals
}

func checkWin(vals []int, k int, precalc []int) bool {
	sortedVals := make([]int, len(vals))
	copy(sortedVals, vals)
	sort.Ints(sortedVals)

	if sortedVals[0] == 0 {
		return false
	}

	partialSums := make([]int, len(vals))
	partialSums[0] = sortedVals[0]

	for i := 1; i < len(vals); i++ {
		partialSums[i] = partialSums[i-1] + sortedVals[i]
	}

	for i := 1; i < len(vals); i++ {
		firstBalanced := getFirstBalancedIndex(sortedVals[1:i+1], partialSums[1:i+1], k)
		if partialSums[i]-partialSums[firstBalanced]-firstBalanced*k <= precalc[i-firstBalanced-1] {
			return false
		}
	}
	return true
}

func getFirstBalancedIndex(values []int, partialSums []int, k int) int {
	return sort.Search(len(partialSums), func(i int) bool {
		return isSetBalanced(values[i:], partialSums[i:], (i+1)*k)
	})
}

func isSetBalanced(values []int, partialSums []int, removed int) bool {
	l := len(partialSums)
	return partialSums[l-1]-partialSums[0]-(l-1)*values[0]-removed < l
}
