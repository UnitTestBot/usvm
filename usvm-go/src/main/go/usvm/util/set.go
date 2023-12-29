package util

type Set[T comparable] struct {
	content map[T]struct{}
}

func NewSet[T comparable]() Set[T] {
	return Set[T]{
		content: make(map[T]struct{}),
	}
}

func (s Set[T]) Contains(value T) bool {
	_, ok := s.content[value]
	return ok
}

func (s Set[T]) Insert(value T) {
	s.content[value] = struct{}{}
}
