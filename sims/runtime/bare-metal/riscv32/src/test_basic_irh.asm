.section .text
.globl _start

# --- Exact CLINT Register Addresses ---
.equ CLINT_MSIP,        0x20000000
.equ CLINT_MTIMECMP_LO, 0x20004000
.equ CLINT_MTIMECMP_HI, 0x20004004
.equ CLINT_MTIME_LO,    0x2000BFF8
.equ CLINT_MTIME_HI,    0x2000BFFC

_start:
    # -------------------------------------------------------------------------
    # 1. Setup Trap Vector (mtvec)
    # -------------------------------------------------------------------------
    la x5, trap_handler       # Load address of trap_handler into x5 (t0)
    csrw mtvec, x5            # Write it to Machine Trap Vector CSR
    
    # Initialize flags used to verify interrupts fired
    li x3, 0                  # x3 = Soft IRQ flag
    li x4, 0                  # x4 = Timer IRQ flag

    # -------------------------------------------------------------------------
    # 2. Test Software Interrupt (MSIP)
    # -------------------------------------------------------------------------
    # Enable Machine Software Interrupts (MSIE) -> bit 3 in mie CSR
    li x5, 0x8                # 0x8 = 0b1000 (bit 3)
    csrw mie, x5

    # Enable Global Interrupts (MIE) -> bit 3 in mstatus CSR
    li x5, 0x8
    csrw mstatus, x5

    # Trigger Software Interrupt: Write 1 to CLINT msip register
    li x6, CLINT_MSIP
    li x7, 1
    sw x7, 0(x6)              # Write 1 to 0x20000000

wait_for_soft_irq:
    # CPU should instantly trap to trap_handler.
    # The handler will set x3 = 1 and clear the interrupt.
    li x7, 1
    bne x3, x7, wait_for_soft_irq

    # -------------------------------------------------------------------------
    # 3. Test Timer Interrupt (MTIP)
    # -------------------------------------------------------------------------
    # Enable Machine Timer Interrupts (MTIE) -> bit 7 in mie CSR
    li x5, 0x80               # 0x80 = 0b10000000 (bit 7)
    csrw mie, x5              # Overwrites MSIE, leaving only MTIE enabled

    # Read current mtime (lower 32 bits)
    li x6, CLINT_MTIME_LO
    lw x7, 0(x6)              # Read 0x2000BFF8

    # Add 100 clock ticks to current time to schedule the timer interrupt
    addi x7, x7, 100
    
    # Write to mtimecmp
    li x6, CLINT_MTIMECMP_LO
    sw x7, 0(x6)              # Write lower 32 bits
    
    li x6, CLINT_MTIMECMP_HI
    sw zero, 0(x6)            # Write 0 to upper 32 bits

    # Re-enable Global Interrupts (mret cleared MIE, so we must restore it)
    li x5, 0x8
    csrw mstatus, x5

wait_for_timer_irq:
    # CPU will loop here until mtime >= mtimecmp.
    # Then it will trap, and the handler will set x4 = 1.
    li x7, 1
    bne x4, x7, wait_for_timer_irq

    # -------------------------------------------------------------------------
    # 4. Finish Test
    # -------------------------------------------------------------------------
    # If we reach here, both interrupts fired perfectly!
    j .


# -----------------------------------------------------------------------------
# Trap Handler
# -----------------------------------------------------------------------------
.balign 4                     # mtvec MUST be 4-byte aligned for RISC-V DIRECT mode
trap_handler:
    # Read mcause to figure out why we trapped
    csrr x28, mcause          # Read mcause into x28 (t3)
    
    # Check if Software Interrupt (mcause == 0x80000003)
    li x29, 0x80000003
    beq x28, x29, handle_soft_irq

    # Check if Timer Interrupt (mcause == 0x80000007)
    li x29, 0x80000007
    beq x28, x29, handle_timer_irq

    # If it's something else (Exception), just halt
    j .

handle_soft_irq:
    # 1. Acknowledge: Clear MSIP by writing 0 to the CLINT
    li x30, CLINT_MSIP
    sw zero, 0(x30)
    
    # 2. Set success flag for the main thread
    li x3, 1                  # Set x3 = 1
    j trap_end

handle_timer_irq:
    li x31, -1                
    li x30, CLINT_MTIMECMP_LO
    sw x31, 0(x30)
    li x30, CLINT_MTIMECMP_HI
    sw x31, 0(x30)
    
    lw x0, 0(x30) 
    
    li x4, 1                  
    j trap_end

trap_end:
    # Return from Trap. 
    # This automatically restores PC from mepc, and copies MPIE back to MIE.
    mret
