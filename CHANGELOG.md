# Pocket NOC - Changelog

## [0.1.0] - 2024-03-11

### Added - Agent (Rust)

- ✅ **Telemetry Collection**
  - CPU metrics (global + per-core, frequency)
  - Memory metrics (RAM + Swap)
  - Disk metrics (usage per mount point)
  - Temperature sensors (hwmon)
  - Uptime and Load Average

- ✅ **Service Monitoring**
  - Check systemd service status
  - Get service PID and description
  - Support for multiple services

- ✅ **Command Execution**
  - Whitelist-based command execution
  - Prevent shell injection attacks
  - Default emergency commands (restart nginx, docker, etc)

- ✅ **API Endpoints**
  - GET /health (no auth required)
  - GET /telemetry (JWT protected)
  - GET /services/{name} (JWT protected)
  - GET /commands (JWT protected)
  - POST /commands/{id} (JWT protected)
  - GET /metrics (Prometheus compatible)

- ✅ **Authentication**
  - JWT token generation and validation
  - API Key fallback authentication
  - Bearer token extraction from headers

- ✅ **Logging**
  - Structured logging with tracing
  - Request/response logging
  - Integration with systemd-journald

- ✅ **Systemd Service File**
  - Production-ready service configuration
  - Resource limits (CPU, memory)
  - Auto-restart on failure

### Added - Controller (Kotlin/Android)

- ✅ **Project Structure**
  - MVVM architecture setup
  - Gradle configuration
  - Jetpack Compose configuration

- ✅ **Data Layer**
  - Data models for all API responses
  - Retrofit HTTP client
  - Repository pattern

- ✅ **UI Layer**
  - Dashboard screen with telemetry display
  - Status traffic light (green/yellow/red)
  - Metric cards (CPU, Memory, Disk)
  - Percentage bars
  - Emergency action buttons

- ✅ **ViewModel**
  - DashboardViewModel with StateFlow
  - Loading/Success/Error states
  - Coroutine integration

- ✅ **Theming**
  - Dark theme by default
  - Material 3 design system
  - Custom colors (green/orange/red for alerts)

### Added - Documentation

- ✅ Main README.md with architecture overview
- ✅ SETUP.md - Installation and configuration guide
- ✅ SECURITY.md - Authentication, TLS, attack prevention
- ✅ API.md - Complete API specification with examples
- ✅ ARCHITECTURE.md - Technical architecture details

### Added - Configuration

- ✅ .gitignore for both Rust and Android projects
- ✅ Cargo.toml with production dependencies
- ✅ build.gradle.kts with Android/Kotlin setup

---

## Upcoming Features

### Phase 2 - Enhanced Features
- [ ] Dashboard improvements (graphs, history)
- [ ] Server list and management
- [ ] Multiple server support
- [ ] Settings screen
- [ ] Hilt dependency injection (Android)
- [ ] WebSocket real-time updates
- [ ] Offline mode with local caching

### Phase 3 - Advanced Features
- [ ] Android widget (homescreen traffic light)
- [ ] Push notifications for alerts
- [ ] Prometheus/Grafana integration
- [ ] Alert rules engine
- [ ] Audit logs
- [ ] Multi-user support

### Phase 4 - Enterprise
- [ ] Central hub aggregator
- [ ] Database for metrics history
- [ ] Web dashboard
- [ ] LDAP/AD integration
- [ ] Role-based access control (RBAC)
- [ ] API rate limiting
- [ ] SLA monitoring

---

## Known Limitations

1. **Temperature sensors**: Only Linux hwmon is supported
2. **Commands**: Limited to pre-approved whitelist
3. **Scaling**: Single agent per server (no clustering)
4. **Real-time**: Polling only (WebSocket planned)
5. **Storage**: No metrics history (in-memory only)
6. **Alert**: No notifications yet (push planned)

---

## Development Notes

### Running Tests

**Agent (Rust)**:
```bash
cd agent
cargo test
```

**Controller (Kotlin)**:
```bash
cd controller
./gradlew test
```

### Building for Release

**Agent**:
```bash
cd agent
cargo build --release
strip target/release/pocket-noc-agent
ls -lh target/release/pocket-noc-agent
```

**Controller**:
```bash
cd controller
./gradlew assembleRelease
```

### Debugging

**Agent Logs**:
```bash
sudo journalctl -u pocket-noc-agent -f
```

**Android Logcat**:
```bash
adb logcat | grep pocket-noc
```

---

## Contributing

Please read [CONTRIBUTING.md](./CONTRIBUTING.md) before submitting PRs.

---

## License

MIT License - See LICENSE.md

---

**Last Updated**: 2024-03-11  
**Maintained by**: Pocket NOC Team
