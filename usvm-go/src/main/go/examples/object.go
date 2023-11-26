package examples

type Object struct {
	value int
}

func (o *Object) Get() int {
	if o.value == 0 {
		return -1
	}
	return o.value
}

func (o *Object) Set(i int) {
	o.value = i
}

func (o *Object) SetAndReturn(i int) int {
	if o == nil {
		return -1001
	}
	if i == o.value {
		return 1001
	}
	o.value = i
	return o.value
}

func ModifyAndGet(o *Object, i int) int {
	return o.SetAndReturn(i)
}
