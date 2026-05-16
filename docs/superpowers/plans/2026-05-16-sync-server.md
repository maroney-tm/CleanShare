# CleanShare Sync Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone Go HTTP server that stores CleanShare history in SQLite and syncs it to Android and desktop clients via REST + SSE over LAN.

**Architecture:** A single Go binary with five source files: `store.go` owns SQLite CRUD, `hub.go` owns the SSE pub/sub goroutine, `server.go` owns all HTTP handlers, `mdns.go` registers the service on the LAN, and `main.go` wires everything together. All files share the `main` package, so no intra-project imports are needed.

**Tech Stack:** Go 1.22+, `modernc.org/sqlite` (pure Go, no CGo), `github.com/hashicorp/mdns`, stdlib `net/http` (1.22 pattern-routing), `encoding/json`, `database/sql`.

---

### Task 1: Initialize the repo and Go module

**Files:**
- Create: `~/AndroidStudioProjects/CleanShareServer/` (new directory)
- Create: `~/AndroidStudioProjects/CleanShareServer/go.mod`
- Create: `~/AndroidStudioProjects/CleanShareServer/.gitignore`

- [ ] **Step 1: Create the directory and init git**

```bash
mkdir -p ~/AndroidStudioProjects/CleanShareServer
cd ~/AndroidStudioProjects/CleanShareServer
git init
```

Expected: `Initialized empty Git repository in .../CleanShareServer/.git/`

- [ ] **Step 2: Create the Go module**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
go mod init github.com/maroney/cleanshare-server
```

Expected: creates `go.mod` with `module github.com/maroney/cleanshare-server` and the installed Go version.

- [ ] **Step 3: Add dependencies**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
go get modernc.org/sqlite
go get github.com/hashicorp/mdns
```

Expected: `go.mod` and `go.sum` updated with both packages.

- [ ] **Step 4: Create `.gitignore`**

Write `~/AndroidStudioProjects/CleanShareServer/.gitignore`:
```
cleanshare.db
cleanshare.db-wal
cleanshare.db-shm
```

- [ ] **Step 5: Commit**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
git add go.mod go.sum .gitignore
git commit -m "chore: initialize Go module with sqlite and mdns deps"
```

---

### Task 2: schema.sql

**Files:**
- Create: `~/AndroidStudioProjects/CleanShareServer/schema.sql`

- [ ] **Step 1: Write the schema**

Write `~/AndroidStudioProjects/CleanShareServer/schema.sql`:
```sql
CREATE TABLE IF NOT EXISTS share_records (
    sync_id       TEXT    PRIMARY KEY,
    original_text TEXT    NOT NULL,
    cleaned_text  TEXT    NOT NULL,
    shared_at     INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL,
    notes         TEXT,
    source        TEXT    NOT NULL DEFAULT 'MOBILE'
);

CREATE TABLE IF NOT EXISTS link_metadata (
    sync_id         TEXT PRIMARY KEY REFERENCES share_records(sync_id) ON DELETE CASCADE,
    title           TEXT,
    thumbnail_url   TEXT,
    description     TEXT,
    article_snippet TEXT,
    content_type    TEXT NOT NULL,
    fetch_status    TEXT NOT NULL
);

PRAGMA foreign_keys = ON;
```

Note: `ON DELETE CASCADE` on `link_metadata` means deleting a `share_record` automatically removes its metadata row.

- [ ] **Step 2: Commit**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
git add schema.sql
git commit -m "feat: add SQLite schema"
```

---

### Task 3: store.go — types and SQLite CRUD

**Files:**
- Create: `~/AndroidStudioProjects/CleanShareServer/store.go`

- [ ] **Step 1: Write store.go**

Write `~/AndroidStudioProjects/CleanShareServer/store.go`:
```go
package main

import (
	"database/sql"
	_ "embed"

	_ "modernc.org/sqlite"
)

//go:embed schema.sql
var schemaSQL string

// ---- Domain types ----

type ShareRecord struct {
	SyncID       string        `json:"syncId"`
	OriginalText string        `json:"originalText"`
	CleanedText  string        `json:"cleanedText"`
	SharedAt     int64         `json:"sharedAt"`
	UpdatedAt    int64         `json:"updatedAt"`
	Notes        *string       `json:"notes"`
	Source       string        `json:"source"`
	LinkMetadata *LinkMetadata `json:"linkMetadata"`
}

type LinkMetadata struct {
	Title          *string `json:"title"`
	ThumbnailURL   *string `json:"thumbnailUrl"`
	Description    *string `json:"description"`
	ArticleSnippet *string `json:"articleSnippet"`
	ContentType    string  `json:"contentType"`
	FetchStatus    string  `json:"fetchStatus"`
}

// PatchRequest is the body for PATCH /records/{syncId}.
type PatchRequest struct {
	Notes     *string `json:"notes"`
	UpdatedAt int64   `json:"updatedAt"`
}

// MetadataRequest is the body for PUT /records/{syncId}/metadata.
type MetadataRequest struct {
	Title          *string `json:"title"`
	ThumbnailURL   *string `json:"thumbnailUrl"`
	Description    *string `json:"description"`
	ArticleSnippet *string `json:"articleSnippet"`
	ContentType    string  `json:"contentType"`
	FetchStatus    string  `json:"fetchStatus"`
}

// ---- Store ----

type Store struct {
	db *sql.DB
}

// NewStore opens (or creates) the SQLite database at path and applies the schema.
// Use ":memory:" for tests.
func NewStore(path string) (*Store, error) {
	db, err := sql.Open("sqlite", path)
	if err != nil {
		return nil, err
	}
	// SQLite supports only one writer at a time.
	db.SetMaxOpenConns(1)
	if err := db.Ping(); err != nil {
		return nil, err
	}
	if _, err := db.Exec("PRAGMA foreign_keys = ON"); err != nil {
		return nil, err
	}
	if _, err := db.Exec(schemaSQL); err != nil {
		return nil, err
	}
	return &Store{db: db}, nil
}

// AllRecords returns all records ordered newest-first, with metadata embedded.
func (s *Store) AllRecords() ([]ShareRecord, error) {
	rows, err := s.db.Query(`
		SELECT r.sync_id, r.original_text, r.cleaned_text, r.shared_at, r.updated_at, r.notes, r.source,
		       m.title, m.thumbnail_url, m.description, m.article_snippet, m.content_type, m.fetch_status
		FROM share_records r
		LEFT JOIN link_metadata m ON r.sync_id = m.sync_id
		ORDER BY r.shared_at DESC
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var records []ShareRecord
	for rows.Next() {
		r, err := scanRecord(rows)
		if err != nil {
			return nil, err
		}
		records = append(records, r)
	}
	if records == nil {
		records = []ShareRecord{}
	}
	return records, rows.Err()
}

// InsertRecord inserts a new share record. Returns an error if sync_id already exists.
func (s *Store) InsertRecord(r ShareRecord) error {
	_, err := s.db.Exec(
		`INSERT INTO share_records (sync_id, original_text, cleaned_text, shared_at, updated_at, notes, source)
		 VALUES (?, ?, ?, ?, ?, ?, ?)`,
		r.SyncID, r.OriginalText, r.CleanedText, r.SharedAt, r.UpdatedAt, r.Notes, r.Source,
	)
	return err
}

// PatchRecord applies notes+updatedAt if the incoming updatedAt is strictly newer (LWW).
// Returns the resulting record and whether the patch was applied.
// Returns (nil, false, nil) when the syncId does not exist.
func (s *Store) PatchRecord(syncID string, notes *string, updatedAt int64) (*ShareRecord, bool, error) {
	tx, err := s.db.Begin()
	if err != nil {
		return nil, false, err
	}
	defer tx.Rollback() //nolint:errcheck

	var stored int64
	err = tx.QueryRow("SELECT updated_at FROM share_records WHERE sync_id = ?", syncID).Scan(&stored)
	if err == sql.ErrNoRows {
		return nil, false, nil
	}
	if err != nil {
		return nil, false, err
	}

	applied := updatedAt > stored
	if applied {
		if _, err = tx.Exec(
			"UPDATE share_records SET notes = ?, updated_at = ? WHERE sync_id = ?",
			notes, updatedAt, syncID,
		); err != nil {
			return nil, false, err
		}
	}

	if err := tx.Commit(); err != nil {
		return nil, false, err
	}

	record, err := s.getRecord(syncID)
	return record, applied, err
}

// DeleteRecord hard-deletes a record (metadata is cascade-deleted by the FK constraint).
func (s *Store) DeleteRecord(syncID string) error {
	_, err := s.db.Exec("DELETE FROM share_records WHERE sync_id = ?", syncID)
	return err
}

// UpsertMetadata inserts or replaces the link_metadata row for syncID,
// then returns the full record (with embedded metadata) for broadcasting.
func (s *Store) UpsertMetadata(syncID string, meta MetadataRequest) (*ShareRecord, error) {
	_, err := s.db.Exec(`
		INSERT INTO link_metadata (sync_id, title, thumbnail_url, description, article_snippet, content_type, fetch_status)
		VALUES (?, ?, ?, ?, ?, ?, ?)
		ON CONFLICT(sync_id) DO UPDATE SET
			title           = excluded.title,
			thumbnail_url   = excluded.thumbnail_url,
			description     = excluded.description,
			article_snippet = excluded.article_snippet,
			content_type    = excluded.content_type,
			fetch_status    = excluded.fetch_status
	`, syncID, meta.Title, meta.ThumbnailURL, meta.Description, meta.ArticleSnippet, meta.ContentType, meta.FetchStatus)
	if err != nil {
		return nil, err
	}
	return s.getRecord(syncID)
}

// ---- helpers ----

type scanner interface {
	Scan(dest ...any) error
}

func scanRecord(s scanner) (ShareRecord, error) {
	var r ShareRecord
	var notes sql.NullString
	var title, thumbURL, desc, snippet, ctype, fstatus sql.NullString
	if err := s.Scan(
		&r.SyncID, &r.OriginalText, &r.CleanedText, &r.SharedAt, &r.UpdatedAt, &notes, &r.Source,
		&title, &thumbURL, &desc, &snippet, &ctype, &fstatus,
	); err != nil {
		return r, err
	}
	if notes.Valid {
		r.Notes = &notes.String
	}
	if ctype.Valid {
		r.LinkMetadata = &LinkMetadata{
			Title:          nullableStr(title),
			ThumbnailURL:   nullableStr(thumbURL),
			Description:    nullableStr(desc),
			ArticleSnippet: nullableStr(snippet),
			ContentType:    ctype.String,
			FetchStatus:    fstatus.String,
		}
	}
	return r, nil
}

func (s *Store) getRecord(syncID string) (*ShareRecord, error) {
	row := s.db.QueryRow(`
		SELECT r.sync_id, r.original_text, r.cleaned_text, r.shared_at, r.updated_at, r.notes, r.source,
		       m.title, m.thumbnail_url, m.description, m.article_snippet, m.content_type, m.fetch_status
		FROM share_records r
		LEFT JOIN link_metadata m ON r.sync_id = m.sync_id
		WHERE r.sync_id = ?
	`, syncID)
	r, err := scanRecord(row)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &r, nil
}

func nullableStr(ns sql.NullString) *string {
	if ns.Valid {
		return &ns.String
	}
	return nil
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
go build ./...
```

Expected: no output (clean build).

- [ ] **Step 3: Commit**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
git add store.go
git commit -m "feat: add Store with SQLite CRUD"
```

---

### Task 4: store_test.go

**Files:**
- Create: `~/AndroidStudioProjects/CleanShareServer/store_test.go`

- [ ] **Step 1: Write the failing tests**

Write `~/AndroidStudioProjects/CleanShareServer/store_test.go`:
```go
package main

import (
	"testing"
)

func newTestStore(t *testing.T) *Store {
	t.Helper()
	s, err := NewStore(":memory:")
	if err != nil {
		t.Fatalf("NewStore: %v", err)
	}
	return s
}

func strPtr(s string) *string { return &s }

func TestAllRecords_empty(t *testing.T) {
	s := newTestStore(t)
	records, err := s.AllRecords()
	if err != nil {
		t.Fatal(err)
	}
	if len(records) != 0 {
		t.Fatalf("want 0 records, got %d", len(records))
	}
}

func TestInsertAndAllRecords(t *testing.T) {
	s := newTestStore(t)
	rec := ShareRecord{
		SyncID:       "uuid-1",
		OriginalText: "https://example.com?utm_source=foo",
		CleanedText:  "https://example.com",
		SharedAt:     1000,
		UpdatedAt:    1000,
		Source:       "MOBILE",
	}
	if err := s.InsertRecord(rec); err != nil {
		t.Fatal(err)
	}
	records, err := s.AllRecords()
	if err != nil {
		t.Fatal(err)
	}
	if len(records) != 1 {
		t.Fatalf("want 1 record, got %d", len(records))
	}
	got := records[0]
	if got.SyncID != "uuid-1" {
		t.Errorf("SyncID: want uuid-1, got %s", got.SyncID)
	}
	if got.LinkMetadata != nil {
		t.Errorf("LinkMetadata: want nil before upsert, got %+v", got.LinkMetadata)
	}
}

func TestPatchRecord_appliesWhenNewer(t *testing.T) {
	s := newTestStore(t)
	_ = s.InsertRecord(ShareRecord{SyncID: "u1", OriginalText: "o", CleanedText: "c", SharedAt: 1000, UpdatedAt: 1000, Source: "MOBILE"})

	note := "my note"
	got, applied, err := s.PatchRecord("u1", &note, 2000)
	if err != nil {
		t.Fatal(err)
	}
	if !applied {
		t.Fatal("want applied=true")
	}
	if got == nil || got.Notes == nil || *got.Notes != "my note" {
		t.Errorf("want notes='my note', got %+v", got)
	}
	if got.UpdatedAt != 2000 {
		t.Errorf("want updatedAt=2000, got %d", got.UpdatedAt)
	}
}

func TestPatchRecord_rejectsWhenOlderOrEqual(t *testing.T) {
	s := newTestStore(t)
	_ = s.InsertRecord(ShareRecord{SyncID: "u1", OriginalText: "o", CleanedText: "c", SharedAt: 1000, UpdatedAt: 2000, Source: "MOBILE"})

	note := "stale note"
	_, applied, err := s.PatchRecord("u1", &note, 1500)
	if err != nil {
		t.Fatal(err)
	}
	if applied {
		t.Fatal("want applied=false for stale updatedAt")
	}
	// Verify DB unchanged
	records, _ := s.AllRecords()
	if records[0].Notes != nil {
		t.Errorf("want notes unchanged (nil), got %+v", records[0].Notes)
	}
}

func TestPatchRecord_notFound(t *testing.T) {
	s := newTestStore(t)
	got, applied, err := s.PatchRecord("no-such-id", nil, 999)
	if err != nil {
		t.Fatal(err)
	}
	if got != nil || applied {
		t.Errorf("want (nil, false), got (%v, %v)", got, applied)
	}
}

func TestDeleteRecord_cascadesMetadata(t *testing.T) {
	s := newTestStore(t)
	_ = s.InsertRecord(ShareRecord{SyncID: "u1", OriginalText: "o", CleanedText: "c", SharedAt: 1000, UpdatedAt: 1000, Source: "MOBILE"})
	_, _ = s.UpsertMetadata("u1", MetadataRequest{ContentType: "UNKNOWN", FetchStatus: "SUCCESS"})

	if err := s.DeleteRecord("u1"); err != nil {
		t.Fatal(err)
	}
	records, _ := s.AllRecords()
	if len(records) != 0 {
		t.Fatalf("want 0 records after delete, got %d", len(records))
	}
	// Verify metadata was cascade-deleted (no orphan row)
	var count int
	_ = s.db.QueryRow("SELECT COUNT(*) FROM link_metadata WHERE sync_id = 'u1'").Scan(&count)
	if count != 0 {
		t.Errorf("want 0 metadata rows, got %d (cascade delete failed)", count)
	}
}

func TestUpsertMetadata(t *testing.T) {
	s := newTestStore(t)
	_ = s.InsertRecord(ShareRecord{SyncID: "u1", OriginalText: "o", CleanedText: "c", SharedAt: 1000, UpdatedAt: 1000, Source: "MOBILE"})

	full, err := s.UpsertMetadata("u1", MetadataRequest{
		Title:       strPtr("Test Title"),
		ContentType: "ARTICLE",
		FetchStatus: "SUCCESS",
	})
	if err != nil {
		t.Fatal(err)
	}
	if full.LinkMetadata == nil {
		t.Fatal("want LinkMetadata populated")
	}
	if full.LinkMetadata.ContentType != "ARTICLE" {
		t.Errorf("ContentType: want ARTICLE, got %s", full.LinkMetadata.ContentType)
	}
	if full.LinkMetadata.Title == nil || *full.LinkMetadata.Title != "Test Title" {
		t.Errorf("Title: want 'Test Title', got %v", full.LinkMetadata.Title)
	}
}
```

- [ ] **Step 2: Run tests and verify they pass**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
go test ./... -run TestAllRecords_empty -v
go test ./... -run TestInsertAndAllRecords -v
go test ./... -run TestPatchRecord -v
go test ./... -run TestDeleteRecord -v
go test ./... -run TestUpsertMetadata -v
```

Expected: all tests PASS.

- [ ] **Step 3: Commit**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
git add store_test.go
git commit -m "test: add Store CRUD tests"
```

---

### Task 5: hub.go — SSE pub/sub

**Files:**
- Create: `~/AndroidStudioProjects/CleanShareServer/hub.go`

- [ ] **Step 1: Write hub.go**

Write `~/AndroidStudioProjects/CleanShareServer/hub.go`:
```go
package main

// Event is a single SSE payload.
type Event struct {
	Type string
	Data []byte
}

type subscription struct {
	id string
	ch chan Event
}

// Hub fans SSE events out to all connected clients.
// Start it with go hub.Run() before accepting HTTP connections.
type Hub struct {
	subscribe   chan subscription
	unsubscribe chan string
	broadcast   chan Event
}

func NewHub() *Hub {
	return &Hub{
		subscribe:   make(chan subscription, 16),
		unsubscribe: make(chan string, 16),
		broadcast:   make(chan Event, 64),
	}
}

// Run is the hub's event loop; call in a dedicated goroutine.
func (h *Hub) Run() {
	clients := make(map[string]chan Event)
	for {
		select {
		case sub := <-h.subscribe:
			clients[sub.id] = sub.ch

		case id := <-h.unsubscribe:
			if ch, ok := clients[id]; ok {
				close(ch)
				delete(clients, id)
			}

		case ev := <-h.broadcast:
			for id, ch := range clients {
				select {
				case ch <- ev:
				default:
					// Slow client: drop it rather than blocking the hub.
					close(ch)
					delete(clients, id)
				}
			}
		}
	}
}

// Subscribe registers a new client and returns its event channel.
func (h *Hub) Subscribe(id string) chan Event {
	ch := make(chan Event, 8)
	h.subscribe <- subscription{id: id, ch: ch}
	return ch
}

// Unsubscribe removes a client and closes its channel.
func (h *Hub) Unsubscribe(id string) {
	h.unsubscribe <- id
}

// Broadcast sends an event to all connected clients.
func (h *Hub) Broadcast(ev Event) {
	h.broadcast <- ev
}
```

- [ ] **Step 2: Verify compile**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
go build ./...
```

Expected: clean.

- [ ] **Step 3: Commit**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
git add hub.go
git commit -m "feat: add SSE pub/sub Hub"
```

---

### Task 6: hub_test.go

**Files:**
- Create: `~/AndroidStudioProjects/CleanShareServer/hub_test.go`

- [ ] **Step 1: Write the tests**

Write `~/AndroidStudioProjects/CleanShareServer/hub_test.go`:
```go
package main

import (
	"testing"
	"time"
)

func startHub(t *testing.T) *Hub {
	t.Helper()
	h := NewHub()
	go h.Run()
	// Give the goroutine a tick to start reading from channels.
	time.Sleep(5 * time.Millisecond)
	return h
}

func TestHub_BroadcastDelivered(t *testing.T) {
	h := startHub(t)
	ch := h.Subscribe("client-1")
	time.Sleep(5 * time.Millisecond) // let hub process the subscription

	ev := Event{Type: "record_created", Data: []byte(`{"syncId":"u1"}`)}
	h.Broadcast(ev)

	select {
	case got := <-ch:
		if got.Type != "record_created" {
			t.Errorf("want type record_created, got %s", got.Type)
		}
	case <-time.After(500 * time.Millisecond):
		t.Fatal("timeout: event not delivered")
	}
}

func TestHub_UnsubscribeStopsDelivery(t *testing.T) {
	h := startHub(t)
	ch := h.Subscribe("client-1")
	time.Sleep(5 * time.Millisecond)

	h.Unsubscribe("client-1")
	time.Sleep(5 * time.Millisecond)

	// The channel should be closed; a receive returns immediately with zero value.
	select {
	case _, open := <-ch:
		if open {
			t.Fatal("want channel closed after unsubscribe")
		}
	case <-time.After(500 * time.Millisecond):
		t.Fatal("timeout: channel not closed after unsubscribe")
	}
}

func TestHub_MultipleClients(t *testing.T) {
	h := startHub(t)
	ch1 := h.Subscribe("client-1")
	ch2 := h.Subscribe("client-2")
	time.Sleep(5 * time.Millisecond)

	h.Broadcast(Event{Type: "record_deleted", Data: []byte(`{"syncId":"u1"}`)})

	for i, ch := range []chan Event{ch1, ch2} {
		select {
		case got := <-ch:
			if got.Type != "record_deleted" {
				t.Errorf("client %d: want record_deleted, got %s", i+1, got.Type)
			}
		case <-time.After(500 * time.Millisecond):
			t.Fatalf("client %d: timeout waiting for event", i+1)
		}
	}
}
```

- [ ] **Step 2: Run tests**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
go test ./... -run TestHub -v
```

Expected: all three PASS.

- [ ] **Step 3: Commit**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
git add hub_test.go
git commit -m "test: add Hub broadcast/unsubscribe tests"
```

---

### Task 7: server.go — HTTP handlers

**Files:**
- Create: `~/AndroidStudioProjects/CleanShareServer/server.go`

- [ ] **Step 1: Write server.go**

Write `~/AndroidStudioProjects/CleanShareServer/server.go`:
```go
package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"time"
)

type Server struct {
	store *Store
	hub   *Hub
	mux   *http.ServeMux
}

func NewServer(store *Store, hub *Hub) *Server {
	s := &Server{store: store, hub: hub, mux: http.NewServeMux()}
	s.mux.HandleFunc("GET /health", s.handleHealth)
	s.mux.HandleFunc("GET /records", s.handleGetRecords)
	s.mux.HandleFunc("POST /records", s.handlePostRecord)
	s.mux.HandleFunc("PATCH /records/{syncId}", s.handlePatchRecord)
	s.mux.HandleFunc("DELETE /records/{syncId}", s.handleDeleteRecord)
	s.mux.HandleFunc("PUT /records/{syncId}/metadata", s.handlePutMetadata)
	s.mux.HandleFunc("GET /events", s.handleEvents)
	return s
}

func (s *Server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	s.mux.ServeHTTP(w, r)
}

// GET /health
func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
}

// GET /records
func (s *Server) handleGetRecords(w http.ResponseWriter, r *http.Request) {
	records, err := s.store.AllRecords()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, records)
}

// POST /records
func (s *Server) handlePostRecord(w http.ResponseWriter, r *http.Request) {
	var rec ShareRecord
	if err := json.NewDecoder(r.Body).Decode(&rec); err != nil {
		http.Error(w, "bad request: "+err.Error(), http.StatusBadRequest)
		return
	}
	if rec.SyncID == "" || rec.CleanedText == "" {
		http.Error(w, "syncId and cleanedText are required", http.StatusBadRequest)
		return
	}
	if err := s.store.InsertRecord(rec); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	s.hub.Broadcast(Event{Type: "record_created", Data: mustMarshal(rec)})
	writeJSON(w, http.StatusCreated, rec)
}

// PATCH /records/{syncId}
func (s *Server) handlePatchRecord(w http.ResponseWriter, r *http.Request) {
	syncID := r.PathValue("syncId")
	var req PatchRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "bad request: "+err.Error(), http.StatusBadRequest)
		return
	}
	record, applied, err := s.store.PatchRecord(syncID, req.Notes, req.UpdatedAt)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if record == nil {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	if applied {
		s.hub.Broadcast(Event{Type: "record_updated", Data: mustMarshal(record)})
	}
	writeJSON(w, http.StatusOK, record)
}

// DELETE /records/{syncId}
func (s *Server) handleDeleteRecord(w http.ResponseWriter, r *http.Request) {
	syncID := r.PathValue("syncId")
	if err := s.store.DeleteRecord(syncID); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	s.hub.Broadcast(Event{
		Type: "record_deleted",
		Data: mustMarshal(map[string]string{"syncId": syncID}),
	})
	w.WriteHeader(http.StatusNoContent)
}

// PUT /records/{syncId}/metadata
func (s *Server) handlePutMetadata(w http.ResponseWriter, r *http.Request) {
	syncID := r.PathValue("syncId")
	var req MetadataRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "bad request: "+err.Error(), http.StatusBadRequest)
		return
	}
	record, err := s.store.UpsertMetadata(syncID, req)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if record == nil {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	s.hub.Broadcast(Event{Type: "record_metadata_updated", Data: mustMarshal(record)})
	writeJSON(w, http.StatusOK, record)
}

// GET /events — SSE stream
func (s *Server) handleEvents(w http.ResponseWriter, r *http.Request) {
	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "streaming not supported", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")

	clientID := r.RemoteAddr + "-" + strconv.FormatInt(time.Now().UnixNano(), 36)
	ch := s.hub.Subscribe(clientID)
	defer s.hub.Unsubscribe(clientID)

	// Initial keepalive comment so the client knows the stream is open.
	fmt.Fprintf(w, ": ping\n\n")
	flusher.Flush()

	for {
		select {
		case <-r.Context().Done():
			return
		case ev, ok := <-ch:
			if !ok {
				return
			}
			fmt.Fprintf(w, "event: %s\ndata: %s\n\n", ev.Type, ev.Data)
			flusher.Flush()
		}
	}
}

// ---- helpers ----

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(v) //nolint:errcheck
}

func mustMarshal(v any) []byte {
	b, err := json.Marshal(v)
	if err != nil {
		panic(err)
	}
	return b
}
```

- [ ] **Step 2: Verify compile**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
go build ./...
```

Expected: clean.

- [ ] **Step 3: Commit**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
git add server.go
git commit -m "feat: add HTTP handlers (REST + SSE)"
```

---

### Task 8: server_test.go

**Files:**
- Create: `~/AndroidStudioProjects/CleanShareServer/server_test.go`

- [ ] **Step 1: Write the tests**

Write `~/AndroidStudioProjects/CleanShareServer/server_test.go`:
```go
package main

import (
	"bufio"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func newTestServer(t *testing.T) (*Server, *httptest.Server) {
	t.Helper()
	store := newTestStore(t)
	hub := NewHub()
	go hub.Run()
	srv := NewServer(store, hub)
	ts := httptest.NewServer(srv)
	t.Cleanup(ts.Close)
	return srv, ts
}

func TestHealth(t *testing.T) {
	_, ts := newTestServer(t)
	resp, err := http.Get(ts.URL + "/health")
	if err != nil {
		t.Fatal(err)
	}
	if resp.StatusCode != http.StatusOK {
		t.Errorf("want 200, got %d", resp.StatusCode)
	}
}

func TestPostRecord_created(t *testing.T) {
	_, ts := newTestServer(t)
	body := `{"syncId":"u1","originalText":"https://x.com?utm=1","cleanedText":"https://x.com","sharedAt":1000,"updatedAt":1000,"source":"MOBILE"}`
	resp, err := http.Post(ts.URL+"/records", "application/json", strings.NewReader(body))
	if err != nil {
		t.Fatal(err)
	}
	if resp.StatusCode != http.StatusCreated {
		t.Errorf("want 201, got %d", resp.StatusCode)
	}
	var got ShareRecord
	json.NewDecoder(resp.Body).Decode(&got)
	if got.SyncID != "u1" {
		t.Errorf("want syncId=u1, got %s", got.SyncID)
	}
}

func TestGetRecords(t *testing.T) {
	_, ts := newTestServer(t)
	// Insert via POST
	body := `{"syncId":"u1","originalText":"o","cleanedText":"c","sharedAt":1000,"updatedAt":1000,"source":"MOBILE"}`
	http.Post(ts.URL+"/records", "application/json", strings.NewReader(body)) //nolint:errcheck

	resp, err := http.Get(ts.URL + "/records")
	if err != nil {
		t.Fatal(err)
	}
	var records []ShareRecord
	json.NewDecoder(resp.Body).Decode(&records)
	if len(records) != 1 {
		t.Fatalf("want 1 record, got %d", len(records))
	}
}

func TestPatchRecord_lwwApplied(t *testing.T) {
	_, ts := newTestServer(t)
	http.Post(ts.URL+"/records", "application/json", strings.NewReader( //nolint:errcheck
		`{"syncId":"u1","originalText":"o","cleanedText":"c","sharedAt":1000,"updatedAt":1000,"source":"MOBILE"}`))

	req, _ := http.NewRequest(http.MethodPatch, ts.URL+"/records/u1",
		strings.NewReader(`{"notes":"hello","updatedAt":2000}`))
	req.Header.Set("Content-Type", "application/json")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	if resp.StatusCode != http.StatusOK {
		t.Errorf("want 200, got %d", resp.StatusCode)
	}
	var got ShareRecord
	json.NewDecoder(resp.Body).Decode(&got)
	if got.Notes == nil || *got.Notes != "hello" {
		t.Errorf("want notes=hello, got %v", got.Notes)
	}
}

func TestPatchRecord_lwwRejected(t *testing.T) {
	_, ts := newTestServer(t)
	http.Post(ts.URL+"/records", "application/json", strings.NewReader( //nolint:errcheck
		`{"syncId":"u1","originalText":"o","cleanedText":"c","sharedAt":1000,"updatedAt":5000,"source":"MOBILE"}`))

	req, _ := http.NewRequest(http.MethodPatch, ts.URL+"/records/u1",
		strings.NewReader(`{"notes":"stale","updatedAt":1000}`))
	req.Header.Set("Content-Type", "application/json")
	resp, _ := http.DefaultClient.Do(req)
	var got ShareRecord
	json.NewDecoder(resp.Body).Decode(&got)
	// LWW rejected: notes should still be nil
	if got.Notes != nil {
		t.Errorf("want notes unchanged (nil), got %v", got.Notes)
	}
}

func TestDeleteRecord(t *testing.T) {
	_, ts := newTestServer(t)
	http.Post(ts.URL+"/records", "application/json", strings.NewReader( //nolint:errcheck
		`{"syncId":"u1","originalText":"o","cleanedText":"c","sharedAt":1000,"updatedAt":1000,"source":"MOBILE"}`))

	req, _ := http.NewRequest(http.MethodDelete, ts.URL+"/records/u1", nil)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	if resp.StatusCode != http.StatusNoContent {
		t.Errorf("want 204, got %d", resp.StatusCode)
	}

	listResp, _ := http.Get(ts.URL + "/records")
	var records []ShareRecord
	json.NewDecoder(listResp.Body).Decode(&records)
	if len(records) != 0 {
		t.Errorf("want 0 records after delete, got %d", len(records))
	}
}

func TestPutMetadata(t *testing.T) {
	_, ts := newTestServer(t)
	http.Post(ts.URL+"/records", "application/json", strings.NewReader( //nolint:errcheck
		`{"syncId":"u1","originalText":"o","cleanedText":"c","sharedAt":1000,"updatedAt":1000,"source":"MOBILE"}`))

	title := "Test"
	req, _ := http.NewRequest(http.MethodPut, ts.URL+"/records/u1/metadata",
		strings.NewReader(`{"title":"Test","contentType":"ARTICLE","fetchStatus":"SUCCESS"}`))
	req.Header.Set("Content-Type", "application/json")
	_ = title
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	if resp.StatusCode != http.StatusOK {
		t.Errorf("want 200, got %d", resp.StatusCode)
	}
	var got ShareRecord
	json.NewDecoder(resp.Body).Decode(&got)
	if got.LinkMetadata == nil || got.LinkMetadata.ContentType != "ARTICLE" {
		t.Errorf("want linkMetadata.contentType=ARTICLE, got %+v", got.LinkMetadata)
	}
}

func TestSSE_receivesEvent(t *testing.T) {
	_, ts := newTestServer(t)

	// Connect SSE client in a goroutine
	eventCh := make(chan string, 4)
	go func() {
		resp, err := http.Get(ts.URL + "/events")
		if err != nil {
			return
		}
		defer resp.Body.Close()
		scanner := bufio.NewScanner(resp.Body)
		for scanner.Scan() {
			line := scanner.Text()
			if strings.HasPrefix(line, "event: ") {
				eventCh <- strings.TrimPrefix(line, "event: ")
			}
		}
	}()

	// Give SSE client time to connect and subscribe
	time.Sleep(100 * time.Millisecond)

	// Post a record (should trigger record_created SSE event)
	http.Post(ts.URL+"/records", "application/json", strings.NewReader( //nolint:errcheck
		`{"syncId":"u1","originalText":"o","cleanedText":"c","sharedAt":1000,"updatedAt":1000,"source":"MOBILE"}`))

	select {
	case eventType := <-eventCh:
		if eventType != "record_created" {
			t.Errorf("want record_created, got %s", eventType)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("timeout: SSE event not received")
	}
}
```

- [ ] **Step 2: Run all tests**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
go test ./... -v -timeout 30s
```

Expected: all tests PASS.

- [ ] **Step 3: Commit**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
git add server_test.go
git commit -m "test: add HTTP handler tests including SSE"
```

---

### Task 9: mdns.go + main.go

**Files:**
- Create: `~/AndroidStudioProjects/CleanShareServer/mdns.go`
- Create: `~/AndroidStudioProjects/CleanShareServer/main.go`

- [ ] **Step 1: Write mdns.go**

Write `~/AndroidStudioProjects/CleanShareServer/mdns.go`:
```go
package main

import (
	"fmt"
	"net"

	"github.com/hashicorp/mdns"
)

// RegisterMDNS advertises _cleanshare._tcp on the LAN.
// Returns a cleanup function that must be called on shutdown.
func RegisterMDNS(port int) (func(), error) {
	// Gather non-loopback IPv4 addresses for the mDNS record.
	var ips []net.IP
	ifaces, err := net.Interfaces()
	if err == nil {
		for _, iface := range ifaces {
			addrs, _ := iface.Addrs()
			for _, addr := range addrs {
				if ipnet, ok := addr.(*net.IPNet); ok {
					if ip4 := ipnet.IP.To4(); ip4 != nil && !ip4.IsLoopback() {
						ips = append(ips, ip4)
					}
				}
			}
		}
	}

	info := []string{"version=1"}
	service, err := mdns.NewMDNSService(
		"CleanShare",      // instance name
		"_cleanshare._tcp", // service type
		"",                // domain (empty = .local.)
		"",                // host (empty = hostname)
		port,
		ips,
		info,
	)
	if err != nil {
		return nil, fmt.Errorf("mdns service: %w", err)
	}

	server, err := mdns.NewServer(&mdns.Config{Zone: service})
	if err != nil {
		return nil, fmt.Errorf("mdns server: %w", err)
	}

	return func() { server.Shutdown() }, nil //nolint:errcheck
}
```

- [ ] **Step 2: Write main.go**

Write `~/AndroidStudioProjects/CleanShareServer/main.go`:
```go
package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

func main() {
	port := flag.Int("port", 8765, "HTTP listen port")
	dbPath := flag.String("db", "./cleanshare.db", "path to SQLite database file")
	flag.Parse()

	store, err := NewStore(*dbPath)
	if err != nil {
		log.Fatalf("open store: %v", err)
	}

	hub := NewHub()
	go hub.Run()

	srv := NewServer(store, hub)

	httpSrv := &http.Server{
		Addr:    fmt.Sprintf(":%d", *port),
		Handler: srv,
	}

	// mDNS registration (non-fatal: server still works without it)
	stopMDNS, err := RegisterMDNS(*port)
	if err != nil {
		log.Printf("mDNS unavailable: %v (continuing without LAN discovery)", err)
	} else {
		defer stopMDNS()
		log.Printf("mDNS: registered _cleanshare._tcp on port %d", *port)
	}

	// Graceful shutdown on SIGINT / SIGTERM
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		log.Printf("CleanShare sync server listening on :%d", *port)
		if err := httpSrv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("listen: %v", err)
		}
	}()

	<-quit
	log.Println("shutting down...")

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := httpSrv.Shutdown(ctx); err != nil {
		log.Printf("shutdown: %v", err)
	}
}
```

- [ ] **Step 3: Build the binary**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
go build -o cleanshare-server .
```

Expected: produces `./cleanshare-server` binary with no errors.

- [ ] **Step 4: Smoke-test**

In one terminal:
```bash
cd ~/AndroidStudioProjects/CleanShareServer
./cleanshare-server --port 8765 --db /tmp/test.db
```

In another:
```bash
curl -s http://localhost:8765/health        # → 200
curl -s http://localhost:8765/records       # → []
curl -s -X POST http://localhost:8765/records \
  -H 'Content-Type: application/json' \
  -d '{"syncId":"test-1","originalText":"https://x.com?utm=1","cleanedText":"https://x.com","sharedAt":1000,"updatedAt":1000,"source":"MOBILE"}'
curl -s http://localhost:8765/records       # → [{"syncId":"test-1",...}]
```

Kill the server with Ctrl-C. Expected: "shutting down..." log line.

- [ ] **Step 5: Run full test suite**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
go test ./... -timeout 30s
```

Expected: all tests PASS.

- [ ] **Step 6: Commit**

```bash
cd ~/AndroidStudioProjects/CleanShareServer
rm -f cleanshare-server /tmp/test.db  # clean up local artifacts
git add mdns.go main.go
git commit -m "feat: add mDNS registration and main entrypoint"
```
