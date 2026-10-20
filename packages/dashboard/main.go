package main

import (
	_ "embed"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"net/http"
)

const port = 4321

//go:embed index.html
var indexHTML []byte

type cursorStateResponse struct {
	State CursorState `json:"state"`
}

func main() {
	cursor := NewCursorStore()
	mux := http.NewServeMux()

	mux.HandleFunc("GET /{$}", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Write(indexHTML)
	})

	mux.HandleFunc("GET /api/state/cursor", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, cursor.Get())
	})

	mux.HandleFunc("POST /api/state/cursor/cycle", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, cursor.Cycle())
	})

	mux.HandleFunc("GET /api/state/cursor/events", func(w http.ResponseWriter, r *http.Request) {
		flusher, ok := w.(http.Flusher)
		if !ok {
			http.Error(w, "streaming unsupported", http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "text/event-stream")
		w.Header().Set("Cache-Control", "no-cache")

		changes, unsubscribe := cursor.Subscribe()
		defer unsubscribe()

		send := func(s CursorState) {
			fmt.Fprintf(w, "data: %s\n\n", s)
			flusher.Flush()
		}
		send(cursor.Get())
		for {
			select {
			case s := <-changes:
				send(s)
			case <-r.Context().Done():
				return
			}
		}
	})

	fmt.Printf("http://%s:%d\n", lanIP(), port)
	log.Fatal(http.ListenAndServe(fmt.Sprintf(":%d", port), mux))
}

func writeJSON(w http.ResponseWriter, s CursorState) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(cursorStateResponse{s})
}

// lanIP returns the address other devices on the network can reach us at,
// falling back to localhost.
func lanIP() string {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return "localhost"
	}
	for _, a := range addrs {
		if ipnet, ok := a.(*net.IPNet); ok && !ipnet.IP.IsLoopback() && ipnet.IP.To4() != nil {
			return ipnet.IP.String()
		}
	}
	return "localhost"
}
