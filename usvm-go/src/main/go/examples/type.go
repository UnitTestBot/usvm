package examples

type Person struct {
	Name string
	Age  int
}

func (p Person) GetName() string {
	return p.Name
}

func (p Person) GetAge() int {
	return p.Age
}

func (p Person) WithName(name string) Creature {
	checkGoodString(name)
	p.Name = name
	return p
}

func (p Person) Validate() (bool, error) {
	return true, nil
}

type Creature interface {
	GetAge() int
	GetName() string
	WithName(n string) Creature
	Validate() (bool, error)
}

type NamedInt int

func (n NamedInt) square() int {
	if n == 0 {
		panic("zero")
	}
	if n == 1 || n == -1 {
		panic("one")
	}

	return int(n * n)
}

func toNamedInt(i int) NamedInt {
	return NamedInt(i)
}

func callNamedInt(i int) int {
	return NamedInt(i).square()
}
