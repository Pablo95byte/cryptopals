package main

import (
	"encoding/base64"
	"encoding/hex"
	"fmt"
)

func main() {
	var input string
	fmt.Print("Enter the string \n")
	fmt.Scanln(&input)

	bytes, err := hex.DecodeString(input)
	if err != nil {
	}

	fmt.Print("Decoded String: \n")
	fmt.Println(bytes) // Stampa:
	base64Str := base64.StdEncoding.EncodeToString(bytes)

	fmt.Print("base64 String: \n")
	fmt.Println(base64Str) // Stampa: Q2lhbw==
}
