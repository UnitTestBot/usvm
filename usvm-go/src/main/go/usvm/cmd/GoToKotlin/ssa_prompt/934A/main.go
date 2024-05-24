package main

func main() {
	var t = 10
	//fmt.Fscan(stdin, &t)

	for i := 0; i < t; i++ {
		solve()
	}

	//stdout.Flush()
}

func solve() {
	var n int
	//fmt.Fscan(stdin, &n)

	a := make([]int, n)

	for i := 0; i < n; i++ {
		var val int
		//fmt.Fscan(stdin, &val)
		a[val]++
	}

	usedOne := false
	for i := 0; i < n; i++ {
		if a[i] == 0 {
			//fmt.Fprintln(stdout, i)
			return
		}
		if a[i] > 1 {
			continue
		}
		if usedOne {
			//fmt.Fprintln(stdout, i)
			return
		}
		usedOne = true
	}
	//fmt.Fprintln(stdout, n)
}
