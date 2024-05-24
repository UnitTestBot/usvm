package main

var mod = 1_000_000_009
var p = 3_259
var invP = 286_897_824

func main() {
	var t int
	//fmt.Fscan(stdin, &t)

	for i := 0; i < t; i++ {
		solve()
	}

	//stdout.Flush()
}

func solve() {
	var n, q int
	//fmt.Fscan(stdin, &n, &q)

	var s string
	//fmt.Fscan(stdin, &s)

	pows, invPows := preCalcPows(n)

	repeatStart := make([]int, n)
	repeatStart[0] = 0
	partialHashes := make([]int, n)
	partialHashes[0] = int(s[0])

	for i := 1; i < n; i++ {
		partialHashes[i] = (partialHashes[i-1] + int(s[i])*pows[i]) % mod
		if s[i] == s[i-1] {
			repeatStart[i] = repeatStart[i-1]
		} else {
			repeatStart[i] = i
		}
	}

	alternateStart := make([]int, n)
	alternateStart[0] = 0
	alternateStart[1] = 0

	for i := 2; i < n; i++ {
		if s[i] == s[i-2] {
			alternateStart[i] = alternateStart[i-1]
		} else {
			alternateStart[i] = i - 1
		}
	}

	reversePartialHashes := make([]int, n)
	reversePartialHashes[0] = int(s[n-1])

	for i := 1; i < n; i++ {
		reversePartialHashes[i] = (reversePartialHashes[i-1] + int(s[n-i-1])*pows[i]) % mod
	}

	for i := 0; i < q; i++ {
		var k, l int
		//fmt.Fscan(stdin, &k, &l)
		k--
		l--

		if repeatStart[l] <= k {
			//fmt.Fprintln(stdout, 0)
			continue
		}

		length := l - k + 1

		if alternateStart[l] <= k {
			base := length * (length + 1) / 2
			baddies := ((length + 1) / 2) * ((length + 1) / 2)
			base = base - baddies
			//fmt.Fprintln(stdout, base-baddies)
			continue
		}

		val := length*(length+1)/2 - 1

		isPalindrome := isPalindrome(n, k, l, partialHashes, reversePartialHashes, invPows)
		if isPalindrome {
			val -= length
		}
		//fmt.Fprintln(stdout, val)
	}
}

func preCalcPows(n int) ([]int, []int) {
	powers := make([]int, n)
	invPowers := make([]int, n)

	powers[0] = 1
	invPowers[0] = 1
	for i := 1; i < n; i++ {
		powers[i] = (powers[i-1] * p) % mod
		invPowers[i] = (invPowers[i-1] * invP) % mod
	}

	return powers, invPowers
}

func isPalindrome(n, k, l int, hashes, reverseHashes []int, invP []int) bool {
	hash := hashes[l]
	if k > 0 {
		hash += mod - hashes[k-1]
	}
	hash *= invP[k]
	hash %= mod

	reverseHash := reverseHashes[n-k-1]
	if n-l-1 > 0 {
		reverseHash += mod - reverseHashes[n-l-2]
	}
	reverseHash *= invP[n-l-1]
	reverseHash %= mod

	return hash == reverseHash
}
