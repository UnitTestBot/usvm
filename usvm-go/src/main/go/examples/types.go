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
