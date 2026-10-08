# Dreamwalker diagnostics session

Session UUID: 8ea47d47-9ede-4b27-a1e4-1e2cfaafd269
Capture: STOPPED / NO ACTIVE RECORDING; reason: OPERATOR_STOP
Started: 2026-10-08T12:27:37.099557700Z; requested duration: 30 seconds
Observed duration: 10.443 seconds

Events: 3 observed, 3 retained, 0 dropped.
Distinct error keys: 0; repeated occurrences are counted in errors.json.
Object snapshots: 0 retained; full NBT is never captured.

Server ticks: 209 observed; ticks over 50 ms: 1.
Tick mean / p95 / p99: 8.481 ms / 26.054 ms / 36.926 ms.
Mean/count cover observed ticks; p95/p99 use only the retained window. Tick intervals include Fabric END callbacks. Own hook timings can overlap; they are not unique process CPU cost.

Client telemetry: BOUNDED_CLIENT_BATCHES_PRESENT. Graphics settings and frame intervals are in client-batches.json and the separate local client ZIP with the same UUID.
GPU timing: NOT_MEASURED. CPU render submission and FPS do not determine a block's GPU time or exact load percentage.
All-mod / vanilla chunk / DataTracker / TCP traffic: NOT_MEASURED. Network counters include only instrumented custom-channel payload bytes.
JFR reports actual observed GC pauses when available; MXBean collection time is not an STW pause measurement. Client and integrated server may share one PID: do not add their heap/GC values.

Production version: 0.1.0-prototype.5; SHA256: 521337222c6f75b31a094c4944963c4103c514985d5696d55187f31dc4b17ca5.
Minecraft/Java/Fabric/mod versions are in environment.json; detailed measurements, retention and unavailable data are in measurements.json. Normal placement refusals are events, not errors. This capture is not manual visual acceptance.
