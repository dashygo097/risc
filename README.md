# risc

###### NOTE: THIS REPO IS STILL UNDER DEVELOPMENT.

## Prerequisite

#### Code Generation

- sbt version in this project: 1.10.11 (see **project/build.properties**)
- see chisel/scala version in **build.sbt**
- **Make sure you installed [Vopts](https://github.com/dashygo097/Vopts.git)**

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

Run sta using **Yosys** or **Vivaod** through **sta-yosys.sh(sta-vivado)** with Xilinx toolchain.

> ```bash
> make sta # (FZF=true STA_TOOL=yosys(vivado))
> ```
