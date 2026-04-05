use pocket_noc_agent::watchdog::event::{WatchdogEvent, WatchdogEventStore};

fn make_test_event(service: &str, status: &str, server_id: &str) -> WatchdogEvent {
    WatchdogEvent {
        id: uuid::Uuid::new_v4().to_string(),
        timestamp: chrono::Utc::now().timestamp(),
        timestamp_iso: chrono::Utc::now().to_rfc3339(),
        server_id: server_id.to_string(),
        server_role: "generic".to_string(),
        server_hostname: "test-host".to_string(),
        service: service.to_string(),
        probe_result: "Down".to_string(),
        probe_latency_ms: Some(500),
        action_taken: format!("RestartService({})", service),
        final_status: status.to_string(),
        attempts: 1,
        circuit_open: status == "CircuitOpen",
        message: format!("Test event for {}", service),
    }
}

#[test]
fn test_event_store_push_and_recent() {
    let mut store = WatchdogEventStore::new(100);
    store.push(make_test_event("nginx", "Success", "server-1"));
    store.push(make_test_event("mysql", "Failed", "server-1"));

    let recent = store.recent(10);
    assert_eq!(recent.len(), 2, "Should have 2 events");
}

#[test]
fn test_event_store_ring_buffer_overflow() {
    let mut store = WatchdogEventStore::new(5);
    for i in 0..10 {
        store.push(make_test_event(&format!("svc-{}", i), "Success", "server-1"));
    }

    assert_eq!(store.len(), 5, "Ring buffer should cap at 5 events");
    // Oldest events should be evicted
    let recent = store.recent(5);
    assert_eq!(recent.len(), 5);
}

#[test]
fn test_event_store_filter_by_server() {
    let mut store = WatchdogEventStore::new(100);
    store.push(make_test_event("nginx", "Success", "server-1"));
    store.push(make_test_event("mysql", "Failed", "server-2"));
    store.push(make_test_event("redis", "Success", "server-1"));

    let filtered = store.by_server("server-1");
    assert_eq!(filtered.len(), 2, "Should have 2 events for server-1");
}

#[test]
fn test_event_store_filter_by_status() {
    let mut store = WatchdogEventStore::new(100);
    store.push(make_test_event("nginx", "Success", "server-1"));
    store.push(make_test_event("mysql", "Failed", "server-1"));
    store.push(make_test_event("redis", "CircuitOpen", "server-1"));

    let failed = store.by_status("Failed");
    assert_eq!(failed.len(), 1, "Should have 1 Failed event");
}

#[test]
fn test_event_store_clear() {
    let mut store = WatchdogEventStore::new(100);
    store.push(make_test_event("nginx", "Success", "server-1"));
    store.push(make_test_event("mysql", "Failed", "server-1"));

    store.clear();
    assert_eq!(store.len(), 0, "Store should be empty after clear");
}

#[test]
fn test_event_store_count_by_server() {
    let mut store = WatchdogEventStore::new(100);
    store.push(make_test_event("nginx", "Success", "server-1"));
    store.push(make_test_event("mysql", "Failed", "server-2"));
    store.push(make_test_event("redis", "Success", "server-1"));
    store.push(make_test_event("php", "Failed", "server-2"));

    let counts = store.count_by_server();
    assert_eq!(counts.get("server-1"), Some(&2));
    assert_eq!(counts.get("server-2"), Some(&2));
}
