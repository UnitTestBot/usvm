package main

import (
        "fmt"
        "strings"
)

// Define a simple struct
type Person struct {
        Name string
        Age  int
}

// Method for the Person struct
func (p Person) SayHello() string {
        return fmt.Sprintf("Hello, my name is %s and I'm %d years old.", p.Name, p.Age)
}

// Function to convert a string to uppercase
func toUppercase(s string) string {
        return strings.ToUpper(s)
}

func main() {
        // Print a simple message
        fmt.Println("Welcome to Go!")

        // Create a slice of integers
        numbers := []int{1, 2, 3, 4, 5}
        line := ""

        // Use a for loop to iterate over the slice
        sum := 0
        for _, num := range numbers {
                sum += num
        }
        fmt.Printf("Sum of numbers: %d\n", sum)

        // Create an instance of Person
        person := Person{Name: "Alice", Age: 30}

        // Call the SayHello method
        fmt.Println(person.SayHello())

        // Use the toUppercase function
        message := "go is awesome"
        uppercaseMessage := toUppercase(message)
        fmt.Printf("Original: %s\nUppercase: %s\n", message, uppercaseMessage)
}