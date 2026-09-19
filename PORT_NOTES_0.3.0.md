# 0.3.0 Port Notes

## Restored from original BOTB architecture

- `networking/ModPayloads`
- `networking/ModPackets` (initial registration/join slice)
- `networking/StateBroadcaster` (initial core-state slice)
- `networking/SyncDayNightS2CPayload`
- `states/ClientState` (day/night portion)

## Temporary diagnostic bridge

The following payloads are new and intentionally temporary:

- `SyncScriptNameS2CPayload`
- `NetworkSyncProbeS2CPayload`
- `NetworkSyncAckC2SPayload`

`SyncScriptNameS2CPayload` will be replaced when the original full script payload/codec is ported. The probe/ack pair exists solely to make network correctness visible during the controlled migration.
