package main

import (
	"fmt"
	"log"
	"net/http"
)

const addr = ":8080"

func main() {
	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, "hello world")
	})

	fmt.Println("hello world")
	log.Printf("debug page on http://localhost%s", addr)
	log.Fatal(http.ListenAndServe(addr, nil))
}
