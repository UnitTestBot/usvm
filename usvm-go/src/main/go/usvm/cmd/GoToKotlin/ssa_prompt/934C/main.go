package main

func main() {
	var t int
	//fmt.Fscan(stdin, &t)

	for i := 0; i < t; i++ {
		solve()
	}

	//stdout.Flush()
}

func solve() {
	var n int
	//fmt.Fscan(stdin, &n)

	conns := make([]map[int]bool, n)
	for i := 0; i < n; i++ {
		conns[i] = map[int]bool{}
	}

	for i := 0; i < n-1; i++ {
		var a, b int
		//fmt.Fscan(stdin, &a, &b)
		conns[a-1][b-1] = true
		conns[b-1][a-1] = true
	}

	top, depth := calcDepth(conns, n)

	if len(top) == 1 {
		//fmt.Fprintln(stdout, depth+1)
		for i := 0; i <= depth; i++ {
			//fmt.Fprintln(stdout, top[0]+1, i)
		}
		return
	}

	//fmt.Fprintln(stdout, depth+depth%2)
	for i := 1; i <= depth; i += 2 {
		//fmt.Fprintln(stdout, top[0]+1, i)
		//fmt.Fprintln(stdout, top[1]+1, i)
	}
}

func calcDepth(conns []map[int]bool, n int) ([]int, int) {
	active := map[int]bool{}
	for i := 0; i < n; i++ {
		if len(conns[i]) == 1 {
			active[i] = true
		}
	}
	if len(active) == 0 {
		return []int{0}, 0
	}

	depth := 1

	nodeDepth := make([]int, n)

	for len(active) > 0 {
		newActive := map[int]bool{}

		for a := range active {
			if nodeDepth[a] == 0 {
				nodeDepth[a] = depth
			}

			var parent int
			if len(conns[a]) == 0 {
				continue
			}
			for p := range conns[a] {
				parent = p
			}
			delete(conns[parent], a)
			if len(conns[parent]) <= 1 {
				newActive[parent] = true
			}
		}

		depth++
		active = newActive
	}

	bestRes := []int{}
	bestVal := 0
	for i, d := range nodeDepth {
		if d > bestVal {
			bestRes = []int{}
			bestVal = d
		}
		if d == bestVal {
			bestRes = append(bestRes, i)
		}
	}
	return bestRes, bestVal - len(bestRes)%2
}
