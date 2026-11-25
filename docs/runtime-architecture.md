# AETHER Runtime — architecture proposal

Status: design only. No native runtime binaries have been built or tested.

## Scope

An offline Luau scripting environment for AETHER's own interface and simulator on Android and Windows. It does not attach to Roblox processes, alter third-party clients, or depend on proprietary executors. No anti-cheat avoidance or claims of ban immunity.

## Common core

- C++ core using upstream Luau.Compiler and Luau.VM from a reviewed, pinned release. Record the exact upstream revision at integration time.
- Compile source locally; never accept externally supplied bytecode.
- Initialize standard libraries and sandbox the global state and each script thread using the upstream APIs. Give each execution an independent VM state.
- Enforce source size, allocation, output, execution time and concurrency limits; run untrusted jobs outside the UI process. Terminate the worker on hard deadline, cancellation or resource failure.
- Expose only narrowly scoped APIs for the AETHER simulator and its local visual previews. No filesystem, network, native FFI, process manipulation or unrestricted callbacks in the script environment.
- Use structured results: job ID, status, diagnostics, output and duration. Bound message sizes and validate every request.

## Platform integration

Android: NDK/CMake native build, isolated worker service, validated IPC, UI bridge limited to starting and cancelling local jobs and reading results. Test arm64-v8a and x86_64 emulator.

Windows: MSVC/CMake x64 worker executable, restricted process and job object with memory and lifetime limits, validated IPC. Keep native execution off the UI thread.

## Acceptance tests

Valid scripts, syntax errors, runtime errors, infinite loops, excessive allocations, oversized output, forbidden APIs, cancellation, repeated execution, worker crashes and restart, simulator-only capability enforcement. Build and run tests independently on each platform. No runtime safety guarantee until these tests are performed.

## References

- https://github.com/luau-lang/luau
- https://luau.org/sandbox/
- https://luau.org/api/
