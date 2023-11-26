package examples

func twoSum(nums []int, target int) []int {
	m := make(map[int]int, len(nums))
	for i := range nums {
		n := nums[i]
		m[target-n] = i
	}
	for i := range nums {
		n := nums[i]
		if _, ok := m[n]; !ok {
			continue
		}
		if m[n] != i {
			return []int{i, m[n]}
		}
	}

	return nil
}

func containsNearbyDuplicate(nums []int, k int) bool {
	m := make(map[int]int, k)
	for i := 0; i < len(nums); i++ {
		m[nums[i]]++
		if m[nums[i]] >= 2 {
			return true
		}
		if i-k >= 0 {
			m[nums[i-k]]--
		}
	}
	return false
}

func canVisitAllRooms(rooms [][]int) bool {
	n := len(rooms)
	if n <= 1 {
		panic("too small rooms list")
	}
	for i := range rooms {
		if len(rooms[i]) == 0 {
			panic("empty room")
		}
		for j := range rooms[i] {
			if rooms[i][j] < 0 || rooms[i][j] >= n {
				panic("illegal key")
			}
		}
	}

	cur := make([]int, 0, n)
	cur = append(cur, 0)
	next := make([]int, 0, n)
	v := make(map[int]bool, n)
	for len(cur) > 0 {
		for _, i := range cur {
			if v[i] {
				continue
			}
			v[i] = true
			for _, k := range rooms[i] {
				next = append(next, k)
			}
		}
		cur = next
		next = make([]int, 0, n)
	}
	return len(v) == n
}
