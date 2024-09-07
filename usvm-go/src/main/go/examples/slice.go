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
