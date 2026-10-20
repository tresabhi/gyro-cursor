package main

import "sync"

type CursorState string

const (
	CursorDisabled CursorState = "disabled"
	CursorMove     CursorState = "cursor"
	CursorClick    CursorState = "click"
	CursorScroll   CursorState = "scroll"
)

var cursorCycle = []CursorState{CursorDisabled, CursorMove, CursorClick, CursorScroll}

// CursorStore holds the current state and fans changes out to subscribers.
type CursorStore struct {
	mu    sync.Mutex
	index int
	subs  map[chan CursorState]struct{}
}

func NewCursorStore() *CursorStore {
	return &CursorStore{subs: map[chan CursorState]struct{}{}}
}

func (s *CursorStore) Get() CursorState {
	s.mu.Lock()
	defer s.mu.Unlock()
	return cursorCycle[s.index]
}

// Cycle advances to the next state, wrapping around, and returns it.
func (s *CursorStore) Cycle() CursorState {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.index = (s.index + 1) % len(cursorCycle)
	next := cursorCycle[s.index]
	for ch := range s.subs {
		// Keep only the latest state for slow subscribers.
		select {
		case <-ch:
		default:
		}
		ch <- next
	}
	return next
}

// Subscribe returns a channel of state changes and a function to stop it.
func (s *CursorStore) Subscribe() (<-chan CursorState, func()) {
	ch := make(chan CursorState, 1)
	s.mu.Lock()
	s.subs[ch] = struct{}{}
	s.mu.Unlock()
	return ch, func() {
		s.mu.Lock()
		delete(s.subs, ch)
		s.mu.Unlock()
	}
}
