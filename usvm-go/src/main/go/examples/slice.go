package examples

func sliceSimple(arr []int) int {
	arr[0] = 5
	return arr[0]
}

func sliceOverwrite(arr []int) int {
	if len(arr) == 0 {
		return -1
	}
	arr[0] = 152
	return arr[0]
}

func sliceAlloc(l int) []int {
	arr := make([]int, l)
	if l < 5 || l > 10 {
		return arr
	}
	arr[3] = 111
	return arr
}

func sliceFirst(nums []int) int {
	if len(nums) < 2 || len(nums) > 20 {
		return -1
	}
	if nums[0] == 1 && nums[1] == 2 {
		return 1
	}
	return 0
}

func sliceSum(nums []int) int {
	if len(nums) < 5 {
		return -228
	}
	res := 0
	for i := range nums {
		if nums[i] > 0 {
			res += nums[i]
		} else if nums[i] < 0 {
			res -= nums[i]
		}
	}
	return res
}

func sliceCompare(nums []int) int {
	i := nums[0]
	j := nums[1]
	if i < j {
		return i
	}
	return j
}

var sliceComparator = sliceCompare

func sliceCompareFuncVar(nums []int) int {
	if sliceComparator(nums) == 5 {
		return 3
	}
	return 1
}

func sliceSliceFull(nums []int) []int {
	if len(nums) < 5 || len(nums) > 10 {
		return nil
	}
	return nums[:]
}

func sliceSliceFrom(nums []int, i int) []int {
	if len(nums) < 5 || len(nums) > 10 {
		return nil
	}
	return nums[i:]
}

func sliceSliceTo(nums []int, i int) []int {
	if len(nums) < 5 || len(nums) > 10 || i > 3 {
		return nil
	}
	return nums[:i]
}

func sliceSlice(nums []int, i, j int) []int {
	if len(nums) < 5 || len(nums) > 10 || i == j {
		return nil
	}

	n := nums[:]
	n = n[:j]
	n = n[i:]
	return n
}

func sliceToArrayPointer(nums []int) *[3]int {
	a := (*[3]int)(nums)
	a[1] = 1
	return a
}
