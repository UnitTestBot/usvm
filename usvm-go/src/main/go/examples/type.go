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

func GetAge(p Person) int {
	return p.GetAge()
}

type Building struct {
	Height int
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
	return NamedInt(i + 1)
}

func callNamedInt(i int) int {
	return NamedInt(i).square()
}

func callCreature(c Creature) int {
	if c == nil {
		return -1
	}

	return c.GetAge()
}

func assertCreature() (Person, bool) {
	var c Creature = Person{
		Name: "Name",
		Age:  42,
	}
	p, ok := c.(Person)
	return p, ok
}

func assertCreatureNoComma() Person {
	var c Creature = Person{
		Name: "Name",
		Age:  42,
	}
	return c.(Person)
}

func assertCreatureFailNoComma() Building {
	var c any = Person{
		Name: "Name",
		Age:  42,
	}
	return c.(Building)
}

func assertCreaturePointer() (*Person, bool) {
	var c Creature = &Person{
		Name: "Name",
		Age:  42,
	}
	p, ok := c.(*Person)
	return p, ok
}

func assertCreatureArgument(c Creature) (Person, bool) {
	if p, ok := c.(Person); ok {
		return p, ok
	}

	return Person{}, false
}

func assertCreatureArgumentCall(c Creature) int {
	if p, ok := c.(Person); ok {
		return p.GetAge()
	}

	return -1
}

func assertNamedIntCall(i any) int {
	if n, ok := i.(NamedInt); ok {
		return n.square()
	}
	if n, ok := i.(int); ok {
		if n*n <= 1 {
			panic("invalid int")
		}
		return n * n
	}
	return -1
}

func assertIntAny() int {
	var a any = 2
	var b any = 3
	return a.(int) + b.(int)
}

type Node struct {
	left  *Node
	right *Node
}

type X *X
