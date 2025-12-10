# RISC-V CPU Simulator

## Prerequisite

- make/cmake
- verilator
- riscv toolchains

## Quick Start

### Build The Simulator

```bash
make    # Ninja by default, and you can always change the generator
```

or manually follow this by default:

```bash
mkdir -p build
cd build
cmake ..
make
```

You will get an excutable named rv32_simulator eventually. **rv32_simulator -h** to see the commands

### Write Your own tests

And all the tests are located at **tests**, you can write your own tests in assembly under **tests/asm** and use

```bash
make all
```

to generate hex, dump, elf, bin, etc.
