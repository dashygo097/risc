# GShare Function Verification

This report checks the gshare-oriented test programs for functional correctness.
The focus is not prediction accuracy, but whether each program computes the
expected architectural result from its own assembly logic.

Method:

- For each test ELF, run until the `ebreak` retirement point.
- Re-run to just past that point and dump registers.
- Compute expected final values from the assembly itself.
- Compare expected vs. actual register state.

Important caveats:

- `ebreak` is not handled as a terminating trap in the current core/simulator
  path, so the simulator does not stop automatically at `ebreak`.
- The checks below therefore sample register state at the `ebreak` boundary.
- `test_gshare_c_O2.elf` and `test_gshare_opt.elf` as currently shipped are
  linked at `0x00000000` and cannot be loaded by the simulator. Their source was
  still checked by rebuilding temporary fixed ELFs at `0x80000000`.

## Summary

| Test | Expected | Actual | Result | Notes |
| --- | --- | --- | --- | --- |
| `test_gshare` | `x10=50, x11=50, x12=1000, x20=0x12345678` | `x10=50, x11=50, x12=1000, x20=0x12345678` | PASS | Main functional reference case |
| `test_gshare_alternating` | `x10=257, x11=256, x12=386, x13=1, x20=0x12345678` | `x10=257, x11=256, x12=386, x13=1, x20=0x12345678` | PASS | Final odd iteration falls through into `even_path`, so `x10` and `x12` each get one extra increment |
| `test_gshare_correlated_dual` | `x10=256, x11=256, x12=1280, x13=3, x14=2, x15=1, x20=0x12345678` | `x10=256, x11=256, x12=1280, x13=3, x14=2, x15=1, x20=0x12345678` | PASS | `20` accumulator points per 4 iterations |
| `test_gshare_history10` | `x10=321, x11=320, x12=561, x13=7, x14=5, x20=0x12345678` | `x10=321, x11=320, x12=561, x13=7, x14=5, x20=0x12345678` | PASS | Final taken iteration falls through into `not_taken_path`, causing one extra `x10` and `x12` increment |
| `test_gshare_loop_exit` | `x10=0, x12=300, x20=0x12345678` | `x10=0, x12=300, x20=0x12345678` | PASS | Simple countdown loop |
| `test_gshare_good_pattern` | `x10=2000, x11=2000, x12=6500, x13=3, x14=3, x20=0x12345678` | `x10=2000, x11=2000, x12=6500, x13=3, x14=3, x20=0x12345678` | PASS | Pattern `TTTN`, 500 groups, `13` points per group |
| `test_gshare_interleaved_independent` | `x10=240, x11=240, x12=2408, x20=0x12345678` | `x10=240, x11=240, x12=2760, x15=0, x17=0, x20=0x12345678` | FAIL | Uses `rem`; current core does not implement `DIV/REM` correctly for this test |
| `test_gshare_alias_thrash` | `x10=192, x11=192, x12=2016, x13=3, x14=3, x15=3, x16=1, x20=0x12345678` | `x10=192, x11=192, x12=2016, x13=3, x14=3, x15=3, x16=1, x20=0x12345678` | PASS | Alias stress affects performance, not final result |
| `test_gshare_long_period` | `x10=320, x11=320, x12=800, x13=15, x14=14, x20=0x12345678` | `x10=320, x11=320, x12=800, x13=15, x14=14, x20=0x12345678` | PASS | 4 taken cases per 16 iterations |
| `test_gshare_bad_random` | `x5=991, x6=1009, x10=2000, x11=2000, x12=0x4b06, x20=0x12345678` | `x5=991, x6=1009, x10=2000, x11=2000, x12=0x4b06, x20=0x12345678` | PASS | Expected values computed by exact LFSR emulation from assembly |
| `test_gshare_lfsr_branchmix` | `x10=220, x11=220, x12=0x7aa9, x13=1101, x14=0, x15=0xb400, x16=1, x17=8, x20=0x12345678` | `x10=220, x11=220, x12=0x7aa9, x13=1101, x14=0, x15=0xb400, x16=1, x17=8, x20=0x12345678` | PASS | Expected values computed by exact LFSR emulation from assembly |
| `test_gshare_c` | Source-level expected event count `300`, marker `x20=0x12345678` | `x20=0x12345678` | PARTIAL | Current shipped ELF spills locals to memory, so register dump does not expose the event counter directly |
| `test_gshare_c_O2.elf` | N/A for shipped ELF | Load failure | FAIL | Current artifact linked at `0x00000000`; simulator reports no mapped device there |
| `test_gshare_opt.elf` | N/A for shipped ELF | Load failure | FAIL | Current artifact linked at `0x00000000`; simulator reports no mapped device there |
| `test_gshare_c_O2` rebuilt temporarily | `x20=0x12345678` | `x20=0x12345678` | PASS | Optimizer removes the loop; marker path still functions |
| `test_gshare_opt` rebuilt temporarily | Source-level expected event count `300`, marker `x20=0x12345678` | `x15=300, x20=0x12345678` | PASS | Rebuilt fixed ELF linked correctly; optimized loop result visible in `x15` |

## Key Functional Findings

1. Most pure branch-pattern assembly tests are functionally correct.
2. `test_gshare_interleaved_independent` is functionally incorrect on the
   current core because it depends on `rem`, and `DIV/REM` is not implemented
   in the current RV32IM decode/execute path.
3. The currently shipped `test_gshare_c_O2.elf` and `test_gshare_opt.elf` are
   invalid test artifacts for this simulator because they are linked at
   `0x00000000` instead of the mapped IMEM base `0x80000000`.
4. `ebreak` still does not terminate simulation correctly after retirement.

## Evidence Pointers

- Main trace used earlier: `sims/tests/test_gshare_trace.log`
- This report: `sims/tests/gshare_function_verification.md`
- The `rem` dependency in the failing test is visible in:
  `sims/tests/asm/test_gshare_interleaved_independent.asm`
- Missing divide/remainder support is visible in:
  `arch/core/decoder/variants/RV32IM.scala`
