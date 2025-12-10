# rvcpu

#### A toy RISCV CPU impled with chisel scala with a riscv cpu simulator.

###### NOTE: THIS REPO IS STILL UNDER DEVELOPMENT AND ONLY WORKS UNDER UNIX-LIKE OS.

## Prerequisite

#### Code Generation

- sbt version in this project: 1.10.11 (see **project/build.properties**)
- see chisel/scala version in **build.sbt**

#### Autotest:

##### For regular autotest impled in verilog:

- gtkwave / surfer (waveform visualization)
- verilator (generating executable files for testbench)
- fzf (optional)

##### For cocotb autotest impled in python:

- icarus & cocotb (only tested under cocotb 1.9.2)
- fzf (optional)

#### Synthesis and STA:

- Use Vivado (precise)
- Pls use the **updated** yosys or there might be problems.(rough)

## How to use

To generate systemVerilog

> ```bash
> make run
> ```

## Run Autotest

Run test using **verilator** and **gtkwave / surfer** through **tb.sh**

> ```bash
> make tb # (FZF=true)
> ```

Make sure that the tb file located in **sims/tb** <br>

or using

> ```bash
> make cocotb # (FZF=true)
> ```

for cocotb through **cocotb.sh**

Similarly, make sure that the py scripts are located in **sims/cocotb** and make sure you have cocotb env, activating a venv with uv is recommended.

###### All the related scripts can be found in scripts/

## Run STA

Run sta using **Yosys** or **Vivaod** through **./sta-yosys.sh** with Xilinx toolchain.

> ```bash
> make sta # (FZF=true STA_TOOL=yosys(vivado))
> ```

# RISC-V CPU Simulator

## Prerequisite

- make/cmake
- verilator
- riscv toolchains

## Quick Start

### Build The Simulator

```bash
cd sims
make    # Ninja by default, and you can always change the generator
```

You will get an excutable named rv32_simulator eventually. **rv32_simulator -h** to see the commands

### Write Your own tests

And all the tests are located at **sims/tests**, you can write your own tests in assembly under **sims/tests/asm** and use

```bash
make all
```

to generate hex, dump, elf, bin, etc.
