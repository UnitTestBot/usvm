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

func sliceAppend(nums []int) []int {
	nums = append(nums, 3)
	nums = append(nums, []int{1, 2}...)
	return append(nums, 4)
}

func sliceAppendTwo(nums1, nums2 []int) []int {
	if len(nums1) < 5 || len(nums1) > 10 || len(nums2) < 5 || len(nums2) > 10 {
		return nil
	}

	nums := append(nums1, nums2...)
	return append(nums, 4)
}

func sliceAppendSimple(a []int) []int {
	if len(a) == 0 || a == nil {
		return []int{1, 2}
	}

	var c []int = nil
	b := append(c, 5, 6)
	return append(a, b...)
}

func sliceSumMatrix(matrix [][]int) int {
	if len(matrix) < 3 {
		panic("too small")
	}
	if len(matrix[0]) < 3 {
		panic("too small")
	}

	s := 0
	for _, m := range matrix {
		for _, n := range m {
			s += n
		}
	}
	return s
}

func sliceCopySimple(a []int) []int {
	if len(a) == 0 {
		return a
	}

	a[0] = 5
	b := make([]int, len(a))
	copy(b, a)
	return b
}

type customSlice []int

func sliceCustomOverwrite(arr customSlice) int {
	if len(arr) == 0 {
		return -1
	}
	arr[0] = 152
	return arr[0]
}

func sliceCustomAppend() customSlice {
	c := make(customSlice, 0)
	c = append(c, customSlice{1, 2, 3}...)
	c = append(c, []int{4, 5}...)
	c = append(c, 123)
	return c
}

func sliceCustomSlice() customSlice {
	c := customSlice{1, 2, 3, 4, 5}
	return c[1:3]
}

func sliceCustomCopySimple(a customSlice) customSlice {
	if len(a) == 0 {
		return a
	}

	a[0] = 5
	b := make(customSlice, len(a))
	copy(b, a)
	return b
}

func sliceCustomToArrayPointer(nums customSlice) *[3]int {
	a := (*[3]int)(nums)
	a[1] = 1
	return a
}
