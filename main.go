package main

import (
	"encoding/base64"
	"encoding/hex"
	"fmt"
)

func main() {
	s := "565656"
	//b := []byte(s)

	fmt.Println(s) // Stampa: Q2lhbw==
	bytes, err := hex.DecodeString(s)
	if err != nil {
	}

	fmt.Println(bytes) // Stampa: Q2lhbw==
	base64Str := base64.StdEncoding.EncodeToString(bytes)

	fmt.Println(base64Str) // Stampa: Q2lhbw==
}
